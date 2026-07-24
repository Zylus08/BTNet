package net.meshnet.core.storage.bloom

import com.google.common.hash.BloomFilter
import com.google.common.hash.Funnels
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Seen-packet deduplication using a Bloom filter.
 *
 * Properties:
 *   - Capacity : 1,000,000 packet IDs
 *   - False positive rate: < 0.1% (1 in 1000)
 *   - Memory : ~1.8 MB (approx 14.4 bits/element at 0.1% FPR)
 *   - Lookups : O(1)
 *
 * Zero false negatives: if [mightContain] returns false, the packet has
 * definitely never been seen. If true, it probably has (< 0.1% FPR).
 *
 * Persistence:
 *   - Written to disk on [persist]; loaded on construction via [tryLoad].
 *   - Weekly automatic reset (controlled by caller) prevents unbounded growth.
 *
 * Thread safety: all mutations protected by [mutex].
 */
@Singleton
class SeenPacketBloomFilter @Inject constructor(
    @Named("bloom_filter_file") private val persistFile: File,
) {
    private val mutex = Mutex()
    private var filter: BloomFilter<ByteArray> = createFilter()

    init {
        tryLoad()
    }

    /**
     * Returns true if [packetId] was probably already seen (< 0.1% false positive rate).
     * Returns false if [packetId] was definitely NOT seen.
     */
    suspend fun mightContain(packetId: ByteArray): Boolean = mutex.withLock {
        filter.mightContain(packetId)
    }

    /**
     * Marks [packetId] as seen.
     * This operation is irreversible for the current filter instance.
     */
    suspend fun put(packetId: ByteArray) = mutex.withLock {
        filter.put(packetId)
    }

    /**
     * Persists the current filter to disk.
     * Call on app pause / background transition.
     */
    suspend fun persist() = mutex.withLock {
        runCatching {
            FileOutputStream(persistFile).use { filter.writeTo(it) }
            Timber.d("Bloom filter persisted to ${persistFile.absolutePath}")
        }.onFailure { e ->
            Timber.e(e, "Failed to persist bloom filter")
        }
    }

    /**
     * Resets the filter to empty state and deletes the persisted file.
     * Call weekly to prevent stale entry accumulation.
     */
    suspend fun reset() = mutex.withLock {
        filter = createFilter()
        persistFile.delete()
        Timber.i("Bloom filter reset")
    }

    /** Approximate false positive probability of the current filter. */
    suspend fun approximateFpp(): Double = mutex.withLock {
        filter.expectedFpp()
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private fun tryLoad() {
        if (!persistFile.exists()) return
        runCatching {
            FileInputStream(persistFile).use { input ->
                filter = BloomFilter.readFrom(input, Funnels.byteArrayFunnel())
            }
            Timber.d("Bloom filter loaded from ${persistFile.absolutePath}")
        }.onFailure { e ->
            Timber.w(e, "Failed to load bloom filter; starting fresh")
            filter = createFilter()
        }
    }

    private fun createFilter(): BloomFilter<ByteArray> =
        BloomFilter.create(
            Funnels.byteArrayFunnel(),
            EXPECTED_INSERTIONS,
            FALSE_POSITIVE_PROBABILITY,
        )

    companion object {
        /** Expected number of distinct packet IDs before false positive rate degrades. */
        const val EXPECTED_INSERTIONS = 1_000_000L
        /** Target false positive probability. */
        const val FALSE_POSITIVE_PROBABILITY = 0.001  // 0.1%
    }
}
