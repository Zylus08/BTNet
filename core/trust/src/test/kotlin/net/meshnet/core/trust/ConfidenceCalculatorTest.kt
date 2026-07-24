package net.meshnet.core.trust

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConfidenceCalculatorTest {

    // ── Witness component ─────────────────────────────────────────────────────

    @Test
    fun `zero witnesses gives zero witness component`() {
        val score = ConfidenceCalculator.witnessComponent(0)
        assertEquals(0.0, score, 0.001)
    }

    @Test
    fun `nine witnesses saturates witness component at 1`() {
        val score = ConfidenceCalculator.witnessComponent(9)
        assertEquals(1.0, score, 0.001)
    }

    @Test
    fun `witness component increases monotonically`() {
        val scores = (0..9).map { ConfidenceCalculator.witnessComponent(it) }
        scores.zipWithNext().forEach { (a, b) ->
            assertTrue(b >= a, "Witness component not monotonically increasing")
        }
    }

    // ── Distance component ────────────────────────────────────────────────────

    @Test
    fun `zero distance gives distance component of 1`() {
        val score = ConfidenceCalculator.distanceComponent(0.0)
        assertEquals(1.0, score, 0.001)
    }

    @Test
    fun `distance component decreases with distance`() {
        val d0 = ConfidenceCalculator.distanceComponent(0.0)
        val d1 = ConfidenceCalculator.distanceComponent(1.0)
        val d5 = ConfidenceCalculator.distanceComponent(5.0)
        assertTrue(d0 > d1)
        assertTrue(d1 > d5)
    }

    @Test
    fun `negative distance treated as zero`() {
        val score = ConfidenceCalculator.distanceComponent(-1.0)
        assertEquals(1.0, score, 0.001)
    }

    // ── Time decay component ──────────────────────────────────────────────────

    @Test
    fun `freshly created report has time component near 1`() {
        val now = System.currentTimeMillis()
        val score = ConfidenceCalculator.timeDecayComponent(createdAtMs = now, nowMs = now)
        assertEquals(1.0, score, 0.001)
    }

    @Test
    fun `24h old report has very low time component`() {
        val now = System.currentTimeMillis()
        val createdAt = now - 24 * 3_600_000L
        val score = ConfidenceCalculator.timeDecayComponent(createdAtMs = createdAt, nowMs = now)
        assertTrue(score < 0.1, "Expected score < 0.1 for 24h old report, got $score")
    }

    @Test
    fun `time component decreases over time`() {
        val base = System.currentTimeMillis()
        val t0 = ConfidenceCalculator.timeDecayComponent(base, base)
        val t6 = ConfidenceCalculator.timeDecayComponent(base - 6 * 3_600_000L, base)
        val t12 = ConfidenceCalculator.timeDecayComponent(base - 12 * 3_600_000L, base)
        assertTrue(t0 > t6)
        assertTrue(t6 > t12)
    }

    // ── History component ─────────────────────────────────────────────────────

    @Test
    fun `unknown originator gives neutral history score`() {
        val score = ConfidenceCalculator.historyComponent(0, 0, 0)
        assertEquals(0.5, score, 0.001)
    }

    @Test
    fun `perfect history gives high score`() {
        val score = ConfidenceCalculator.historyComponent(10, 0, 10)
        assertTrue(score > 0.9)
    }

    @Test
    fun `all flagged history gives minimum score`() {
        val score = ConfidenceCalculator.historyComponent(0, 10, 10)
        assertEquals(0.2, score, 0.001) // clamped to HISTORY_MIN
    }

    // ── Full calculation ──────────────────────────────────────────────────────

    @Test
    fun `score is in 0 to 1 range for all inputs`() {
        val now = System.currentTimeMillis()
        val score = ConfidenceCalculator.calculate(
            uniqueWitnesses = 5,
            avgWitnessDistKm = 0.5,
            createdAtMs = now - 2 * 3_600_000L,
            nowMs = now,
            reportsCorroborated = 3,
            reportsFlagged = 1,
            totalReports = 5,
        )
        assertTrue(score.value in 0f..1f)
    }

    @Test
    fun `high witness count close up recently gives HIGH confidence level`() {
        val now = System.currentTimeMillis()
        val score = ConfidenceCalculator.calculate(
            uniqueWitnesses = 9,
            avgWitnessDistKm = 0.1,
            createdAtMs = now - 30_000L,
            nowMs = now,
            reportsCorroborated = 8,
            reportsFlagged = 0,
            totalReports = 10,
        )
        assertEquals(ConfidenceLevel.HIGH, score.level, "Expected HIGH confidence, got ${score.level} (${score.value})")
    }

    @Test
    fun `zero witnesses gives LOW confidence level`() {
        val now = System.currentTimeMillis()
        val score = ConfidenceCalculator.calculate(
            uniqueWitnesses = 0,
            avgWitnessDistKm = 0.0,
            createdAtMs = now,
            nowMs = now,
        )
        assertEquals(ConfidenceLevel.LOW, score.level)
    }
}
