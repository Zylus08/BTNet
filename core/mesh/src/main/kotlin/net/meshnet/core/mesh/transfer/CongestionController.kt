package net.meshnet.core.mesh.transfer

import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the congestion window (cwnd) for active file transfers.
 * 
 * Uses a simplified TCP Reno-style Additive Increase Multiplicative Decrease (AIMD) algorithm:
 * - Increases cwnd by 1 on successful ACK (Additive Increase)
 * - Halves cwnd on timeout (Multiplicative Decrease)
 */
@Singleton
class CongestionController @Inject constructor() {

    private val windows = mutableMapOf<String, WindowState>()

    fun getWindowSize(fileId: String): Int {
        return windows[fileId]?.cwnd ?: INITIAL_CWND
    }

    fun onAckReceived(fileId: String) {
        val state = windows.getOrPut(fileId) { WindowState(INITIAL_CWND, 0) }
        
        // Additive Increase: increment cwnd slightly for each ACK, up to a max limit
        if (state.cwnd < MAX_CWND) {
            state.ackCount++
            if (state.ackCount >= state.cwnd) {
                state.cwnd++
                state.ackCount = 0
                Timber.d("CongestionController: Increased cwnd for $fileId to ${state.cwnd}")
            }
        }
    }

    fun onTimeout(fileId: String) {
        val state = windows.getOrPut(fileId) { WindowState(INITIAL_CWND, 0) }
        
        // Multiplicative Decrease: cut window in half on packet loss (timeout)
        val newCwnd = maxOf(MIN_CWND, state.cwnd / 2)
        if (newCwnd != state.cwnd) {
            state.cwnd = newCwnd
            state.ackCount = 0
            Timber.w("CongestionController: Timeout for $fileId, reduced cwnd to ${state.cwnd}")
        }
    }

    fun transferComplete(fileId: String) {
        windows.remove(fileId)
    }

    private data class WindowState(var cwnd: Int, var ackCount: Int)

    companion object {
        const val INITIAL_CWND = 10
        const val MIN_CWND = 2
        const val MAX_CWND = 100
    }
}
