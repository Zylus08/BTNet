package net.meshnet.core.mesh.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import net.meshnet.core.metrics.MetricsCollector
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Scans for BLE devices advertising the MeshNet service UUID.
 * Emits discovered devices containing the 8-byte ephemeral ID via [scannedDevices].
 */
@SuppressLint("MissingPermission") // Caller handles permissions
@Singleton
class BLEScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val metricsCollector: MetricsCollector,
) {
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        manager.adapter
    }

    private val scanner by lazy { bluetoothAdapter?.bluetoothLeScanner }

    private val _scannedDevices = MutableSharedFlow<MeshBleDevice>(
        extraBufferCapacity = 64
    )
    
    /** Flow of raw discovered devices. */
    val scannedDevices: Flow<MeshBleDevice> = _scannedDevices.asSharedFlow()

    private var isScanning = false

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.let { processScanResult(it) }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            results?.forEach { processScanResult(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            isScanning = false
            Timber.e("BLE Scan failed with error code $errorCode")
        }
    }

    fun start() {
        if (isScanning) return
        if (scanner == null) {
            Timber.e("BLE Scanner not available")
            return
        }

        val filter = ScanFilter.Builder()
            .setServiceUuid(MeshBleConstants.MESHNET_SERVICE_PARCEL_UUID)
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()

        try {
            scanner?.startScan(listOf(filter), settings, scanCallback)
            isScanning = true
            Timber.d("BLE Scanning started")
        } catch (e: Exception) {
            Timber.e(e, "Exception starting BLE scanner")
        }
    }

    fun stop() {
        if (!isScanning) return
        try {
            scanner?.stopScan(scanCallback)
            isScanning = false
            Timber.d("BLE Scanning stopped")
        } catch (e: Exception) {
            Timber.e(e, "Exception stopping BLE scanner")
        }
    }

    private fun processScanResult(result: ScanResult) {
        val scanRecord = result.scanRecord ?: return
        val serviceData = scanRecord.getServiceData(MeshBleConstants.MESHNET_SERVICE_PARCEL_UUID)

        if (serviceData != null && serviceData.size == EphemeralIdManager.ID_LENGTH_BYTES) {
            metricsCollector.recordBleScanResult()
            val device = MeshBleDevice(
                bluetoothDevice = result.device,
                ephemeralId = serviceData,
                rssi = result.rssi
            )
            _scannedDevices.tryEmit(device)
        }
    }
}
