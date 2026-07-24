package net.meshnet.core.routing

import net.meshnet.core.mesh.model.Peer
import net.meshnet.core.protocol.MeshPacket

/**
 * Plugin interface for mesh routing algorithms.
 *
 * Each implementation encapsulates a specific forwarding strategy.
 * The [RoutingManager] selects and composes strategies based on
 * packet priority, congestion state, and peer capabilities.
 *
 * Implementations must be thread-safe. All methods may be called from
 * multiple coroutines concurrently.
 *
 * Current implementations:
 *   - [EpidemicRouting]   — flood to all; maximum delivery guarantee
 *   - [PRoPHETRouting]    — probabilistic; delivery-history-based forwarding
 *   - [SprayAndWait]      — limited copies; congestion-aware
 *   - [GossipRouting]     — random peer subset; routing table dissemination
 *
 * Future:
 *   - ML-based routing trained on historical delivery data
 */
interface RoutingStrategy {

    /** Stable identifier for this strategy (e.g. "epidemic", "prophet"). */
    val strategyId: String

    /**
     * Returns the ordered list of peers to forward [packet] to.
     *
     * The list may be empty (drop), contain one peer (unicast forward),
     * or multiple peers (multi-path / epidemic spray).
     *
     * Implementations must not modify [packet] or [availablePeers].
     *
     * @param packet         the packet to be forwarded
     * @param availablePeers all currently reachable peers
     * @param localPeerId    this device's public key; used to avoid self-loops
     */
    fun nextHops(
        packet: MeshPacket,
        availablePeers: List<Peer>,
        localPeerId: ByteArray,
    ): List<Peer>

    /**
     * Called when a new peer is discovered (PEER_ANNOUNCE received).
     * Use to update delivery probability tables or peer histories.
     */
    fun onPeerDiscovered(peer: Peer)

    /**
     * Called when a packet is received (before forwarding decision).
     * Use to update encounter history, delivery estimates, etc.
     */
    fun onPacketReceived(packet: MeshPacket, from: Peer)

    /**
     * Called when a delivery ACK is received for a previously forwarded packet.
     *
     * @param packetId the 16-byte UUID of the acknowledged packet
     * @param via      the peer through which delivery was confirmed
     */
    fun onDeliveryAck(packetId: ByteArray, via: Peer)

    /**
     * Estimated delivery probability to [destination] using this strategy.
     * Returns a value in [0.0, 1.0].
     * Returns [Float.NaN] if this strategy doesn't track delivery probability.
     */
    fun deliveryProbability(destination: ByteArray): Float

    /**
     * Called when a peer disconnects. Allows strategies to demote or remove
     * stale delivery estimates.
     */
    fun onPeerLost(peer: Peer)
}
