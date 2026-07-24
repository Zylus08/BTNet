package net.meshnet.core.mesh.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hosts the local GATT server.
 * Allows other nodes (GATT clients) to connect to us and exchange packets.
 */
@SuppressLint("MissingPermission") // Caller handles permissions
@Singleton
class MeshGattServer @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val bluetoothManager: BluetoothManager = 
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

    private var gattServer: BluetoothGattServer? = null

    // Track connected devices and their MTU
    private val connectedDevices = ConcurrentHashMap<String, BluetoothDevice>()
    private val deviceMtu = ConcurrentHashMap<String, Int>()

    // Track state for incoming fragmented writes per device
    private val reassemblers = ConcurrentHashMap<String, ChunkReassembler>()

    // Emits complete assembled payloads received from connected clients
    private val _incomingPayloads = MutableSharedFlow<Pair<BluetoothDevice, ByteArray>>(
        extraBufferCapacity = 64
    )
    val incomingPayloads: Flow<Pair<BluetoothDevice, ByteArray>> = _incomingPayloads.asSharedFlow()

    private val serverCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            val address = device.address
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                Timber.d("GATT Client connected: $address")
                connectedDevices[address] = device
                // Default MTU before negotiation
                deviceMtu[address] = DEFAULT_MTU 
                reassemblers[address] = ChunkReassembler()
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                Timber.d("GATT Client disconnected: $address")
                connectedDevices.remove(address)
                deviceMtu.remove(address)
                reassemblers.remove(address)
            }
        }

        override fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
            Timber.d("GATT Server MTU changed for ${device.address} to $mtu")
            deviceMtu[device.address] = mtu
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            if (characteristic.uuid == MeshBleConstants.RX_CHARACTERISTIC_UUID) {
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
                }
                
                val reassembler = reassemblers[device.address]
                if (reassembler != null) {
                    try {
                        val completePayload = reassembler.processChunk(value)
                        if (completePayload != null) {
                            _incomingPayloads.tryEmit(device to completePayload)
                        }
                    } catch (e: ReassemblyException) {
                        Timber.e(e, "Reassembly failed for device ${device.address}")
                    }
                }
            } else {
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED, offset, null)
                }
            }
        }
    }

    fun start() {
        if (gattServer != null) return

        gattServer = bluetoothManager.openGattServer(context, serverCallback)
        if (gattServer == null) {
            Timber.e("Failed to open GATT server")
            return
        }

        val service = BluetoothGattService(
            MeshBleConstants.MESHNET_SERVICE_UUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )

        val rxCharacteristic = BluetoothGattCharacteristic(
            MeshBleConstants.RX_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )

        val txCharacteristic = BluetoothGattCharacteristic(
            MeshBleConstants.TX_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        )

        service.addCharacteristic(rxCharacteristic)
        service.addCharacteristic(txCharacteristic)

        gattServer?.addService(service)
        Timber.d("GATT Server started")
    }

    fun stop() {
        gattServer?.close()
        gattServer = null
        connectedDevices.clear()
        deviceMtu.clear()
        reassemblers.clear()
        Timber.d("GATT Server stopped")
    }

    /**
     * Sends a byte array to a connected client by breaking it into MTU-sized chunks
     * and sending them via notifications on the TX characteristic.
     */
    fun sendData(device: BluetoothDevice, data: ByteArray, transferId: Short) {
        val server = gattServer ?: return
        val service = server.getService(MeshBleConstants.MESHNET_SERVICE_UUID) ?: return
        val txChar = service.getCharacteristic(MeshBleConstants.TX_CHARACTERISTIC_UUID) ?: return

        // MTU - 3 bytes overhead for GATT Notification
        val mtu = deviceMtu[device.address] ?: DEFAULT_MTU
        val maxPayloadSize = mtu - 3 - PacketFragmenter.HEADER_SIZE

        val chunks = PacketFragmenter.fragment(data, transferId, maxPayloadSize)
        
        for (chunk in chunks) {
            txChar.value = chunk
            // In a real implementation with high throughput, we'd need to wait for 
            // onNotificationSent callback between chunks to avoid dropping packets.
            // For now, fire and forget (assuming Android BLE stack buffers it).
            server.notifyCharacteristicChanged(device, txChar, false)
        }
    }

    companion object {
        const val DEFAULT_MTU = 23
    }
}
