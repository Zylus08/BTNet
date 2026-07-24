package net.meshnet.core.metrics

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.DoubleAdder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Zero-overhead no-op implementation.
 * Injected in release builds where metrics collection has no value.
 * All methods are empty; JIT should eliminate calls entirely.
 */
class NoOpMetricsCollector : MetricsCollector {
    override fun recordPacketSent(transportId: String) = Unit
    override fun recordPacketDropped(reason: DropReason, transportId: String) = Unit
    override fun recordPeerConnected(transportId: String) = Unit
    override fun recordPeerDisconnected(transportId: String) = Unit
    override fun recordPacketForwarded(hopCount: Int, strategyId: String) = Unit
    override fun recordDeliveryAck(latencyMs: Long, hops: Int) = Unit
    override fun recordRoutingUpdate(strategyId: String) = Unit
    override fun recordLatency(category: String, ms: Long) = Unit
    override fun recordBleAdvertisement() = Unit
    override fun recordBleScanResult() = Unit
    override fun recordGattConnectionAttempt() = Unit
    override fun recordGattConnectionSuccess(latencyMs: Long) = Unit
    override fun recordGattConnectionFailure(reason: String) = Unit
    override fun recordMtuNegotiated(mtu: Int) = Unit
    override fun recordFragmentSent() = Unit
    override fun recordFragmentReceived() = Unit
    override fun recordPacketQueued() = Unit
    override fun recordPacketExpired() = Unit
    override fun recordBloomFilterHit() = Unit
    override fun recordBloomFilterMiss() = Unit
    override fun recordReportCreated() = Unit
    override fun recordCorroborationAccepted() = Unit
    override fun recordCorroborationRejected(reason: String) = Unit
    override fun snapshot(): MetricsSnapshot = MetricsSnapshot()
    override fun snapshots(intervalMs: Long): Flow<MetricsSnapshot> = flow { }
}

/**
 * In-memory aggregating implementation.
 *
 * Uses lock-free atomics for counters. Safe to call from any thread or coroutine.
 * Used in:
 *   - Debug builds (with LoggingMetricsCollector as a delegate)
 *   - Benchmarks
 *   - Integration tests (assert delivery rates, drop rates, etc.)
 *
 * Call [reset] between test scenarios.
 */
@Singleton
class AggregatingMetricsCollector @Inject constructor() : MetricsCollector {

    // ── Transport ─────────────────────────────────────────────────────────────
    private val packetsSent = AtomicLong()
    private val packetsDropped = AtomicLong()
    private val dropsPerReason = ConcurrentHashMap<DropReason, AtomicLong>()
    private val peersConnected = AtomicLong()
    private val peersDisconnected = AtomicLong()

    // ── Routing ───────────────────────────────────────────────────────────────
    private val packetsForwarded = AtomicLong()
    private val deliveryAcks = AtomicLong()
    private val deliveryLatencySum = DoubleAdder()
    private val deliveryHopsSum = DoubleAdder()

    // ── BLE ───────────────────────────────────────────────────────────────────
    private val bleAds = AtomicLong()
    private val bleScanResults = AtomicLong()
    private val gattAttempts = AtomicLong()
    private val gattSuccesses = AtomicLong()
    private val gattFailures = AtomicLong()
    private val gattConnectLatencySum = DoubleAdder()
    private val fragmentsSent = AtomicLong()
    private val fragmentsReceived = AtomicLong()

    // ── Storage ───────────────────────────────────────────────────────────────
    private val packetsQueued = AtomicLong()
    private val packetsExpired = AtomicLong()
    private val bloomHits = AtomicLong()
    private val bloomMisses = AtomicLong()

    // ── Trust ─────────────────────────────────────────────────────────────────
    private val reportsCreated = AtomicLong()
    private val corroborationsAccepted = AtomicLong()
    private val corroborationsRejected = AtomicLong()

    // ── Latency ───────────────────────────────────────────────────────────────
    private val latencySums = ConcurrentHashMap<String, DoubleAdder>()
    private val latencyCounts = ConcurrentHashMap<String, AtomicLong>()

    // ── MetricsCollector impl ─────────────────────────────────────────────────

    override fun recordPacketSent(transportId: String) { packetsSent.incrementAndGet() }
    override fun recordPacketDropped(reason: DropReason, transportId: String) {
        packetsDropped.incrementAndGet()
        dropsPerReason.getOrPut(reason) { AtomicLong() }.incrementAndGet()
    }
    override fun recordPeerConnected(transportId: String) { peersConnected.incrementAndGet() }
    override fun recordPeerDisconnected(transportId: String) { peersDisconnected.incrementAndGet() }
    override fun recordPacketForwarded(hopCount: Int, strategyId: String) { packetsForwarded.incrementAndGet() }
    override fun recordDeliveryAck(latencyMs: Long, hops: Int) {
        deliveryAcks.incrementAndGet()
        deliveryLatencySum.add(latencyMs.toDouble())
        deliveryHopsSum.add(hops.toDouble())
    }
    override fun recordRoutingUpdate(strategyId: String) = Unit
    override fun recordLatency(category: String, ms: Long) {
        latencySums.getOrPut(category) { DoubleAdder() }.add(ms.toDouble())
        latencyCounts.getOrPut(category) { AtomicLong() }.incrementAndGet()
    }
    override fun recordBleAdvertisement() { bleAds.incrementAndGet() }
    override fun recordBleScanResult() { bleScanResults.incrementAndGet() }
    override fun recordGattConnectionAttempt() { gattAttempts.incrementAndGet() }
    override fun recordGattConnectionSuccess(latencyMs: Long) {
        gattSuccesses.incrementAndGet()
        gattConnectLatencySum.add(latencyMs.toDouble())
    }
    override fun recordGattConnectionFailure(reason: String) { gattFailures.incrementAndGet() }
    override fun recordMtuNegotiated(mtu: Int) = Unit
    override fun recordFragmentSent() { fragmentsSent.incrementAndGet() }
    override fun recordFragmentReceived() { fragmentsReceived.incrementAndGet() }
    override fun recordPacketQueued() { packetsQueued.incrementAndGet() }
    override fun recordPacketExpired() { packetsExpired.incrementAndGet() }
    override fun recordBloomFilterHit() { bloomHits.incrementAndGet() }
    override fun recordBloomFilterMiss() { bloomMisses.incrementAndGet() }
    override fun recordReportCreated() { reportsCreated.incrementAndGet() }
    override fun recordCorroborationAccepted() { corroborationsAccepted.incrementAndGet() }
    override fun recordCorroborationRejected(reason: String) { corroborationsRejected.incrementAndGet() }

    override fun snapshot(): MetricsSnapshot {
        val ackCount = deliveryAcks.get()
        val gattCount = gattSuccesses.get()
        return MetricsSnapshot(
            timestampMs = System.currentTimeMillis(),
            packetsSent = packetsSent.get(),
            packetsDropped = packetsDropped.get(),
            dropsPerReason = dropsPerReason.mapValues { it.value.get() },
            peersConnected = peersConnected.get(),
            peersDisconnected = peersDisconnected.get(),
            packetsForwarded = packetsForwarded.get(),
            deliveryAcks = ackCount,
            avgDeliveryLatencyMs = if (ackCount > 0) deliveryLatencySum.sum() / ackCount else 0.0,
            avgDeliveryHops = if (ackCount > 0) deliveryHopsSum.sum() / ackCount else 0.0,
            bleAdvertisements = bleAds.get(),
            bleScanResults = bleScanResults.get(),
            gattAttempts = gattAttempts.get(),
            gattSuccesses = gattSuccesses.get(),
            gattFailures = gattFailures.get(),
            avgGattConnectMs = if (gattCount > 0) gattConnectLatencySum.sum() / gattCount else 0.0,
            fragmentsSent = fragmentsSent.get(),
            fragmentsReceived = fragmentsReceived.get(),
            packetsQueued = packetsQueued.get(),
            packetsExpired = packetsExpired.get(),
            bloomHits = bloomHits.get(),
            bloomMisses = bloomMisses.get(),
            reportsCreated = reportsCreated.get(),
            corroborationsAccepted = corroborationsAccepted.get(),
            corroborationsRejected = corroborationsRejected.get(),
            latencyAvgMs = latencySums.mapValues { (k, adder) ->
                val count = latencyCounts[k]?.get() ?: 1L
                adder.sum() / count
            },
        )
    }

    override fun snapshots(intervalMs: Long): Flow<MetricsSnapshot> = flow {
        while (true) {
            kotlinx.coroutines.delay(intervalMs)
            emit(snapshot())
        }
    }

    /** Resets all counters to zero. Use between test scenarios. */
    fun reset() {
        packetsSent.set(0); packetsDropped.set(0); dropsPerReason.clear()
        peersConnected.set(0); peersDisconnected.set(0)
        packetsForwarded.set(0); deliveryAcks.set(0)
        deliveryLatencySum.reset(); deliveryHopsSum.reset()
        bleAds.set(0); bleScanResults.set(0)
        gattAttempts.set(0); gattSuccesses.set(0); gattFailures.set(0)
        gattConnectLatencySum.reset(); fragmentsSent.set(0); fragmentsReceived.set(0)
        packetsQueued.set(0); packetsExpired.set(0)
        bloomHits.set(0); bloomMisses.set(0)
        reportsCreated.set(0); corroborationsAccepted.set(0); corroborationsRejected.set(0)
        latencySums.clear(); latencyCounts.clear()
    }
}
