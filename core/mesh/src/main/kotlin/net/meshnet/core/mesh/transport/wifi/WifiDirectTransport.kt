package net.meshnet.core.mesh.transport.wifi

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
import net.meshnet.core.mesh.transport.IncomingPacket
import net.meshnet.core.mesh.transport.MeshTransport
import net.meshnet.core.mesh.transport.TransportEvent
import net.meshnet.core.protocol.MeshPacket
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wi-Fi Direct implementation of [MeshTransport].
 * Used for high-bandwidth tasks like file transfers and voice notes.
 */
@Singleton
class WifiDirectTransport @Inject constructor(
    private val negotiator: WifiDirectGroupOwnerNegotiator,
) : MeshTransport {

    override val transportId: String = "wifidirect"
    override val displayName: String = "Wi-Fi Direct"

    // Assume available if hardware supports it (checked elsewhere via WifiManager)
    override val isAvailable: Boolean = true

    private val isRunning = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _incomingPackets = MutableSharedFlow<IncomingPacket>(extraBufferCapacity = 128)
    private val _connectedPeers = MutableStateFlow<List<Peer>>(emptyList())
    private val _events = MutableSharedFlow<TransportEvent>(extraBufferCapacity = 64)

    private val activePeers = mutableMapOf<String, Peer>()
    
    // In a full implementation, we'd have Socket connections here
    // private val socketManager = SocketManager()

    init {
        scope.launch {
            negotiator.connectionEvents.collect { event ->
                when (event) {
                    is ConnectionEvent.Connected -> {
                        Timber.i("Wi-Fi Direct connected. GroupOwner=${event.isGroupOwner}")
                        // Once connected, we establish standard TCP/UDP sockets
                        // socketManager.start(event.isGroupOwner, event.groupOwnerAddress)
                        
                        // For the stub, we just pretend a peer connected
                        val peer = Peer(ByteArray(32) { 1 }) // Dummy
                        activePeers[peer.id.toHex()] = peer
                        _connectedPeers.value = activePeers.values.toList()
                        _events.emit(TransportEvent.PeerConnected(peer))
                    }
                    is ConnectionEvent.Disconnected -> {
                        Timber.i("Wi-Fi Direct disconnected.")
                        // socketManager.stop()
                        val peers = activePeers.values.toList()
                        activePeers.clear()
                        _connectedPeers.value = emptyList()
                        peers.forEach { peer ->
                            _events.emit(TransportEvent.PeerDisconnected(peer, "Wi-Fi Direct loss"))
                        }
                    }
                    is ConnectionEvent.Failed -> {
                        // Handled internally, maybe emit a transport event
                    }
                }
            }
        }
    }

    override suspend fun start() {
        if (!isRunning.compareAndSet(false, true)) return
        Timber.i("Starting Wi-Fi Direct Transport")
        negotiator.start()
        _events.emit(TransportEvent.Started)
    }

    override suspend fun stop() {
        if (!isRunning.compareAndSet(true, false)) return
        Timber.i("Stopping Wi-Fi Direct Transport")
        negotiator.stop()
        // socketManager.stop()
        activePeers.clear()
        _connectedPeers.value = emptyList()
        _events.emit(TransportEvent.Stopped)
    }

    override suspend fun advertise() {
        // Handled via BLE usually, but we could make ourselves discoverable over P2P here
    }

    override suspend fun scan() {
        // Wi-Fi direct scan is expensive; we rely on BLE for discovery
    }

    override suspend fun send(packet: MeshPacket, peer: Peer): Result<Unit> {
        if (!isRunning.get()) return Result.failure(IllegalStateException("Transport not running"))
        if (!activePeers.containsKey(peer.id.toHex())) {
            return Result.failure(IllegalStateException("Peer not connected via Wi-Fi Direct"))
        }

        // Send via sockets
        // val bytes = packet.toByteArray()
        // socketManager.send(bytes)
        
        return Result.success(Unit)
    }

    override fun incomingPackets(): Flow<IncomingPacket> = _incomingPackets.asSharedFlow()

    override fun connectedPeers(): Flow<List<Peer>> = _connectedPeers.asStateFlow()

    override fun events(): Flow<TransportEvent> = _events.asSharedFlow()

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
