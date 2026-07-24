package net.meshnet.core.routing

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.meshnet.core.crypto.CryptoEngine
import net.meshnet.core.crypto.KeyManager
import net.meshnet.core.events.EventBus
import net.meshnet.core.events.MeshEvent
import net.meshnet.core.metrics.MetricsCollector
import net.meshnet.core.protocol.MeshPacket
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages End-to-End Acknowledgements (Delivery ACKs).
 * 
 * When a packet reaches its final destination, the destination generates an ACK packet
 * and routes it back to the original sender. The original sender uses this to mark the
 * message as 'Delivered' in the UI and to update routing metrics.
 */
@Singleton
class AckManager @Inject constructor(
    private val eventBus: EventBus,
    private val keyManager: KeyManager,
    private val cryptoEngine: CryptoEngine,
    private val metricsCollector: MetricsCollector,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Tracks packets we originated that are waiting for an ACK.
    // Packet ID (Hex) -> Timestamp Sent
    private val pendingAcks = ConcurrentHashMap<String, Long>()

    fun start() {
        scope.launch {
            eventBus.on<MeshEvent.PacketReceived>().collect { event ->
                val packet = event.packet
                val myId = keyManager.identityPublicKey
                
                // If we are the destination of the packet...
                if (packet.recipientId.toByteArray().contentEquals(myId)) {
                    // And it's not an ACK packet itself
                    if (packet.type != MeshPacket.Type.ACK) {
                        generateAck(packet)
                    } else {
                        processIncomingAck(packet, event.from.id)
                    }
                }
            }
        }
    }

    /**
     * Registers a newly sent message so we can track its latency when the ACK arrives.
     */
    fun expectAck(packetId: ByteArray) {
        pendingAcks[packetId.toHex()] = System.currentTimeMillis()
    }

    private suspend fun generateAck(originalPacket: MeshPacket) {
        Timber.d("Generating Delivery ACK for packet ${originalPacket.packetId.toByteArray().toHex().take(8)}")
        
        // Payload of an ACK is simply the 16-byte Packet ID of the original message
        val ackPayload = originalPacket.packetId.toByteArray()
        
        // In a real implementation, we construct the MeshPacket, encrypt it for the
        // original sender, and pass it to RelayEngine or TransportManager to route back.
        // For this stub, we simulate the structure.
        
        // Pseudo-code for creating the ACK packet:
        // val ackPacket = MeshPacket.newBuilder()
        //     .setType(MeshPacket.Type.ACK)
        //     .setRecipientId(originalPacket.senderId)
        //     ... encrypt and sign ...
        //     .build()
        // 
        // Then route it: eventBus.emit(MeshEvent.PacketForwarded(...))
    }

    private suspend fun processIncomingAck(ackPacket: MeshPacket, viaPeerId: ByteArray) {
        // The payload of the ACK packet contains the original Packet ID
        // Note: In reality, we'd need to decrypt the payload first.
        val originalPacketIdHex = "dummy_hex_id" // cryptoEngine.decrypt(ackPacket).toHex()
        
        val sentTime = pendingAcks.remove(originalPacketIdHex)
        if (sentTime != null) {
            val latencyMs = System.currentTimeMillis() - sentTime
            Timber.i("Received Delivery ACK for $originalPacketIdHex (Latency: ${latencyMs}ms, Hops: ${ackPacket.hopCount})")
            
            metricsCollector.recordDeliveryAck(latencyMs, ackPacket.hopCount)
            
            eventBus.emitSuspend(
                MeshEvent.DeliveryAcknowledged(
                    packetId = originalPacketIdHex.toByteArray(), // fake conversion for stub
                    via = net.meshnet.core.mesh.model.Peer(viaPeerId),
                    latencyMs = latencyMs,
                    hops = ackPacket.hopCount
                )
            )
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
