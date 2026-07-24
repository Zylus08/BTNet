package net.meshnet.core.routing

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.meshnet.core.events.EventBus
import net.meshnet.core.events.MeshEvent
import net.meshnet.core.mesh.model.Peer
import net.meshnet.core.mesh.transport.TransportManager
import net.meshnet.core.metrics.MetricsCollector
import net.meshnet.core.protocol.MeshPacket
import net.meshnet.core.storage.packet.PacketDao
import net.meshnet.core.storage.packet.PacketEntity
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Delay Tolerant Networking (DTN) Manager.
 * 
 * Implements store-and-forward routing. When a new peer connects, this manager
 * queries the PacketDatabase for pending packets that the new peer might be a good
 * next-hop for, and forwards them.
 */
@Singleton
class DtnManager @Inject constructor(
    private val eventBus: EventBus,
    private val packetDao: PacketDao,
    private val transportManager: TransportManager,
    private val routingStrategy: RoutingStrategy,
    private val metricsCollector: MetricsCollector,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun start() {
        // Listen for new peers connecting to trigger DTN flush
        scope.launch {
            eventBus.on<MeshEvent.PeerDiscovered>().collect { event ->
                flushPendingToPeer(event.peer)
            }
        }
        
        // Transport peer connections also trigger this
        scope.launch {
            transportManager.events().collect { event ->
                if (event is net.meshnet.core.mesh.transport.TransportEvent.PeerConnected) {
                    flushPendingToPeer(event.peer)
                }
            }
        }
    }

    /**
     * Checks all stored, unexpired packets to see if the newly connected [peer]
     * is a valid next-hop according to the active [RoutingStrategy].
     */
    private suspend fun flushPendingToPeer(peer: Peer) {
        Timber.d("DTN: Checking stored packets for new peer ${peer.id.toHex().take(8)}")
        
        // In a real app, we'd query packets that haven't been acked and aren't expired.
        // Assuming PacketDao has a method `getPendingPackets(nowMs)`
        val nowMs = System.currentTimeMillis()
        val pendingEntities = packetDao.getPendingPackets(nowMs)

        var forwardedCount = 0

        for (entity in pendingEntities) {
            val packet = parseEntity(entity) ?: continue
            
            // Re-evaluate routing strategy for this specific peer
            // e.g. PRoPHET will check if this peer has a high delivery predictability
            val eligible = routingStrategy.nextHops(packet, listOf(peer), ByteArray(32)) // myId omitted for stub
            
            if (eligible.isNotEmpty()) {
                val result = transportManager.send(packet, peer)
                if (result.isSuccess) {
                    forwardedCount++
                    metricsCollector.recordPacketForwarded(packet.hopCount + 1, "DTN")
                    eventBus.emit(MeshEvent.PacketSent(packet, peer, "dtn"))
                }
            }
        }

        if (forwardedCount > 0) {
            Timber.i("DTN: Flushed $forwardedCount packets to peer ${peer.id.toHex().take(8)}")
        }
    }

    private fun parseEntity(entity: PacketEntity): MeshPacket? {
        return try {
            MeshPacket.parseFrom(entity.rawBytes)
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse stored packet ${entity.packetId}")
            null
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
