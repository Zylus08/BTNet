package net.meshnet.core.mesh.transport.wifi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Monitors the health of active Wi-Fi Direct links.
 * 
 * Wi-Fi Direct connections can drop silently (e.g., peer walks out of range).
 * The Android OS sometimes takes 10+ seconds to report a disconnection.
 * This manager sends lightweight application-layer pings to detect drops faster,
 * allowing the TransportManager to fallback to BLE or find a new route immediately.
 */
@Singleton
class HeartbeatManager @Inject constructor() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var heartbeatJob: Job? = null

    // Simulates an active socket connection that we can ping
    fun startMonitoring(onTimeout: () -> Unit) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(PING_INTERVAL_MS)
                
                // In a real app, send a ping packet and wait for PONG
                val isAlive = pingPeer()
                
                if (!isAlive) {
                    Timber.w("Heartbeat failed. Wi-Fi Direct link assumed dead.")
                    onTimeout()
                    break
                }
            }
        }
    }

    fun stopMonitoring() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private suspend fun pingPeer(): Boolean {
        // Stub: always alive for now
        // A real implementation would write to the TCP socket and wait for a reply
        // with a tight timeout (e.g., 2000ms).
        return true
    }

    companion object {
        const val PING_INTERVAL_MS = 5000L
        const val TIMEOUT_MS = 2000L
    }
}
