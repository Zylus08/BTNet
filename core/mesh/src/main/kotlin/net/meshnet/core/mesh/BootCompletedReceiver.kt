package net.meshnet.core.mesh

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import timber.log.Timber

/**
 * Restarts the MeshNodeService when the device finishes booting.
 * This ensures the device continues to act as a mesh relay even if the user
 * forgets to launch the app after a reboot.
 */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || 
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            
            Timber.i("Boot completed. Starting MeshNodeService...")
            
            val serviceIntent = Intent(context, MeshNodeService::class.java)
            // Must use startForegroundService since Android 8.0 (API 26)
            context.startForegroundService(serviceIntent)
        }
    }
}
