package net.meshnet.simulator

import kotlinx.coroutines.flow.Flow
import net.meshnet.core.mesh.model.Peer
import net.meshnet.core.protocol.MeshPacket

/**
 * Represents a simulated node in the mesh network.
 * Provides hooks to control location, capabilities, and lifecycle.
 */
interface VirtualPeer {
    val id: ByteArray
    val properties: Peer
    
    fun setLocation(lat: Double, lon: Double)
    fun setCapabilities(capabilities: ByteArray)
    fun turnOff()
    fun turnOn()
}

/**
 * A virtual transport layer that bridges packets between virtual peers
 * according to the active [NetworkTopology].
 */
interface VirtualTransport {
    /** Connects this transport to the simulated network. */
    fun attachToNetwork(topology: NetworkTopology, localPeer: VirtualPeer)
    
    /** Sets the simulated packet drop rate for this node (0.0 to 1.0). */
    fun setPacketLossRate(rate: Double)
    
    /** Sets the simulated latency in milliseconds for outbound packets. */
    fun setLatency(ms: Long)
}

/**
 * Controls which virtual peers can see each other.
 * Implementations might simulate simple star graphs, random waypoint mobility models,
 * or replay GPS traces.
 */
interface NetworkTopology {
    /** Returns the list of peers currently within range of [peer]. */
    fun getVisiblePeers(peer: VirtualPeer): List<VirtualPeer>
    
    /** Disconnects [peerA] and [peerB]. */
    fun severLink(peerA: VirtualPeer, peerB: VirtualPeer)
    
    /** Connects [peerA] and [peerB]. */
    fun formLink(peerA: VirtualPeer, peerB: VirtualPeer)
    
    /** Emitted whenever the topology graph changes. */
    fun observeChanges(): Flow<TopologyChange>
}

data class TopologyChange(
    val peerA: VirtualPeer,
    val peerB: VirtualPeer,
    val isConnected: Boolean,
    val timestampMs: Long = System.currentTimeMillis()
)
