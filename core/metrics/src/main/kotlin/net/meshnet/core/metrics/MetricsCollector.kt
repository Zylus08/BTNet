package net.meshnet.core.metrics

import kotlinx.coroutines.flow.Flow

/**
 * Metrics collection interface for all MeshNet subsystems.
 *
 * Every subsystem (transport, routing, storage, trust) should hold a reference
 * to a [MetricsCollector] and call its methods at key events. This allows:
 *   - Performance regression detection in CI
 *   - Routing algorithm comparison in benchmarks
 *   - Battery consumption attribution per subsystem
 *   - Real-time debugging during development
 *
 * Implementations:
 *   [NoOpMetricsCollector] — zero overhead; used in production release builds
 *   [LoggingMetricsCollector] — Timber logging; used in debug builds
 *   [AggregatingMetricsCollector] — in-memory counters; used in benchmarks/tests
 */
interface MetricsCollector {

    // ── Transport ─────────────────────────────────────────────────────────────

    /** A packet was successfully handed to the transport layer for transmission. */
    fun recordPacketSent(transportId: String = "")

    /** A packet was dropped (validation failure, rate limit, TTL=0, bloom hit). */
    fun recordPacketDropped(reason: DropReason, transportId: String = "")

    /** A new peer connection was established on any transport. */
    fun recordPeerConnected(transportId: String = "")

    /** A peer disconnected from any transport. */
    fun recordPeerDisconnected(transportId: String = "")

    // ── Routing ───────────────────────────────────────────────────────────────

    /** A packet was forwarded to [hopCount] peers by the routing layer. */
    fun recordPacketForwarded(hopCount: Int, strategyId: String = "")

    /** A delivery ACK was received for a previously forwarded packet. */
    fun recordDeliveryAck(latencyMs: Long, hops: Int)

    /** Routing table update received or sent. */
    fun recordRoutingUpdate(strategyId: String = "")

    // ── Latency ───────────────────────────────────────────────────────────────

    /**
     * Records an observed latency measurement.
     *
     * @param category e.g. "peer_discovery", "gatt_connect", "message_e2e"
     * @param ms       observed duration in milliseconds
     */
    fun recordLatency(category: String, ms: Long)

    // ── BLE ───────────────────────────────────────────────────────────────────

    fun recordBleAdvertisement()
    fun recordBleScanResult()
    fun recordGattConnectionAttempt()
    fun recordGattConnectionSuccess(latencyMs: Long)
    fun recordGattConnectionFailure(reason: String)
    fun recordMtuNegotiated(mtu: Int)
    fun recordFragmentSent()
    fun recordFragmentReceived()

    // ── Storage ───────────────────────────────────────────────────────────────

    fun recordPacketQueued()
    fun recordPacketExpired()
    fun recordBloomFilterHit()    // duplicate detected
    fun recordBloomFilterMiss()   // packet is new

    // ── Trust ─────────────────────────────────────────────────────────────────

    fun recordReportCreated()
    fun recordCorroborationAccepted()
    fun recordCorroborationRejected(reason: String)

    // ── Snapshots ─────────────────────────────────────────────────────────────

    /** Returns a point-in-time snapshot of all accumulated counters. */
    fun snapshot(): MetricsSnapshot

    /** Flow of periodic snapshots; emits every [intervalMs] milliseconds. */
    fun snapshots(intervalMs: Long = 5_000): Flow<MetricsSnapshot>
}

enum class DropReason {
    BLOOM_FILTER,       // already seen
    RATE_LIMIT,         // sender throttled
    REPLAY,             // replay attack
    VALIDATION_FAILED,  // malformed packet
    TTL_EXPIRED,        // hop budget exhausted
    NO_ROUTE,           // no eligible peer
    SIGNATURE_INVALID,  // Ed25519 verification failed
}

/**
 * Immutable snapshot of all metrics counters at a point in time.
 */
data class MetricsSnapshot(
    val timestampMs: Long = System.currentTimeMillis(),

    // Transport
    val packetsSent: Long = 0,
    val packetsDropped: Long = 0,
    val dropsPerReason: Map<DropReason, Long> = emptyMap(),
    val peersConnected: Long = 0,
    val peersDisconnected: Long = 0,

    // Routing
    val packetsForwarded: Long = 0,
    val deliveryAcks: Long = 0,
    val avgDeliveryLatencyMs: Double = 0.0,
    val avgDeliveryHops: Double = 0.0,

    // BLE
    val bleAdvertisements: Long = 0,
    val bleScanResults: Long = 0,
    val gattAttempts: Long = 0,
    val gattSuccesses: Long = 0,
    val gattFailures: Long = 0,
    val avgGattConnectMs: Double = 0.0,
    val fragmentsSent: Long = 0,
    val fragmentsReceived: Long = 0,

    // Storage
    val packetsQueued: Long = 0,
    val packetsExpired: Long = 0,
    val bloomHits: Long = 0,
    val bloomMisses: Long = 0,

    // Trust
    val reportsCreated: Long = 0,
    val corroborationsAccepted: Long = 0,
    val corroborationsRejected: Long = 0,

    // Latency histograms (category → avg ms)
    val latencyAvgMs: Map<String, Double> = emptyMap(),
) {
    val bloomFilterFprEstimate: Double
        get() {
            val total = bloomHits + bloomMisses
            return if (total == 0L) 0.0 else bloomHits.toDouble() / total.toDouble()
        }

    val deliveryRate: Double
        get() {
            val total = deliveryAcks + packetsDropped
            return if (total == 0L) 0.0 else deliveryAcks.toDouble() / total.toDouble()
        }
}
