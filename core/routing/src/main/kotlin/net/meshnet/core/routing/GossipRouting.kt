package net.meshnet.core.routing

import net.meshnet.core.mesh.model.Peer
import net.meshnet.core.protocol.MeshPacket
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlin.random.Random

/**
 * Gossip routing for routing-table dissemination.
 *
 * Used exclusively for ROUTING_UPDATE packets (PRoPHET delivery tables, peer
 * announcements, etc.). Not used for user message forwarding.
 *
 * Algorithm:
 *   On receiving a ROUTING_UPDATE, forward it to a random subset of [FAN_OUT]
 *   connected peers. This achieves O(log N) convergence in well-connected graphs
 *   while limiting redundant transmissions.
 *
 * Anti-entropy: peers periodically exchange full routing state summaries
 * to reconcile diverged views (implemented in SyncManager, Phase 5).
 */
class GossipRouting @Inject constructor() : RoutingStrategy {

    override val strategyId: String = "gossip"

    // Track which peers have already received which ROUTING_UPDATE packet
    private val delivered = ConcurrentHashMap<String, MutableSet<String>>()

    override fun nextHops(
        packet: MeshPacket,
        availablePeers: List<Peer>,
        localPeerId: ByteArray,
    ): List<Peer> {
        val packetKey = packet.packetId.toByteArray().toHex()
        val alreadySent = delivered.getOrPut(packetKey) {
            ConcurrentHashMap.newKeySet<String>().also { set ->
                set.add(packet.senderId.toByteArray().toHex())
            }
        }

        val candidates = availablePeers.filter { peer ->
            peer.id.toHex() !in alreadySent
        }

        val selected = candidates.shuffled(Random).take(FAN_OUT)
        selected.forEach { peer -> alreadySent.add(peer.id.toHex()) }

        // Prune old entries to prevent unbounded memory growth
        if (delivered.size > MAX_TRACKED_PACKETS) {
            val oldest = delivered.keys.take(delivered.size - MAX_TRACKED_PACKETS)
            oldest.forEach { delivered.remove(it) }
        }

        return selected
    }

    override fun onPeerDiscovered(peer: Peer) = Unit
    override fun onPacketReceived(packet: MeshPacket, from: Peer) = Unit
    override fun onDeliveryAck(packetId: ByteArray, via: Peer) {
        delivered.remove(packetId.toHex())
    }
    override fun onPeerLost(peer: Peer) = Unit
    override fun deliveryProbability(destination: ByteArray): Float = Float.NaN

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
    private fun Peer.toHex(): String = id.toHex()

    companion object {
        /** Number of peers to forward each gossip message to. */
        const val FAN_OUT = 3
        /** Maximum number of packet IDs to track in memory. */
        const val MAX_TRACKED_PACKETS = 10_000
    }
}
