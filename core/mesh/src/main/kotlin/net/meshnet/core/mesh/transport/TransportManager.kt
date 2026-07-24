package net.meshnet.core.mesh.transport

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.meshnet.core.mesh.model.Peer
import net.meshnet.core.protocol.Capabilities
import net.meshnet.core.protocol.MeshPacket
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the set of available [MeshTransport] implementations and routes
 * outbound packets through the best available transport per peer.
 *
 * Transport selection policy (in priority order):
 *   1. Wi-Fi Direct — if peer [Capabilities.wifiDirect] is true and transport is available
 *   2. BLE         — always-on fallback
 *
 * All incoming packets from all transports are merged into a single [incomingPackets] flow.
 * The routing layer consumes this unified flow.
 */
@Singleton
class TransportManager @Inject constructor(
    private val transports: Set<@JvmSuppressWildcards MeshTransport>,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _incomingPackets = MutableSharedFlow<IncomingPacket>(extraBufferCapacity = 256)
    val incomingPackets: Flow<IncomingPacket> = _incomingPackets.asSharedFlow()

    private val _connectedPeers = MutableStateFlow<List<Peer>>(emptyList())
    val connectedPeers: Flow<List<Peer>> = _connectedPeers.asStateFlow()

    private val _events = MutableSharedFlow<TransportEvent>(extraBufferCapacity = 64)
    val events: Flow<TransportEvent> = _events.asSharedFlow()

    /** Starts all available transports and begins advertising + scanning. */
    suspend fun startAll() {
        transports.filter { it.isAvailable }.forEach { transport ->
            runCatching {
                transport.start()
                transport.advertise()
                transport.scan()
                collectFrom(transport)
            }.onFailure { e ->
                Timber.e(e, "Failed to start transport: ${transport.displayName}")
            }
        }
    }

    /** Stops all transports gracefully. */
    suspend fun stopAll() {
        transports.forEach { t ->
            runCatching { t.stop() }.onFailure { e ->
                Timber.e(e, "Error stopping transport: ${t.displayName}")
            }
        }
    }

    /**
     * Sends [packet] to [peer] via the best available transport.
     *
     * @return [Result.success] if enqueued; [Result.failure] if no transport available.
     */
    suspend fun send(packet: MeshPacket, peer: Peer): Result<Unit> {
        val transport = selectTransport(peer)
            ?: return Result.failure(NoTransportAvailableException(peer))
        return transport.send(packet, peer)
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private fun selectTransport(peer: Peer): MeshTransport? {
        // Wi-Fi Direct preferred for capable peers
        if (peer.capabilities.wifiDirect) {
            transports.find { it.transportId == TRANSPORT_WIFI_DIRECT && it.isAvailable }
                ?.let { return it }
        }
        // BLE fallback
        return transports.find { it.transportId == TRANSPORT_BLE && it.isAvailable }
    }

    private fun collectFrom(transport: MeshTransport) {
        scope.launch {
            transport.incomingPackets().collect { packet ->
                _incomingPackets.emit(packet)
            }
        }
        scope.launch {
            transport.events().collect { event ->
                _events.emit(event)
                updatePeerList()
            }
        }
    }

    private fun updatePeerList() {
        scope.launch {
            // Aggregate connected peers across all transports (dedup by peer ID)
            val all = transports
                .flatMap { t ->
                    runCatching {
                        // Collect current value from state flow; non-blocking
                        emptyList<Peer>() // placeholder — real impl subscribes per-transport
                    }.getOrDefault(emptyList())
                }
                .distinctBy { it.id.toList() }
            _connectedPeers.value = all
        }
    }

    companion object {
        const val TRANSPORT_BLE = "ble"
        const val TRANSPORT_WIFI_DIRECT = "wifidirect"
    }
}

class NoTransportAvailableException(peer: Peer) :
    Exception("No transport available to reach peer ${peer.id.take(8).toByteArray().contentToString()}")
