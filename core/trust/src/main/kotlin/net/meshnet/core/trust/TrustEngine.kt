package net.meshnet.core.trust

import net.meshnet.core.crypto.KeyManager
import net.meshnet.core.protocol.Report
import net.meshnet.core.protocol.ReportCorroboration
import net.meshnet.core.storage.report.ReportDao
import net.meshnet.core.storage.trust.CorroborationEntity
import net.meshnet.core.storage.trust.ReporterHistoryEntity
import net.meshnet.core.storage.trust.TrustDao
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Orchestrates corroboration recording and confidence score updates.
 *
 * Responsibilities:
 *   1. Validate incoming corroborations (signature + constraints).
 *   2. Persist valid corroborations via [TrustDao].
 *   3. Recompute and persist confidence scores via [ReportDao].
 *   4. Maintain reporter history for originator weighting.
 *
 * Confidence is recomputed on every new corroboration rather than lazily,
 * so the map display always shows current data.
 */
@Singleton
class TrustEngine @Inject constructor(
    private val trustDao: TrustDao,
    private val reportDao: ReportDao,
    private val keyManager: KeyManager,
) {
    /**
     * Processes an incoming [ReportCorroboration].
     *
     * Validation:
     *   - Witness must differ from report originator (no self-corroboration).
     *   - Ed25519 signature must verify against witness public key.
     *   - Witness location must be within [MAX_WITNESS_RADIUS_KM] of report location.
     *   - [observedAt] must fall within the report's active window.
     *
     * Returns true if accepted, false if rejected.
     */
    suspend fun processCorroboration(
        corroboration: ReportCorroboration,
        reportOriginatorId: ByteArray,
        reportLatitude: Double,
        reportLongitude: Double,
        reportCreatedAtMs: Long,
        reportExpiresAtMs: Long,
    ): Boolean {
        val witnessIdHex = corroboration.witnessId.toByteArray().toHex()
        val originatorHex = reportOriginatorId.toHex()

        // Constraint 1: no self-corroboration
        if (witnessIdHex == originatorHex) {
            Timber.w("Rejected corroboration: witness == originator")
            return false
        }

        // Constraint 2: observed time within report window
        val observedAt = corroboration.observedAt
        if (observedAt < reportCreatedAtMs || observedAt > reportExpiresAtMs) {
            Timber.w("Rejected corroboration: observed_at outside report window")
            return false
        }

        // Constraint 3: witness proximity
        val distKm = haversineKm(
            lat1 = corroboration.latitude, lon1 = corroboration.longitude,
            lat2 = reportLatitude, lon2 = reportLongitude,
        )
        if (distKm > MAX_WITNESS_RADIUS_KM) {
            Timber.w("Rejected corroboration: witness ${distKm}km away, exceeds ${MAX_WITNESS_RADIUS_KM}km radius")
            return false
        }

        // Constraint 4: Ed25519 signature (witness signed fields 1-5 of ReportCorroboration)
        val witnessPublicKey = corroboration.witnessId.toByteArray()
        val dataToVerify = corroboration.toBuilder().clearSignature().build().toByteArray()
        val verified = keyManager.verify(
            data = dataToVerify,
            signature = corroboration.signature.toByteArray(),
            publicKey = witnessPublicKey,
        )
        if (!verified) {
            Timber.w("Rejected corroboration: invalid Ed25519 signature")
            return false
        }

        // Persist
        val entity = CorroborationEntity(
            reportId = corroboration.reportId.toByteArray().toHex(),
            witnessId = witnessIdHex,
            witnessLatitude = corroboration.latitude,
            witnessLongitude = corroboration.longitude,
            observedAtMs = observedAt,
            signature = corroboration.signature.toByteArray(),
        )
        val inserted = trustDao.insertCorroboration(entity)
        if (inserted == -1L) {
            Timber.d("Duplicate corroboration ignored")
            return false
        }

        // Recompute confidence
        recomputeConfidence(
            reportId = corroboration.reportId.toByteArray().toHex(),
            reportLat = reportLatitude,
            reportLon = reportLongitude,
            reportCreatedAtMs = reportCreatedAtMs,
            originatorId = originatorHex,
        )
        return true
    }

    /**
     * Marks a report as stale and updates reporter history.
     * Called when a user flags a report as outdated.
     */
    suspend fun flagReportStale(reportId: String, originatorId: String) {
        reportDao.flagStale(reportId)
        ensureReporterHistory(originatorId)
        trustDao.incrementFlaggedStale(originatorId, System.currentTimeMillis())
    }

    /**
     * Records a new report creation for reporter history tracking.
     */
    suspend fun recordReportCreated(originatorId: String) {
        ensureReporterHistory(originatorId)
        trustDao.incrementCreated(originatorId, System.currentTimeMillis())
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private suspend fun recomputeConfidence(
        reportId: String,
        reportLat: Double,
        reportLon: Double,
        reportCreatedAtMs: Long,
        originatorId: String,
    ) {
        val corroborations = trustDao.corroborationsForReport(reportId)
        val uniqueWitnesses = corroborations.distinctBy { it.witnessId }.size

        val avgDistKm = if (corroborations.isEmpty()) {
            0.0
        } else {
            corroborations.map { c ->
                haversineKm(c.witnessLatitude, c.witnessLongitude, reportLat, reportLon)
            }.average()
        }

        val history = trustDao.reporterHistory(originatorId)
        val score = ConfidenceCalculator.calculate(
            uniqueWitnesses = uniqueWitnesses,
            avgWitnessDistKm = avgDistKm,
            createdAtMs = reportCreatedAtMs,
            nowMs = System.currentTimeMillis(),
            reportsCorroborated = history?.reportsCorroborated ?: 0,
            reportsFlagged = history?.reportsFlaggedStale ?: 0,
            totalReports = history?.reportsCreated ?: 0,
        )

        reportDao.updateConfidence(reportId, score.value, uniqueWitnesses)

        // Promote to corroborated in reporter history
        if (uniqueWitnesses >= CORROBORATION_THRESHOLD) {
            trustDao.incrementCorroborated(originatorId, System.currentTimeMillis())
        }

        Timber.d("Report $reportId confidence updated: ${score.value} (witnesses=$uniqueWitnesses)")
    }

    private suspend fun ensureReporterHistory(originatorId: String) {
        if (trustDao.reporterHistory(originatorId) == null) {
            trustDao.insertReporterHistory(ReporterHistoryEntity(originatorId = originatorId))
        }
    }

    /**
     * Haversine formula — returns distance in kilometres between two lat/lon pairs.
     */
    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2).let { it * it } +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
            Math.sin(dLon / 2).let { it * it }
        return r * 2 * Math.asin(sqrt(a))
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    companion object {
        /** Maximum distance from report location for a corroboration to be accepted. */
        const val MAX_WITNESS_RADIUS_KM = 2.0
        /** Minimum unique witnesses to mark a report as corroborated in reporter history. */
        const val CORROBORATION_THRESHOLD = 3
    }
}
