package net.meshnet.core.mesh.ble

import android.bluetooth.BluetoothDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import net.meshnet.core.crypto.KeyManager
import net.meshnet.core.mesh.model.Peer
import timber.log.Timber
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Manages the identity exchange over a raw GATT connection.
 * 
 * When a GATT connection forms (either Client or Server), both sides immediately
 * send a 32-byte HELLO containing their Ed25519 public key.
 * Once both sides receive the HELLO, a full [Peer] object is created and emitted.
 */
@Singleton
class GattConnectionManager @Inject constructor(
    private val gattClient: MeshGattClient,
    private val gattServer: MeshGattServer,
    private val keyManager: KeyManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Device Address -> 32-byte Identity Public Key
    private val establishedPeers = ConcurrentHashMap<String, ByteArray>()

    private val _peerConnected = MutableSharedFlow<Peer>(extraBufferCapacity = 64)
    val peerConnected: SharedFlow<Peer> = _peerConnected.asSharedFlow()

    private val _peerDisconnected = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val peerDisconnected: SharedFlow<String> = _peerDisconnected.asSharedFlow()

    init {
        scope.launch {
            gattClient.connectionStateChanges.collect { (device, isConnected) ->
                handleConnectionStateChange(device, isConnected, isClient = true)
            }
        }

        scope.launch {
            gattClient.incomingPayloads.collect { (device, payload) ->
                handleIncomingPayload(device, payload, isClient = true)
            }
        }

        scope.launch {
            gattServer.incomingPayloads.collect { (device, payload) ->
                handleIncomingPayload(device, payload, isClient = false)
            }
        }
    }

    private fun handleConnectionStateChange(device: BluetoothDevice, isConnected: Boolean, isClient: Boolean) {
        if (isConnected) {
            // Send HELLO (just our 32-byte public key prefixed with a magic byte to distinguish from MeshPackets)
            val myPubKey = keyManager.identityPublicKey
            val helloMsg = ByteArray(1 + myPubKey.size).apply {
                this[0] = MAGIC_HELLO
                System.arraycopy(myPubKey, 0, this, 1, myPubKey.size)
            }
            
            // Random transfer ID for the fragmenter
            val transferId = Random.nextInt(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            
            if (isClient) {
                gattClient.sendData(device.address, helloMsg, transferId)
            } else {
                gattServer.sendData(device, helloMsg, transferId)
            }
        } else {
            val pubKey = establishedPeers.remove(device.address)
            if (pubKey != null) {
                _peerDisconnected.tryEmit(device.address)
            }
        }
    }

    private fun handleIncomingPayload(device: BluetoothDevice, payload: ByteArray, isClient: Boolean) {
        if (payload.isEmpty()) return

        if (payload[0] == MAGIC_HELLO && payload.size == 33) {
            // Process HELLO
            val peerPubKey = payload.copyOfRange(1, 33)
            establishedPeers[device.address] = peerPubKey
            
            val peer = Peer(
                id = peerPubKey,
                lastSeenMs = System.currentTimeMillis()
            )
            Timber.i("Identity exchange complete with ${device.address}. Peer ID: ${peerPubKey.toHex().take(8)}")
            _peerConnected.tryEmit(peer)
        } else {
            // Regular MeshPacket data, to be routed to EventBus
            // (Handled by BLETransport)
        }
    }

    fun getPeerId(deviceAddress: String): ByteArray? = establishedPeers[deviceAddress]

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    companion object {
        const val MAGIC_HELLO: Byte = 0x48 // 'H'
    }
}
