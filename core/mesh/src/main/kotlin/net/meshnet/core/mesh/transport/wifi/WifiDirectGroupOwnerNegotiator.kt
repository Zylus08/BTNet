package net.meshnet.core.mesh.transport.wifi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import net.meshnet.core.events.EventBus
import net.meshnet.core.events.MeshEvent
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages Wi-Fi Direct Group Owner (GO) negotiation and connections.
 * 
 * Uses standard Android [WifiP2pManager] APIs, which will trigger system dialogs
 * prompting the user to accept connections. This provides the best security and 
 * reliability across OEMs compared to reflection hacks.
 */
@Singleton
class WifiDirectGroupOwnerNegotiator @Inject constructor(
    private val context: Context,
    private val eventBus: EventBus,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private val manager: WifiP2pManager? by lazy {
        context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    }
    
    private val channel: WifiP2pManager.Channel? by lazy {
        manager?.initialize(context, context.mainLooper, null)
    }

    private val _connectionEvents = MutableSharedFlow<ConnectionEvent>()
    val connectionEvents = _connectionEvents.asSharedFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    val networkInfo = intent.getParcelableExtra<android.net.NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)
                    if (networkInfo?.isConnected == true) {
                        manager?.requestConnectionInfo(channel) { info ->
                            if (info.groupFormed) {
                                scope.launch {
                                    _connectionEvents.emit(
                                        ConnectionEvent.Connected(
                                            isGroupOwner = info.isGroupOwner,
                                            groupOwnerAddress = info.groupOwnerAddress
                                        )
                                    )
                                }
                            }
                        }
                    } else {
                        scope.launch { _connectionEvents.emit(ConnectionEvent.Disconnected) }
                    }
                }
            }
        }
    }

    fun start() {
        val intentFilter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }
        context.registerReceiver(receiver, intentFilter)
        
        // Listen for transport upgrade requests to initiate connections
        scope.launch {
            eventBus.on<MeshEvent.TransportUpgradeRequested>().collect { event ->
                if (event.targetTransportId == "wifidirect") {
                    // Requires mapping Peer ID to MAC address. In a full implementation,
                    // we'd have discovered the MAC via WifiP2pManager.discoverPeers() 
                    // and matched it using a broadcast payload or device name.
                    val macAddress = "00:00:00:00:00:00" // Stub MAC
                    connectToPeer(macAddress)
                }
            }
        }
    }

    fun stop() {
        try {
            context.unregisterReceiver(receiver)
        } catch (e: Exception) {
            // Already unregistered
        }
    }

    private fun connectToPeer(deviceAddress: String) {
        val config = WifiP2pConfig().apply {
            this.deviceAddress = deviceAddress
            // Let the system negotiate GO intent (0-15). 
            // We could use groupOwnerIntent = 15 if we know we should be the host
            // e.g. based on battery level or peer ID comparison to break ties.
        }

        manager?.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Timber.i("Wi-Fi Direct connection initiated (Dialog shown to user)")
            }

            override fun onFailure(reason: Int) {
                Timber.e("Wi-Fi Direct connection failed: reason=$reason")
                scope.launch { _connectionEvents.emit(ConnectionEvent.Failed(reason)) }
            }
        })
    }
}

sealed interface ConnectionEvent {
    data class Connected(val isGroupOwner: Boolean, val groupOwnerAddress: java.net.InetAddress?) : ConnectionEvent
    data object Disconnected : ConnectionEvent
    data class Failed(val reason: Int) : ConnectionEvent
}
