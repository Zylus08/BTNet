package net.meshnet.core.routing

import net.meshnet.core.mesh.model.Peer
import net.meshnet.core.protocol.MeshPacket
import javax.inject.Inject

/**
 * Epidemic routing — forwards every packet to every available peer.
 *
 * Guarantees: highest delivery probability in sparse networks.
 * Cost: O(peers) transmissions per packet; bounded by TTL and seen-packet filter.
 *
 * Use when: network is sparse, latency tolerance is high, or delivery guarantee
 * is critical (e.g. emergency broadcasts).
 *
 * The caller (RoutingManager) is responsible for seen-packet deduplication via
 * the Bloom filter; this strategy simply returns all peers that are not the sender.
 */
class EpidemicRouting @Inject constructor() : RoutingStrategy {

    override val strategyId: String = "epidemic"

    override fun nextHops(
        packet: MeshPacket,
        availablePeers: List<Peer>,
        localPeerId: ByteArray,
    ): List<Peer> = availablePeers.filter { peer ->
        // Never forward back to sender
        !peer.id.contentEquals(packet.senderId.toByteArray())
    }

    override fun onPeerDiscovered(peer: Peer) = Unit
    override fun onPacketReceived(packet: MeshPacket, from: Peer) = Unit
    override fun onDeliveryAck(packetId: ByteArray, via: Peer) = Unit
    override fun onPeerLost(peer: Peer) = Unit
    override fun deliveryProbability(destination: ByteArray): Float = Float.NaN
}
