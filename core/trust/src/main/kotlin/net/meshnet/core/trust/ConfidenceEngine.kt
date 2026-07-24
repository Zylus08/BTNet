package net.meshnet.core.trust

import net.meshnet.core.storage.entity.ReportEntity
import net.meshnet.core.storage.entity.TrustEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

/**
 * Calculates a dynamic confidence score (0.0 to 1.0) for community reports.
 * Does not treat any single report as ground truth.
 */
@Singleton
class ConfidenceEngine @Inject constructor() {

    /**
     * Computes the confidence of a specific report.
     * 
     * @param report The target report to evaluate
     * @param authorTrust The historical trust score of the author (0.0 - 1.0)
     * @param corroboratingReports Other reports in the same Geohash with the same Category
     * @return Confidence score from 0.0 (untrusted) to 1.0 (highly verified)
     */
    fun computeReportConfidence(
        report: ReportEntity,
        authorTrust: TrustEntity?,
        corroboratingReports: List<ReportEntity>
    ): Float {
        var score = 0.2f // Baseline assumption of good faith

        // 1. Author History (Max +0.3)
        val authorScore = authorTrust?.score ?: 0.5f // Default to 0.5 if unknown
        score += (authorScore - 0.5f) * 0.6f

        // 2. Corroboration (Max +0.4)
        // Each independent witness adds diminishing returns
        val corroborationBonus = min(0.4f, (corroboratingReports.size * 0.15f))
        score += corroborationBonus

        // 3. Freshness / Decay (Max -0.5)
        // Reports lose confidence over time. E.g. A fire 24h ago is likely resolved or moved.
        val ageMs = System.currentTimeMillis() - report.timestamp
        val ageHours = ageMs / (1000 * 60 * 60f)
        
        // Decay depends on category. Water stays longer than a blocked road.
        val decayRate = getDecayRateForCategory(report.category)
        val timePenalty = min(0.5f, ageHours * decayRate)
        score -= timePenalty

        // Ensure bounds
        return max(0.0f, min(1.0f, score))
    }

    private fun getDecayRateForCategory(category: String): Float {
        return when (category) {
            "FIRE", "ROAD_BLOCKED" -> 0.1f // Loses 10% confidence per hour
            "WATER", "SHELTER" -> 0.02f // Loses 2% confidence per hour
            else -> 0.05f
        }
    }
}
