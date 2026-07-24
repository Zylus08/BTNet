package net.meshnet.core.mesh.ble

import android.bluetooth.BluetoothDevice

/**
 * Represents a raw BLE device discovered during scanning.
 * Used internally before establishing a GATT connection and identifying
 * the full Peer (with 32-byte Ed25519 ID).
 */
data class MeshBleDevice(
    val bluetoothDevice: BluetoothDevice,
    val ephemeralId: ByteArray,          // 8-byte rotating ID
    val rssi: Int,
    val lastSeenMs: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MeshBleDevice) return false
        return bluetoothDevice.address == other.bluetoothDevice.address
    }

    override fun hashCode(): Int = bluetoothDevice.address.hashCode()
}
