package net.meshnet.core.routing

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import net.meshnet.core.storage.packet.PacketDao
import timber.log.Timber

/**
 * Android WorkManager Worker that periodically wakes up in the background to:
 * 1. Clean up expired packets from the DTN store.
 * 2. Attempt to flush pending packets if any transports are active.
 */
class DtnWorker(
    appContext: Context,
    params: WorkerParameters,
    // Note: HiltWorker requires @HiltWorker and @AssistedInject in a real app
    // We omit it here for simplicity of the stub
) : CoroutineWorker(appContext, params) {

    // These would be injected via HiltWorker in production
    private lateinit var packetDao: PacketDao
    private lateinit var dtnManager: DtnManager

    override suspend fun doWork(): Result {
        Timber.i("DTN Worker: Starting periodic background maintenance")

        return try {
            val nowMs = System.currentTimeMillis()
            
            // 1. Cleanup expired
            // val deletedCount = packetDao.deleteExpiredPackets(nowMs)
            // Timber.d("DTN Worker: Deleted $deletedCount expired packets")

            // 2. The flush logic is event-driven when peers connect, but we could 
            // force a transport scan here if allowed by Android background limits.
            
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "DTN Worker failed")
            Result.retry()
        }
    }
}
