package net.meshnet.core.mesh.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import net.meshnet.core.metrics.MetricsCollector
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles BLE Advertising.
 * Broadcasts the 128-bit MeshNet service UUID and the current 8-byte ephemeral ID.
 * Automatically restarts advertising when the ephemeral ID rotates.
 */
@SuppressLint("MissingPermission") // Caller (MeshService) handles permissions
@Singleton
class BLEAdvertiser @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ephemeralIdManager: EphemeralIdManager,
    private val metricsCollector: MetricsCollector,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var rotationJob: Job? = null

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        manager.adapter
    }

    private val advertiser by lazy { bluetoothAdapter?.bluetoothLeAdvertiser }

    private var isAdvertising = false

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            isAdvertising = true
            metricsCollector.recordBleAdvertisement()
            Timber.d("BLE Advertising started successfully")
        }

        override fun onStartFailure(errorCode: Int) {
            isAdvertising = false
            Timber.e("BLE Advertising failed to start: error code $errorCode")
        }
    }

    /**
     * Starts BLE advertising. Will continuously observe [EphemeralIdManager]
     * and restart the advertiser whenever the ID rotates.
     */
    fun start() {
        if (rotationJob?.isActive == true) return

        if (advertiser == null) {
            Timber.e("BLE Advertiser not available on this device")
            return
        }

        rotationJob = scope.launch {
            ephemeralIdManager.currentId.collectLatest { ephemeralId ->
                restartAdvertising(ephemeralId)
            }
        }
    }

    /** Stops BLE advertising. */
    fun stop() {
        rotationJob?.cancel()
        rotationJob = null
        stopAdvertisingInternal()
    }

    private fun restartAdvertising(ephemeralId: ByteArray) {
        stopAdvertisingInternal()

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(true)
            .setTimeout(0) // No timeout; we manage it manually
            .build()

        val data = AdvertiseData.Builder()
            .addServiceUuid(MeshBleConstants.MESHNET_SERVICE_PARCEL_UUID)
            // Embed the 8-byte ephemeral ID in Service Data
            .addServiceData(MeshBleConstants.MESHNET_SERVICE_PARCEL_UUID, ephemeralId)
            .setIncludeTxPowerLevel(false)
            .setIncludeDeviceName(false) // Saves space, improves privacy
            .build()

        try {
            advertiser?.startAdvertising(settings, data, advertiseCallback)
        } catch (e: Exception) {
            Timber.e(e, "Exception starting BLE advertiser")
        }
    }

    private fun stopAdvertisingInternal() {
        if (!isAdvertising) return
        try {
            advertiser?.stopAdvertising(advertiseCallback)
            isAdvertising = false
            Timber.d("BLE Advertising stopped")
        } catch (e: Exception) {
            Timber.e(e, "Exception stopping BLE advertiser")
        }
    }
}
