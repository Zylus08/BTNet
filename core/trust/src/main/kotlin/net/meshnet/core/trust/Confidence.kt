package net.meshnet.core.trust

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Computes a confidence score for a community report.
 *
 * Score = f(independent_witnesses, distance_km, time_decay, reporter_history)
 *
 * Formula:
 *   raw = witnessComponent × distanceComponent × timeDecayComponent × historyComponent
 *   confidence = clamp(raw, 0.0, 1.0)
 *
 * Component details:
 *
 *   witnessComponent:
 *     Uses square-root saturation to prevent runaway scores from many witnesses.
 *     score = min(1.0, sqrt(uniqueWitnesses / WITNESSES_FOR_MAX_SCORE))
 *
 *   distanceComponent:
 *     Witnesses closer to the event are more credible.
 *     score = exp(-lambda × distanceKm)  where lambda controls decay rate.
 *     At 0 km: 1.0. At 1 km: ~0.88. At 5 km: ~0.54.
 *
 *   timeDecayComponent:
 *     Reports lose relevance over time. Exponential decay from creation.
 *     score = exp(-lambda × elapsedHours)
 *     At 0 h: 1.0. At 6 h: ~0.57. At 24 h: ~0.05.
 *
 *   historyComponent:
 *     Weights the originator's past accuracy (local, private).
 *     score = 0.5 + 0.5 × (corroboratedRatio − flaggedRatio)  clamped to [0.2, 1.0]
 *     Unknown originators start at 0.5 (neutral).
 */
object ConfidenceCalculator {

    /** Witnesses needed to reach the maximum witness component (1.0). */
    const val WITNESSES_FOR_MAX_SCORE = 9  // sqrt(9/9) = 1.0

    /** Distance decay rate (km⁻¹). */
    private const val DISTANCE_LAMBDA = 0.12

    /** Time decay rate (h⁻¹). Controls how quickly relevance fades. */
    private const val TIME_LAMBDA = 0.12

    /** Minimum history component for originators with no record. */
    private const val HISTORY_NEUTRAL = 0.5f

    /** Minimum allowed history component. Prevents zero-score from bad history alone. */
    private const val HISTORY_MIN = 0.2f

    /**
     * Calculates a [ConfidenceScore] for a report.
     *
     * @param uniqueWitnesses    number of distinct corroborating nodes
     * @param avgWitnessDistKm   average distance of witnesses from reported location
     * @param createdAtMs        report creation timestamp (unix millis)
     * @param nowMs              current time (unix millis)
     * @param reportsCorroborated originator's historically corroborated report count
     * @param reportsFlagged     originator's historically stale-flagged report count
     * @param totalReports       originator's total report count (0 = unknown)
     */
    fun calculate(
        uniqueWitnesses: Int,
        avgWitnessDistKm: Double,
        createdAtMs: Long,
        nowMs: Long,
        reportsCorroborated: Int = 0,
        reportsFlagged: Int = 0,
        totalReports: Int = 0,
    ): ConfidenceScore {
        val witnessComp = witnessComponent(uniqueWitnesses)
        val distComp = distanceComponent(avgWitnessDistKm)
        val timeComp = timeDecayComponent(createdAtMs, nowMs)
        val histComp = historyComponent(reportsCorroborated, reportsFlagged, totalReports)

        val raw = witnessComp * distComp * timeComp * histComp
        val clamped = raw.coerceIn(0.0, 1.0)

        return ConfidenceScore(
            value = clamped.toFloat(),
            witnessComponent = witnessComp.toFloat(),
            distanceComponent = distComp.toFloat(),
            timeDecayComponent = timeComp.toFloat(),
            historyComponent = histComp.toFloat(),
            uniqueWitnesses = uniqueWitnesses,
        )
    }

    // ── Components ────────────────────────────────────────────────────────────

    internal fun witnessComponent(uniqueWitnesses: Int): Double =
        sqrt(uniqueWitnesses.toDouble() / WITNESSES_FOR_MAX_SCORE).coerceIn(0.0, 1.0)

    internal fun distanceComponent(distKm: Double): Double =
        exp(-DISTANCE_LAMBDA * max(0.0, distKm))

    internal fun timeDecayComponent(createdAtMs: Long, nowMs: Long): Double {
        val elapsedHours = (nowMs - createdAtMs).toDouble() / 3_600_000.0
        return exp(-TIME_LAMBDA * max(0.0, elapsedHours))
    }

    internal fun historyComponent(
        corroborated: Int,
        flagged: Int,
        total: Int,
    ): Double {
        if (total == 0) return HISTORY_NEUTRAL.toDouble()
        val ratio = (corroborated.toDouble() - flagged.toDouble()) / total.toDouble()
        return (HISTORY_NEUTRAL + 0.5f * ratio).coerceIn(HISTORY_MIN.toDouble(), 1.0)
    }
}

/**
 * Result of a confidence calculation, broken down by contributing factor.
 * All component values in [0.0, 1.0].
 */
data class ConfidenceScore(
    val value: Float,                  // final clamped score
    val witnessComponent: Float,
    val distanceComponent: Float,
    val timeDecayComponent: Float,
    val historyComponent: Float,
    val uniqueWitnesses: Int,
) {
    /** Display level for UI badges. */
    val level: ConfidenceLevel
        get() = when {
            value >= 0.7f -> ConfidenceLevel.HIGH
            value >= 0.35f -> ConfidenceLevel.MEDIUM
            else -> ConfidenceLevel.LOW
        }
}

enum class ConfidenceLevel { LOW, MEDIUM, HIGH }
