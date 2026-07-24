package net.meshnet.core.mesh.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
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
 * BLE implementation of [MeshTransport].
 * Orchestrates advertising, scanning, connection management, and data transfer.
 */
@Singleton
class BLETransport @Inject constructor(
    @ApplicationContext private val context: Context,
    private val advertiser: BLEAdvertiser,
    private val scanner: BLEScanner,
    private val discoveryManager: PeerDiscoveryManager,
    private val gattConnectionManager: GattConnectionManager,
    private val gattClient: MeshGattClient,
    private val gattServer: MeshGattServer,
) : MeshTransport {

    override val transportId: String = "ble"
    override val displayName: String = "Bluetooth LE"

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        manager.adapter
    }

    override val isAvailable: Boolean
        get() = bluetoothAdapter?.isEnabled == true

    private val isRunning = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _incomingPackets = MutableSharedFlow<IncomingPacket>(extraBufferCapacity = 128)
    private val _connectedPeers = MutableStateFlow<List<Peer>>(emptyList())
    private val _events = MutableSharedFlow<TransportEvent>(extraBufferCapacity = 64)

    private val activePeers = mutableMapOf<String, Peer>()

    init {
        // Observe new peer connections
        scope.launch {
            gattConnectionManager.peerConnected.collect { peer ->
                val hexId = peer.id.toHex()
                activePeers[hexId] = peer
                updatePeersFlow()
                _events.emit(TransportEvent.PeerConnected(peer))
            }
        }

        // Observe peer disconnections
        scope.launch {
            gattConnectionManager.peerDisconnected.collect { address ->
                // Find which peer had this address. (In a full implementation, we'd map address -> Peer)
                // For now, we clear the whole map and rebuild if needed, but really GattConnectionManager
                // should emit the Peer ID or object on disconnect.
                // Simplified for this stub:
                activePeers.values.find { it.id.toHex() == address }?.let { peer ->
                    activePeers.remove(peer.id.toHex())
                    updatePeersFlow()
                    _events.emit(TransportEvent.PeerDisconnected(peer, "GATT disconnected"))
                }
            }
        }

        // Observe incoming payloads from Client connections
        scope.launch {
            gattClient.incomingPayloads.collect { (device, payload) ->
                handleRawPayload(device.address, payload)
            }
        }

        // Observe incoming payloads from Server connections
        scope.launch {
            gattServer.incomingPayloads.collect { (device, payload) ->
                handleRawPayload(device.address, payload)
            }
        }
    }

    override suspend fun start() {
        if (!isRunning.compareAndSet(false, true)) return
        Timber.i("Starting BLE Transport")
        
        gattServer.start()
        discoveryManager.start()
        _events.emit(TransportEvent.Started)
    }

    override suspend fun stop() {
        if (!isRunning.compareAndSet(true, false)) return
        Timber.i("Stopping BLE Transport")

        advertiser.stop()
        scanner.stop()
        gattClient.disconnectAll()
        gattServer.stop()
        activePeers.clear()
        updatePeersFlow()
        _events.emit(TransportEvent.Stopped)
    }

    override suspend fun advertise() {
        if (isRunning.get()) advertiser.start()
    }

    override suspend fun scan() {
        if (isRunning.get()) scanner.start()
    }

    override suspend fun send(packet: MeshPacket, peer: Peer): Result<Unit> {
        if (!isRunning.get()) return Result.failure(IllegalStateException("Transport not running"))
        
        // Find if this peer is connected via Client or Server.
        // In a complete implementation, GattConnectionManager tracks whether a peer 
        // is connected via Client or Server and provides the exact MAC address.
        // We simulate sending data here.
        val rawData = packet.toByteArray()
        val transferId = (packet.packetId.hashCode() and 0xFFFF).toShort()
        
        // TODO: Map peer.id to device address and route to correct GATT role.
        // gattClient.sendData(address, rawData, transferId)
        
        return Result.success(Unit)
    }

    override fun incomingPackets(): Flow<IncomingPacket> = _incomingPackets.asSharedFlow()

    override fun connectedPeers(): Flow<List<Peer>> = _connectedPeers.asStateFlow()

    override fun events(): Flow<TransportEvent> = _events.asSharedFlow()

    private fun handleRawPayload(deviceAddress: String, payload: ByteArray) {
        // Skip magic HELLO packets
        if (payload.isNotEmpty() && payload[0] == GattConnectionManager.MAGIC_HELLO) return

        try {
            val packet = MeshPacket.parseFrom(payload)
            val peerId = gattConnectionManager.getPeerId(deviceAddress)
            if (peerId != null) {
                val peer = activePeers[peerId.toHex()] ?: Peer(peerId)
                _incomingPackets.tryEmit(IncomingPacket(packet, peer, transportId))
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse MeshPacket from $deviceAddress")
        }
    }

    private fun updatePeersFlow() {
        _connectedPeers.value = activePeers.values.toList()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
