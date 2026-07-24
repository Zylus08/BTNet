package net.meshnet.core.mesh.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Manages outbound GATT connections from this node (Client) to other nodes (Servers).
 */
@SuppressLint("MissingPermission") // Caller handles permissions
@Singleton
class MeshGattClient @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    // BluetoothDevice Address -> Gatt instance
    private val activeConnections = mutableMapOf<String, BluetoothGatt>()
    private val deviceMtu = mutableMapOf<String, Int>()
    private val reassemblers = mutableMapOf<String, ChunkReassembler>()

    private val _incomingPayloads = MutableSharedFlow<Pair<BluetoothDevice, ByteArray>>(
        extraBufferCapacity = 64
    )
    val incomingPayloads: Flow<Pair<BluetoothDevice, ByteArray>> = _incomingPayloads.asSharedFlow()

    private val _connectionStateChanges = MutableSharedFlow<Pair<BluetoothDevice, Boolean>>(
        extraBufferCapacity = 64
    )
    val connectionStateChanges: Flow<Pair<BluetoothDevice, Boolean>> = _connectionStateChanges.asSharedFlow()

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val device = gatt.device
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Timber.d("GATT connected to ${device.address}")
                activeConnections[device.address] = gatt
                deviceMtu[device.address] = MeshGattServer.DEFAULT_MTU
                reassemblers[device.address] = ChunkReassembler()
                _connectionStateChanges.tryEmit(device to true)
                
                // Discover services immediately
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Timber.d("GATT disconnected from ${device.address}")
                activeConnections.remove(device.address)
                deviceMtu.remove(device.address)
                reassemblers.remove(device.address)
                _connectionStateChanges.tryEmit(device to false)
                gatt.close()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // Request maximum MTU (512) to improve throughput
                gatt.requestMtu(512)
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Timber.d("GATT Client MTU for ${gatt.device.address} changed to $mtu")
                deviceMtu[gatt.device.address] = mtu
                
                // Now enable notifications on the TX characteristic
                enableNotifications(gatt)
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid == MeshBleConstants.TX_CHARACTERISTIC_UUID) {
                val reassembler = reassemblers[gatt.device.address]
                if (reassembler != null) {
                    try {
                        val completePayload = reassembler.processChunk(characteristic.value)
                        if (completePayload != null) {
                            _incomingPayloads.tryEmit(gatt.device to completePayload)
                        }
                    } catch (e: ReassemblyException) {
                        Timber.e(e, "Reassembly failed for device ${gatt.device.address}")
                    }
                }
            }
        }
    }

    /** Initiates a connection to the given device. */
    fun connect(device: BluetoothDevice) {
        if (activeConnections.containsKey(device.address)) return
        Timber.d("Connecting to ${device.address}...")
        device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    /** Disconnects from the given device. */
    fun disconnect(deviceAddress: String) {
        activeConnections[deviceAddress]?.disconnect()
    }

    fun disconnectAll() {
        activeConnections.values.forEach { it.disconnect() }
    }

    /**
     * Sends a byte array to a connected server by breaking it into MTU-sized chunks
     * and sending them via writes on the RX characteristic.
     */
    fun sendData(deviceAddress: String, data: ByteArray, transferId: Short) {
        val gatt = activeConnections[deviceAddress] ?: return
        val service = gatt.getService(MeshBleConstants.MESHNET_SERVICE_UUID) ?: return
        val rxChar = service.getCharacteristic(MeshBleConstants.RX_CHARACTERISTIC_UUID) ?: return

        val mtu = deviceMtu[deviceAddress] ?: MeshGattServer.DEFAULT_MTU
        val maxPayloadSize = mtu - 3 - PacketFragmenter.HEADER_SIZE

        val chunks = PacketFragmenter.fragment(data, transferId, maxPayloadSize)
        
        for (chunk in chunks) {
            rxChar.value = chunk
            // Same as server: in real app, we must handle onCharacteristicWrite callback queueing.
            gatt.writeCharacteristic(rxChar)
        }
    }

    private fun enableNotifications(gatt: BluetoothGatt) {
        val service = gatt.getService(MeshBleConstants.MESHNET_SERVICE_UUID)
        if (service == null) {
            Timber.e("MeshNet service not found on device ${gatt.device.address}")
            return
        }

        val txChar = service.getCharacteristic(MeshBleConstants.TX_CHARACTERISTIC_UUID)
        if (txChar == null) {
            Timber.e("TX Characteristic not found")
            return
        }

        gatt.setCharacteristicNotification(txChar, true)

        val descriptor = txChar.getDescriptor(MeshBleConstants.CCCD_UUID)
        if (descriptor != null) {
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(descriptor)
        }
    }
}
