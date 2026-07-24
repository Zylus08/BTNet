package net.meshnet.core.mesh.transport

import kotlinx.coroutines.flow.Flow
import net.meshnet.core.mesh.model.Peer
import net.meshnet.core.protocol.MeshPacket

/**
 * Abstraction over all physical transports available to MeshNet.
 *
 * Implementations:
 *   - [BLETransport]          — Bluetooth Low Energy (always-on, fallback)
 *   - [WifiDirectTransport]   — Wi-Fi Direct (high-bandwidth, opportunistic)
 *   - LANTransport            — Local Wi-Fi LAN (future)
 *   - LoRaTransport           — LoRa radio (future)
 *   - InternetBridgeTransport — Internet relay (future; optional, opt-in)
 *
 * Lifecycle: [start] → advertise + scan → [send]/[incomingPackets] → [stop]
 *
 * Thread safety: implementations must be safe to call from any coroutine context.
 * [incomingPackets] and [connectedPeers] are cold flows; collecting multiple times
 * is safe but each collector receives independent updates.
 */
interface MeshTransport {

    /** Unique identifier for this transport (e.g. "ble", "wifidirect"). */
    val transportId: String

    /** Human-readable name for logging and UI (e.g. "Bluetooth LE"). */
    val displayName: String

    /**
     * Whether this transport is currently available on the device.
     * BLE might be disabled; Wi-Fi Direct might be unsupported on some devices.
     */
    val isAvailable: Boolean

    /**
     * Initialises and starts the transport.
     * Safe to call multiple times; idempotent if already started.
     */
    suspend fun start()

    /**
     * Tears down all connections and stops advertising/scanning.
     * Safe to call multiple times; idempotent if already stopped.
     */
    suspend fun stop()

    /**
     * Begins advertising this device's presence to nearby peers.
     * Must be called after [start].
     */
    suspend fun advertise()

    /**
     * Begins scanning for nearby peers.
     * Must be called after [start].
     */
    suspend fun scan()

    /**
     * Sends [packet] to the transport layer for delivery.
     *
     * The transport is responsible for connection management.
     * Returns [Result.failure] on immediate send failure; deferred failures arrive
     * via [TransportEvent.SendFailed] on [events].
     */
    suspend fun send(packet: MeshPacket, peer: Peer): Result<Unit>

    /**
     * Hot [Flow] of packets received from any connected peer.
     * Packets are delivered as-received; no ordering guarantees.
     * The caller (routing layer) is responsible for deduplication and validation.
     */
    fun incomingPackets(): Flow<IncomingPacket>

    /**
     * Hot [Flow] of currently connected peers.
     * Emits a new list on every connection/disconnection event.
     */
    fun connectedPeers(): Flow<List<Peer>>

    /**
     * Hot [Flow] of transport-level events (connect, disconnect, errors).
     */
    fun events(): Flow<TransportEvent>
}

/** A packet received from a peer on this transport. */
data class IncomingPacket(
    val packet: MeshPacket,
    val from: Peer,
    val transportId: String,
    val receivedAtMs: Long = System.currentTimeMillis(),
)

/** Transport lifecycle and error events. */
sealed interface TransportEvent {
    data class PeerConnected(val peer: Peer) : TransportEvent
    data class PeerDisconnected(val peer: Peer, val reason: String) : TransportEvent
    data class SendFailed(val peer: Peer, val packetId: ByteArray, val cause: Throwable) : TransportEvent
    data class Error(val cause: Throwable, val message: String) : TransportEvent
    data object Started : TransportEvent
    data object Stopped : TransportEvent
}
