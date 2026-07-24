package net.meshnet.core.mesh.ble

import android.os.ParcelUuid
import java.util.UUID

object MeshBleConstants {
    /**
     * Unique 128-bit Service UUID for MeshNet.
     * Used in advertising to filter out non-MeshNet devices, and in GATT server
     * to group our characteristics.
     */
    val MESHNET_SERVICE_UUID: UUID = UUID.fromString("00000000-4d45-5348-4e45-540000000000")
    val MESHNET_SERVICE_PARCEL_UUID = ParcelUuid(MESHNET_SERVICE_UUID)

    /**
     * Characteristic for writing packets to this node (Client -> Server).
     */
    val RX_CHARACTERISTIC_UUID: UUID = UUID.fromString("00000001-4d45-5348-4e45-540000000000")

    /**
     * Characteristic for reading/notifying packets from this node (Server -> Client).
     */
    val TX_CHARACTERISTIC_UUID: UUID = UUID.fromString("00000002-4d45-5348-4e45-540000000000")

    /**
     * Client Characteristic Configuration Descriptor (CCCD) used to enable notifications.
     */
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}
