package net.meshnet.core.mesh

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import net.meshnet.core.events.EventBus
import net.meshnet.core.events.MeshEvent
import net.meshnet.core.mesh.transport.TransportManager
import net.meshnet.core.mesh.transport.TransportUpgradeManager
import net.meshnet.core.mesh.transport.wifi.WifiDirectGroupOwnerNegotiator
import timber.log.Timber
import javax.inject.Inject

/**
 * The core Mesh Node Runtime.
 * Runs as a Foreground Service to ensure the OS does not kill the mesh network
 * when the app is backgrounded. Holds partial wake locks only when actively
 * transmitting large files.
 */
@AndroidEntryPoint
class MeshNodeService : Service() {

    @Inject lateinit var transportManager: TransportManager
    @Inject lateinit var transportUpgradeManager: TransportUpgradeManager
    @Inject lateinit var eventBus: EventBus
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        Timber.i("MeshNodeService created")
        
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(0))

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MeshNet::TransferWakeLock")

        // Start core systems
        scope.launch {
            transportManager.startAll()
            transportUpgradeManager.start()
        }

        // Listen for peer count updates to update the notification
        scope.launch {
            transportManager.connectedPeers.collect { peers ->
                updateNotification(peers.size)
            }
        }
        
        // Listen for file transfers to grab wake locks
        scope.launch {
            eventBus.on<MeshEvent.FileTransferInitiated>().collect {
                acquireWakeLock()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // STICKY ensures the service is recreated if the system kills it for memory
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.i("MeshNodeService destroyed")
        scope.launch {
            transportManager.stopAll()
        }
        scope.cancel()
        releaseWakeLock()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(peerCount: Int): Notification {
        val title = "MeshNet is running"
        val text = "Mesh active - $peerCount peers nearby"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Stub icon
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(peerCount: Int) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification(peerCount))
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Mesh Network Runtime",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps the mesh network active in the background"
        }
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == false) {
            // Timeout of 10 minutes max for transfer wake lock
            wakeLock?.acquire(10 * 60 * 1000L)
            Timber.d("Acquired partial wake lock for transfer")
        }
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
            Timber.d("Released partial wake lock")
        }
    }

    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "mesh_runtime_channel"
    }
}
