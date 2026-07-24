package net.meshnet.core.mesh.ble

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridges scanning and connection establishment.
 * Listens to [BLEScanner] and instructs [MeshGattClient] to connect to new devices.
 * Implements cooldowns to prevent connection spam storms.
 */
@Singleton
class PeerDiscoveryManager @Inject constructor(
    private val scanner: BLEScanner,
    private val gattClient: MeshGattClient,
    private val gattConnectionManager: GattConnectionManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // MAC Address -> timestamp of last connection attempt
    private val connectionAttempts = ConcurrentHashMap<String, Long>()

    fun start() {
        scope.launch {
            scanner.scannedDevices.collect { device ->
                handleDiscoveredDevice(device)
            }
        }
    }

    private fun handleDiscoveredDevice(device: MeshBleDevice) {
        val address = device.bluetoothDevice.address

        // 1. Are we already connected and authenticated?
        if (gattConnectionManager.getPeerId(address) != null) {
            return
        }

        // 2. Is there a recent connection attempt in progress or cooldown?
        val lastAttempt = connectionAttempts[address] ?: 0L
        if (System.currentTimeMillis() - lastAttempt < CONNECTION_COOLDOWN_MS) {
            return
        }

        // 3. Initiate connection
        Timber.d("Discovered new peer advertising ${device.ephemeralId.toHex()}, attempting GATT connect...")
        connectionAttempts[address] = System.currentTimeMillis()
        gattClient.connect(device.bluetoothDevice)
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    companion object {
        /** Wait at least 15 seconds before retrying a failed or disconnected peer. */
        const val CONNECTION_COOLDOWN_MS = 15_000L
    }
}
