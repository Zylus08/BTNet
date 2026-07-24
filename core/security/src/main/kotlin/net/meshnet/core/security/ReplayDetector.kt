package net.meshnet.core.security

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Detects replayed packets using a sliding time-window + ID cache.
 *
 * Two-tier defence:
 *   Tier 1 — Bloom filter (SeenPacketBloomFilter in core:storage): O(1), ~1.8 MB,
 *             handles the common case of legitimate deduplication.
 *   Tier 2 — This class: exact-match cache for the recent replay window
 *             (WINDOW_MS), providing cryptographic certainty for recent packets.
 *
 * A packet is a replay if:
 *   - Its [packetId] has been seen within [WINDOW_MS], AND
 *   - Its [timestampMs] falls within ±[TIMESTAMP_TOLERANCE_MS] of the originally
 *     seen packet's timestamp (prevents trivially bumped timestamps).
 *
 * Cache is bounded to [MAX_ENTRIES] to prevent memory exhaustion under flooding.
 * Oldest entries are evicted on overflow.
 */
@Singleton
class ReplayDetector @Inject constructor() {

    private data class SeenEntry(val timestampMs: Long, val seenAtMs: Long)

    // packetId (hex) → SeenEntry
    private val recentPackets = ConcurrentHashMap<String, SeenEntry>()

    /**
     * Checks if [packetId] with [timestampMs] is a replay.
     *
     * Side effect: records the packet as seen if it is not a replay.
     *
     * @return true if replay (should be dropped); false if fresh.
     */
    fun isReplay(packetId: ByteArray, timestampMs: Long): Boolean {
        evictExpired()
        val key = packetId.toHex()
        val existing = recentPackets[key]

        return if (existing != null &&
            kotlin.math.abs(existing.timestampMs - timestampMs) <= TIMESTAMP_TOLERANCE_MS
        ) {
            true  // replay detected
        } else {
            // Evict on overflow before inserting
            if (recentPackets.size >= MAX_ENTRIES) {
                evictOldest()
            }
            recentPackets[key] = SeenEntry(timestampMs, System.currentTimeMillis())
            false
        }
    }

    /** Removes entries older than [WINDOW_MS]. */
    private fun evictExpired() {
        val cutoff = System.currentTimeMillis() - WINDOW_MS
        recentPackets.entries.removeIf { (_, entry) -> entry.seenAtMs < cutoff }
    }

    /** Evicts the oldest 10% of entries when at capacity. */
    private fun evictOldest() {
        val sorted = recentPackets.entries.sortedBy { it.value.seenAtMs }
        sorted.take(MAX_ENTRIES / 10).forEach { recentPackets.remove(it.key) }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    companion object {
        /** Window during which a duplicate packetId is considered a replay. */
        const val WINDOW_MS = 10 * 60 * 1000L  // 10 minutes

        /** Tolerance for timestamp differences between a packet and its replay. */
        const val TIMESTAMP_TOLERANCE_MS = 5_000L  // 5 seconds

        /** Maximum entries to prevent memory exhaustion under flooding. */
        const val MAX_ENTRIES = 50_000
    }
}
