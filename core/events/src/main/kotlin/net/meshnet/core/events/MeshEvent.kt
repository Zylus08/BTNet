package net.meshnet.core.events

import net.meshnet.core.mesh.model.Peer
import net.meshnet.core.protocol.MeshPacket

/**
 * Sealed hierarchy of all domain events flowing through the EventBus.
 *
 * Architecture:
 *   Transport → EventBus → Routing → Storage → UI
 *
 * All cross-module communication uses events; modules never call each other directly.
 * This keeps the dependency graph acyclic and every module independently testable.
 *
 * Naming convention: past tense (something happened), not imperative (do this).
 */
sealed interface MeshEvent {

    // ── Transport events ──────────────────────────────────────────────────────

    /** A packet was received from [from] on transport [transportId]. */
    data class PacketReceived(
        val packet: MeshPacket,
        val from: Peer,
        val transportId: String,
        val receivedAtMs: Long = System.currentTimeMillis(),
    ) : MeshEvent

    /** A packet was successfully sent to [to]. */
    data class PacketSent(
        val packet: MeshPacket,
        val to: Peer,
        val transportId: String,
    ) : MeshEvent

    /** A packet was dropped and will not be forwarded. */
    data class PacketDropped(
        val packetId: ByteArray,
        val reason: String,
        val transportId: String = "",
    ) : MeshEvent

    /** A peer became reachable. */
    data class PeerDiscovered(val peer: Peer, val transportId: String) : MeshEvent

    /** A peer is no longer reachable. */
    data class PeerLost(val peer: Peer, val transportId: String) : MeshEvent

    /** A peer's capabilities were updated (e.g. after capability probe response). */
    data class PeerCapabilitiesUpdated(val peer: Peer) : MeshEvent

    // ── Routing events ────────────────────────────────────────────────────────

    /** The routing layer forwarded [packet] to [peers]. */
    data class PacketForwarded(
        val packet: MeshPacket,
        val peers: List<Peer>,
        val strategyId: String,
    ) : MeshEvent

    /** A delivery ACK was received confirming end-to-end delivery. */
    data class DeliveryAcknowledged(
        val packetId: ByteArray,
        val via: Peer,
        val latencyMs: Long,
        val hops: Int,
    ) : MeshEvent

    /** Routing tables were updated (e.g. PRoPHET exchange). */
    data class RoutingTableUpdated(val strategyId: String) : MeshEvent

    // ── Storage events ────────────────────────────────────────────────────────

    /** A message was persisted and is available for display. */
    data class MessageStored(val packetId: String, val senderId: String) : MeshEvent

    /** A queued packet expired (TTL or expiry time elapsed). */
    data class PacketExpired(val packetId: String) : MeshEvent

    // ── Trust events ──────────────────────────────────────────────────────────

    /** A community report was created locally or received from the mesh. */
    data class ReportReceived(val reportId: String, val category: String) : MeshEvent

    /** A report's confidence score changed. */
    data class ReportConfidenceUpdated(
        val reportId: String,
        val newScore: Float,
        val witnessCount: Int,
    ) : MeshEvent

    /** A report was flagged as stale by the local user. */
    data class ReportFlaggedStale(val reportId: String) : MeshEvent

    // ── System events ─────────────────────────────────────────────────────────

    /** Mesh service started. */
    data object MeshStarted : MeshEvent

    /** Mesh service stopped (user action or system). */
    data object MeshStopped : MeshEvent

    /** A feature flag value changed at runtime. */
    data class FeatureFlagChanged(val flag: String, val enabled: Boolean) : MeshEvent

    /** A packet tracing entry was recorded. */
    data class PacketTraced(val entry: PacketTraceEntry) : MeshEvent
}

/**
 * A single hop record for packet tracing.
 * Uses a truncated pseudonymous node ID (first 8 bytes of public key hex)
 * to avoid exposing full identity in logs.
 */
data class PacketTraceEntry(
    val packetId: String,         // hex
    val nodeId: String,           // 8-char truncated pseudonymous ID
    val hopNumber: Int,
    val eventType: TraceEventType,
    val timestampMs: Long = System.currentTimeMillis(),
    val transportId: String = "",
    val strategyId: String = "",
)

enum class TraceEventType {
    ORIGINATED,   // packet created at this node
    RECEIVED,     // packet received from a peer
    FORWARDED,    // packet forwarded to peer(s)
    DELIVERED,    // ACK received; packet reached destination
    DROPPED,      // packet dropped at this hop
    EXPIRED,      // TTL reached zero
}
