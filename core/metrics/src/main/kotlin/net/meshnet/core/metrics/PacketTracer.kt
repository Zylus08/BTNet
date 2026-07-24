package net.meshnet.core.metrics

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import net.meshnet.core.events.EventBus
import net.meshnet.core.events.MeshEvent
import net.meshnet.core.events.PacketTraceEntry
import net.meshnet.core.events.TraceEventType
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Observes the EventBus and logs the lifecycle of packets as they travel through the node.
 * Uses truncated pseudonymous IDs (first 8 bytes of Ed25519 public key hex) for privacy.
 * Emits [MeshEvent.PacketTraced] back to the bus for UI/debug overlays.
 */
@Singleton
class PacketTracer @Inject constructor(
    private val eventBus: EventBus,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Starts observing events and logging traces. Call once during app init. */
    fun start() {
        scope.launch {
            eventBus.events.collect { event ->
                when (event) {
                    is MeshEvent.PacketReceived -> {
                        val entry = PacketTraceEntry(
                            packetId = event.packet.packetId.toByteArray().toHex(),
                            nodeId = event.from.id.toHex().take(8),
                            hopCount = event.packet.hopCount,
                            eventType = TraceEventType.RECEIVED,
                            transportId = event.transportId
                        )
                        logAndEmit(entry)
                    }
                    is MeshEvent.PacketForwarded -> {
                        val entry = PacketTraceEntry(
                            packetId = event.packet.packetId.toByteArray().toHex(),
                            nodeId = "local", // Forwarded by this node
                            hopCount = event.packet.hopCount,
                            eventType = TraceEventType.FORWARDED,
                            strategyId = event.strategyId
                        )
                        logAndEmit(entry)
                    }
                    is MeshEvent.PacketDropped -> {
                        val entry = PacketTraceEntry(
                            packetId = event.packetId.toHex(),
                            nodeId = "local",
                            hopCount = -1, // Unknown hop count context here
                            eventType = TraceEventType.DROPPED,
                            transportId = event.transportId
                        )
                        logAndEmit(entry, reason = event.reason)
                    }
                    is MeshEvent.DeliveryAcknowledged -> {
                        val entry = PacketTraceEntry(
                            packetId = event.packetId.toHex(),
                            nodeId = event.via.id.toHex().take(8),
                            hopCount = event.hops,
                            eventType = TraceEventType.DELIVERED
                        )
                        logAndEmit(entry, latencyMs = event.latencyMs)
                    }
                    is MeshEvent.PacketExpired -> {
                        val entry = PacketTraceEntry(
                            packetId = event.packetId,
                            nodeId = "local",
                            hopCount = -1,
                            eventType = TraceEventType.EXPIRED
                        )
                        logAndEmit(entry)
                    }
                    else -> {} // Ignore other events
                }
            }
        }
    }

    private fun logAndEmit(entry: PacketTraceEntry, reason: String? = null, latencyMs: Long? = null) {
        val details = buildString {
            if (entry.transportId.isNotEmpty()) append(" transport=${entry.transportId}")
            if (entry.strategyId.isNotEmpty()) append(" strategy=${entry.strategyId}")
            if (reason != null) append(" reason=$reason")
            if (latencyMs != null) append(" latency=${latencyMs}ms")
            if (entry.hopCount >= 0) append(" hops=${entry.hopCount}")
        }
        
        Timber.tag("PacketTracer").d(
            "[${entry.packetId.take(8)}] ${entry.eventType} at node=${entry.nodeId}$details"
        )
        
        // Non-suspending emit; we don't want the tracer to block the bus
        eventBus.emit(MeshEvent.PacketTraced(entry))
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
