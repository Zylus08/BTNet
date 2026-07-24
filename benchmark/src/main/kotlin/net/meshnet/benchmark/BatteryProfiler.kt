package net.meshnet.benchmark

/**
 * Microbenchmark suite estimating battery drain across different radio states.
 * Uses analytical power consumption models derived from Android hardware profiles.
 */
class BatteryProfiler {
    
    // Approximated from AOSP power_profile.xml for average mid-range device
    private val IDLE_CURRENT_MA = 5.0
    private val BLE_SCAN_CURRENT_MA = 15.0
    private val BLE_ADVERTISE_CURRENT_MA = 10.0
    private val WIFI_DIRECT_ACTIVE_MA = 250.0
    private val CPU_ACTIVE_MA = 120.0

    fun calculateEstimatedDrain(
        durationSeconds: Int,
        activeWifiTransferSeconds: Int,
        cpuActiveSeconds: Int
    ): Double {
        var totalMah = 0.0
        
        // Base idle + BLE Mesh continuous scanning/advertising
        val baseMeshCurrent = IDLE_CURRENT_MA + BLE_SCAN_CURRENT_MA + BLE_ADVERTISE_CURRENT_MA
        totalMah += (baseMeshCurrent * (durationSeconds / 3600.0))
        
        // High bandwidth bursts
        totalMah += (WIFI_DIRECT_ACTIVE_MA * (activeWifiTransferSeconds / 3600.0))
        
        // CPU processing (crypto, routing, compression)
        totalMah += (CPU_ACTIVE_MA * (cpuActiveSeconds / 3600.0))
        
        return totalMah
    }
}
