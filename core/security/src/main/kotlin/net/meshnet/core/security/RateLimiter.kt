package net.meshnet.core.security

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Token-bucket rate limiter per sender ID.
 *
 * Each sender gets [BUCKET_CAPACITY] tokens. Tokens refill at
 * [REFILL_RATE_PER_SECOND] tokens per second. A packet costs 1 token.
 *
 * A sender whose bucket is empty is throttled: [tryConsume] returns false
 * and the caller must drop the packet.
 *
 * This mitigates flooding and spam attacks while allowing burst traffic
 * from legitimate peers (e.g. initial sync after reconnect).
 *
 * Thread-safe: per-sender buckets use atomic operations.
 */
@Singleton
class RateLimiter @Inject constructor() {

    private data class Bucket(
        @Volatile var tokens: Double,
        @Volatile var lastRefillMs: Long,
    )

    private val buckets = ConcurrentHashMap<String, Bucket>()

    /**
     * Attempts to consume one token for [senderId].
     *
     * @return true if the token was consumed (packet allowed); false if throttled.
     */
    fun tryConsume(senderId: String): Boolean {
        val now = System.currentTimeMillis()
        val bucket = buckets.getOrPut(senderId) {
            Bucket(tokens = BUCKET_CAPACITY.toDouble(), lastRefillMs = now)
        }

        synchronized(bucket) {
            // Refill tokens based on elapsed time
            val elapsed = (now - bucket.lastRefillMs) / 1000.0
            bucket.tokens = (bucket.tokens + elapsed * REFILL_RATE_PER_SECOND)
                .coerceAtMost(BUCKET_CAPACITY.toDouble())
            bucket.lastRefillMs = now

            return if (bucket.tokens >= 1.0) {
                bucket.tokens -= 1.0
                true
            } else {
                false
            }
        }
    }

    /**
     * Removes rate-limit state for peers not seen recently.
     * Call periodically (e.g. every hour) to prevent memory growth.
     */
    fun evictStale(olderThanMs: Long) {
        val threshold = System.currentTimeMillis() - olderThanMs
        buckets.entries.removeIf { (_, bucket) -> bucket.lastRefillMs < threshold }
    }

    /** Resets the bucket for [senderId]. Use when a peer disconnects cleanly. */
    fun reset(senderId: String) {
        buckets.remove(senderId)
    }

    companion object {
        /** Maximum tokens (burst capacity) per sender. */
        const val BUCKET_CAPACITY = 20

        /** Token refill rate per second per sender. */
        const val REFILL_RATE_PER_SECOND = 2.0
    }
}
