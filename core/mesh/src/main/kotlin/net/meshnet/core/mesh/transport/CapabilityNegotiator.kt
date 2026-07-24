package net.meshnet.core.mesh.transport

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.meshnet.core.events.EventBus
import net.meshnet.core.events.MeshEvent
import net.meshnet.core.mesh.model.Peer
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks and negotiates capabilities between peers.
 * 
 * Listens for PEER_ANNOUNCE packets and updates the capability registry.
 */
@Singleton
class CapabilityNegotiator @Inject constructor(
    private val eventBus: EventBus,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    // Peer ID Hex -> Capabilities (using boolean flags for simplicity)
    private val peerCapabilities = ConcurrentHashMap<String, PeerCaps>()

    fun start() {
        scope.launch {
            // Wait for capability probe responses or PEER_ANNOUNCE broadcasts
            eventBus.on<MeshEvent.PacketReceived>().collect { event ->
                if (event.packet.type == net.meshnet.core.protocol.MeshPacket.Type.PEER_ANNOUNCE) {
                    val caps = event.packet.capabilities
                    peerCapabilities[event.from.id.toHex()] = PeerCaps(
                        wifiDirect = caps.wifiDirect,
                        fileTransfer = caps.fileTransfer,
                        voice = caps.voice,
                        compression = caps.compression
                    )
                }
            }
        }
    }

    /** Returns true if the given peer is known to support Wi-Fi Direct. */
    fun supportsWifiDirect(peer: Peer): Boolean {
        return peerCapabilities[peer.id.toHex()]?.wifiDirect == true
    }

    /** Returns true if the given peer supports LZ4 payload compression. */
    fun supportsCompression(peer: Peer): Boolean {
        return peerCapabilities[peer.id.toHex()]?.compression == true
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}

data class PeerCaps(
    val wifiDirect: Boolean,
    val fileTransfer: Boolean,
    val voice: Boolean,
    val compression: Boolean,
)
