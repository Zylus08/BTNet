package net.meshnet.core.mesh.transport

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.meshnet.core.events.EventBus
import net.meshnet.core.events.MeshEvent
import net.meshnet.core.mesh.model.Peer
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Monitors active BLE connections and decides when to upgrade a link to Wi-Fi Direct.
 *
 * Upgrade logic:
 * - Triggered when a large file transfer is initiated.
 * - Checks if the peer has advertised `wifiDirect` capabilities.
 * - Coordinates the GO (Group Owner) negotiation process by sending a capability probe.
 */
@Singleton
class TransportUpgradeManager @Inject constructor(
    private val eventBus: EventBus,
    private val transportManager: TransportManager,
    private val capabilityNegotiator: CapabilityNegotiator,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun start() {
        scope.launch {
            // Listen for intent to transfer large files
            eventBus.on<MeshEvent.FileTransferInitiated>().collect { event ->
                evaluateUpgrade(event.targetPeer)
            }
        }
    }

    private suspend fun evaluateUpgrade(peer: Peer) {
        if (!capabilityNegotiator.supportsWifiDirect(peer)) {
            Timber.i("Cannot upgrade link: Peer ${peer.id.toHex()} does not support Wi-Fi Direct")
            return
        }

        Timber.i("Initiating Wi-Fi Direct upgrade with Peer ${peer.id.toHex()}")
        
        // In reality, this would issue a system intent or call WifiP2pManager to 
        // invite the peer to a P2P group.
        // For standard dialogs (User selected), it will prompt the user to accept the connection.
        
        // We emit an event to signal the WifiDirectTransport to begin discovery/invitation
        eventBus.emit(MeshEvent.TransportUpgradeRequested(peer, "wifidirect"))
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
