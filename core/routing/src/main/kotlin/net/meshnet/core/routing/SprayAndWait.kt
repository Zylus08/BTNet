package net.meshnet.core.routing

import net.meshnet.core.mesh.model.Peer
import net.meshnet.core.protocol.MeshPacket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

/**
 * Spray-and-Wait routing.
 *
 * Reference: Spyropoulos et al. (2005). "Spray and Wait: An efficient routing
 * scheme for intermittently connected mobile networks."
 *
 * Phase 1 — Spray: the source node distributes L/2 copies to the first L/2
 * distinct encountered peers (binary spray variant).
 *
 * Phase 2 — Wait: each copy-holder forwards directly to the destination when
 * it encounters them; otherwise holds the copy.
 *
 * This bounds network overhead to L total copies per message while maintaining
 * reasonable delivery probability under random-mobility models.
 */
class SprayAndWait @Inject constructor() : RoutingStrategy {

    override val strategyId: String = "spray_and_wait"

    // packetId (hex) → remaining copies to spray
    private val copyBudget = ConcurrentHashMap<String, AtomicInteger>()

    override fun nextHops(
        packet: MeshPacket,
        availablePeers: List<Peer>,
        localPeerId: ByteArray,
    ): List<Peer> {
        val key = packet.packetId.toByteArray().toHex()
        val budget = copyBudget.getOrPut(key) { AtomicInteger(DEFAULT_COPIES) }

        val candidates = availablePeers.filter { peer ->
            !peer.id.contentEquals(packet.senderId.toByteArray())
        }

        return if (budget.get() > 1) {
            // Spray phase: give floor(budget/2) copies to first available peer
            val copies = budget.get() / 2
            val target = candidates.firstOrNull() ?: return emptyList()
            budget.addAndGet(-copies)
            listOf(target)
        } else {
            // Wait phase: only forward directly to destination
            candidates.filter { peer ->
                peer.id.contentEquals(packet.recipientId.toByteArray())
            }
        }
    }

    override fun onPeerDiscovered(peer: Peer) = Unit
    override fun onPacketReceived(packet: MeshPacket, from: Peer) = Unit
    override fun onDeliveryAck(packetId: ByteArray, via: Peer) {
        copyBudget.remove(packetId.toHex())
    }
    override fun onPeerLost(peer: Peer) = Unit
    override fun deliveryProbability(destination: ByteArray): Float = Float.NaN

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    companion object {
        /** Default copy budget L per message. Configurable via settings. */
        const val DEFAULT_COPIES = 16
    }
}
