package net.meshnet.core.mesh.transfer

import net.meshnet.core.mesh.model.Peer
import net.meshnet.core.protocol.MeshPacket
import net.meshnet.core.protocol.PacketType
import java.util.concurrent.PriorityBlockingQueue
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages prioritization of outgoing packets.
 * 
 * Order of Priority:
 * 1. Protocol Messages (ACKs, Routing Updates, Capability Probes) -> Highest
 * 2. Real-time User Data (Messages, Voice Notes, Reports)
 * 3. Bulk Data (File Chunks) -> Lowest
 */
@Singleton
class PriorityQueueManager @Inject constructor() {

    // Thread-safe priority queue. Elements are dequeued based on priority.
    private val queue = PriorityBlockingQueue<PrioritizedPacket>(100, compareBy { it.priority })

    fun enqueue(packet: MeshPacket, targetPeer: Peer) {
        val priority = calculatePriority(packet.type)
        queue.put(PrioritizedPacket(packet, targetPeer, priority))
    }

    fun dequeue(): PrioritizedPacket? {
        return queue.poll()
    }

    private fun calculatePriority(type: PacketType): Int {
        return when (type) {
            // Critical protocol messages (High priority = lower integer value)
            PacketType.ACK,
            PacketType.PEER_ANNOUNCE,
            PacketType.ROUTING_UPDATE,
            PacketType.CAPABILITY_PROBE -> 1
            
            // User interactive messages
            PacketType.MESSAGE,
            PacketType.REPORT,
            PacketType.VOICE_CHUNK -> 5
            
            // Bulk background data
            PacketType.FILE_CHUNK -> 10
            
            PacketType.UNKNOWN,
            PacketType.UNRECOGNIZED -> 100
        }
    }
}

data class PrioritizedPacket(
    val packet: MeshPacket,
    val targetPeer: Peer,
    val priority: Int
)
