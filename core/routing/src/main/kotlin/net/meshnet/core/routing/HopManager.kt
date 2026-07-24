package net.meshnet.core.routing

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.meshnet.core.events.EventBus
import net.meshnet.core.events.MeshEvent
import net.meshnet.core.metrics.MetricsCollector
import net.meshnet.core.protocol.MeshPacket
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages packet TTL (Time-to-Live) and Hop Counts.
 * Drops packets if TTL expires.
 */
@Singleton
class HopManager @Inject constructor(
    private val eventBus: EventBus,
    private val metricsCollector: MetricsCollector,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun start() {
        scope.launch {
            eventBus.on<MeshEvent.PacketReceived>().collect { event ->
                processPacket(event.packet, event.transportId)
            }
        }
    }

    private fun processPacket(packet: MeshPacket, transportId: String) {
        if (packet.ttl <= 1) {
            Timber.d("Packet ${packet.packetId.toByteArray().toHex().take(8)} dropped: TTL expired")
            metricsCollector.recordPacketDropped(net.meshnet.core.metrics.DropReason.TTL_EXPIRED, transportId)
            eventBus.emit(MeshEvent.PacketDropped(packet.packetId.toByteArray(), "TTL expired", transportId))
            return
        }

        // In a real implementation, we would create a new packet instance with decremented TTL
        // and incremented hop_count, and then pass it to RelayEngine. 
        // Note: For Ed25519 signature validity, mutable fields (TTL/hop) must either be 
        // excluded from the signature payload, or the signature must wrap an immutable inner payload.
        
        // Pass through to RelayEngine is handled by EventBus. 
        // RelayEngine listens to PacketReceived too, but it should probably listen to a 
        // 'PacketValidatedAndReadyForRelay' event instead. 
        // To keep the stub simple, we'll let RelayEngine do the TTL check directly or assume 
        // HopManager is just a tracker.
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
