package net.meshnet.core.routing

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles fast hop-by-hop retransmissions.
 * 
 * If a packet is handed to a transport (e.g. BLE) and the transport fails to deliver it
 * immediately (e.g. connection dropped during fragmentation), this scheduler will attempt
 * a short-term retry before falling back to long-term DTN store-and-forward.
 */
@Singleton
class RetransmissionScheduler @Inject constructor() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Packet ID -> Number of attempts
    private val retryCounts = ConcurrentHashMap<String, Int>()

    /**
     * Schedules a retry for a failed packet send.
     * @param packetId The ID of the packet that failed
     * @param action The suspend block to execute to retry sending
     */
    fun scheduleRetry(packetId: String, action: suspend () -> Unit) {
        val attempts = retryCounts.getOrDefault(packetId, 0)
        
        if (attempts >= MAX_RETRIES) {
            Timber.w("Packet $packetId failed after $MAX_RETRIES attempts. Deferring to DTN.")
            retryCounts.remove(packetId)
            return
        }

        val backoffMs = BASE_BACKOFF_MS * (1 shl attempts)
        retryCounts[packetId] = attempts + 1

        scope.launch {
            Timber.d("Scheduling retry ${attempts + 1} for packet $packetId in ${backoffMs}ms")
            delay(backoffMs)
            action()
        }
    }

    /** Clears retry state for a packet if it succeeded. */
    fun cancelRetry(packetId: String) {
        retryCounts.remove(packetId)
    }

    companion object {
        const val MAX_RETRIES = 3
        const val BASE_BACKOFF_MS = 1000L // 1s, 2s, 4s
    }
}
