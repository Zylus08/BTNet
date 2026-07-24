package net.meshnet.core.routing

import net.meshnet.core.mesh.model.Peer
import net.meshnet.core.protocol.MeshPacket
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/**
 * PRoPHET (Probabilistic Routing Protocol with History of Encounters and Transitivity).
 *
 * Reference: Lindgren et al. (2004). "Probabilistic routing in intermittently connected networks."
 *
 * Delivery probability P(A,B) is updated on each encounter between nodes A and B:
 *   P(A,B) = P(A,B)_old + (1 − P(A,B)_old) × P_INIT
 *
 * Ageing (applied when a peer is not seen for a period):
 *   P(A,B) = P(A,B)_old × γ^k
 *   where γ = GAMMA (ageing factor), k = number of time units elapsed
 *
 * Transitivity (when A meets B, update A's probability to reach C via B):
 *   P(A,C) = P(A,C)_old + (1 − P(A,C)_old) × P(A,B) × P(B,C) × BETA
 *
 * Forwarding: forward to a peer if their delivery probability to the destination
 * exceeds our own. This creates a directed gradient toward the destination.
 */
class PRoPHETRouting @Inject constructor() : RoutingStrategy {

    override val strategyId: String = "prophet"

    // localId → destinationId → probability
    private val deliveryTable = ConcurrentHashMap<String, Float>()

    // peerId → last encounter time (ms)
    private val lastEncounterMs = ConcurrentHashMap<String, Long>()

    // peerId → their delivery table (received via ROUTING_UPDATE)
    private val peerTables = ConcurrentHashMap<String, Map<String, Float>>()

    override fun nextHops(
        packet: MeshPacket,
        availablePeers: List<Peer>,
        localPeerId: ByteArray,
    ): List<Peer> {
        val destination = packet.recipientId.toByteArray().toHex()
        val localProb = deliveryTable[destination] ?: 0f

        return availablePeers.filter { peer ->
            if (peer.id.contentEquals(packet.senderId.toByteArray())) return@filter false
            val peerProb = peerTables[peer.id.toHex()]?.get(destination) ?: 0f
            peerProb > localProb
        }
    }

    override fun onPeerDiscovered(peer: Peer) {
        val peerKey = peer.id.toHex()
        val now = System.currentTimeMillis()

        // Update encounter probability P(local, peer)
        val old = deliveryTable[peerKey] ?: 0f
        val updated = old + (1f - old) * P_INIT
        deliveryTable[peerKey] = updated.coerceIn(0f, 1f)
        lastEncounterMs[peerKey] = now

        // Apply transitivity for destinations known via this peer
        peerTables[peerKey]?.forEach { (destination, peerProb) ->
            if (destination == peerKey) return@forEach
            val current = deliveryTable[destination] ?: 0f
            val transitive = current + (1f - current) * updated * peerProb * BETA
            deliveryTable[destination] = transitive.coerceIn(0f, 1f)
        }
    }

    override fun onPacketReceived(packet: MeshPacket, from: Peer) {
        // Re-use onPeerDiscovered to update encounter probability
        onPeerDiscovered(from)
    }

    override fun onDeliveryAck(packetId: ByteArray, via: Peer) = Unit

    override fun onPeerLost(peer: Peer) {
        // Apply ageing when we lose contact
        applyAgeing(peer.id.toHex())
    }

    override fun deliveryProbability(destination: ByteArray): Float =
        deliveryTable[destination.toHex()] ?: 0f

    /**
     * Called when a ROUTING_UPDATE packet is received from a peer.
     * Stores the peer's delivery table for transitivity calculations.
     */
    fun updatePeerTable(peerId: ByteArray, table: Map<String, Float>) {
        peerTables[peerId.toHex()] = table
    }

    /** Returns a copy of the local delivery table for sharing with peers. */
    fun localTable(): Map<String, Float> = deliveryTable.toMap()

    // ── Private ───────────────────────────────────────────────────────────────

    private fun applyAgeing(peerKey: String) {
        val lastSeen = lastEncounterMs[peerKey] ?: return
        val elapsed = System.currentTimeMillis() - lastSeen
        val units = (elapsed / AGEING_UNIT_MS).toInt().coerceAtLeast(1)
        val current = deliveryTable[peerKey] ?: return
        deliveryTable[peerKey] = (current * Math.pow(GAMMA.toDouble(), units.toDouble())).toFloat()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    companion object {
        /** Initial encounter probability increment. */
        const val P_INIT = 0.75f
        /** Ageing factor per time unit. */
        const val GAMMA = 0.98f
        /** Transitivity scaling factor. */
        const val BETA = 0.25f
        /** Duration of one ageing unit in ms. */
        const val AGEING_UNIT_MS = 30_000L
    }
}
