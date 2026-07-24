package net.meshnet.core.routing

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import net.meshnet.core.crypto.KeyManager
import net.meshnet.core.events.EventBus
import net.meshnet.core.events.MeshEvent
import net.meshnet.core.mesh.model.Peer
import net.meshnet.core.mesh.transport.TransportManager
import net.meshnet.core.metrics.MetricsCollector
import net.meshnet.core.protocol.MeshPacket
import net.meshnet.core.storage.bloom.SeenPacketBloomFilter
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The core multi-hop forwarding engine.
 * 
 * 1. Listens for incoming packets on the EventBus.
 * 2. Deduplicates via Bloom Filter.
 * 3. Checks if we are the destination (if so, consume).
 * 4. Checks TTL/Hop budget.
 * 5. Asks the active [RoutingStrategy] for next hops.
 * 6. Forwards via [TransportManager].
 */
@Singleton
class RelayEngine @Inject constructor(
    private val eventBus: EventBus,
    private val transportManager: TransportManager,
    private val routingStrategy: RoutingStrategy,
    private val bloomFilter: SeenPacketBloomFilter,
    private val keyManager: KeyManager,
    private val metricsCollector: MetricsCollector,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Keep track of currently connected peers across all transports
    private var activePeers: List<Peer> = emptyList()

    fun start() {
        // Track active peers for routing decisions
        scope.launch {
            transportManager.connectedPeers().collect { peers ->
                activePeers = peers
            }
        }

        // Process incoming packets
        scope.launch {
            eventBus.on<MeshEvent.PacketReceived>().collect { event ->
                processPacket(event.packet, event.from, event.transportId)
            }
        }
    }

    private suspend fun processPacket(packet: MeshPacket, fromPeer: Peer, transportId: String) {
        val packetId = packet.packetId.toByteArray()
        val packetIdHex = packetId.toHex()

        // 1. Deduplication (Bloom Filter)
        if (bloomFilter.mightContain(packetIdHex)) {
            Timber.d("Packet ${packetIdHex.take(8)} dropped: Bloom filter hit")
            metricsCollector.recordPacketDropped(net.meshnet.core.metrics.DropReason.BLOOM_FILTER, transportId)
            eventBus.emit(MeshEvent.PacketDropped(packetId, "Bloom filter hit", transportId))
            return
        }
        
        bloomFilter.add(packetIdHex)

        // 2. Are we the destination?
        val myId = keyManager.identityPublicKey
        val recipientId = packet.recipientId.toByteArray()
        val isBroadcast = recipientId.isEmpty() || recipientId.all { it == 0.toByte() }
        val isForMe = recipientId.contentEquals(myId)

        if (isForMe) {
            Timber.i("Packet ${packetIdHex.take(8)} reached final destination!")
            // Do not forward further unless it's a broadcast
            if (!isBroadcast) return
        }

        // 3. TTL Check
        if (packet.ttl <= 1) {
            Timber.d("Packet ${packetIdHex.take(8)} dropped: TTL expired")
            metricsCollector.recordPacketDropped(net.meshnet.core.metrics.DropReason.TTL_EXPIRED, transportId)
            eventBus.emit(MeshEvent.PacketDropped(packetId, "TTL expired", transportId))
            return
        }

        // 4. Determine next hops via Routing Strategy
        // We exclude the peer we just received it from to avoid immediate loops
        val eligiblePeers = activePeers.filter { !it.id.contentEquals(fromPeer.id) }
        
        if (eligiblePeers.isEmpty()) {
            Timber.d("Packet ${packetIdHex.take(8)}: No eligible peers to forward to")
            metricsCollector.recordPacketDropped(net.meshnet.core.metrics.DropReason.NO_ROUTE, transportId)
            eventBus.emit(MeshEvent.PacketDropped(packetId, "No route", transportId))
            return
        }

        val nextHops = routingStrategy.nextHops(packet, eligiblePeers, myId)

        if (nextHops.isEmpty()) {
            Timber.d("Packet ${packetIdHex.take(8)}: Strategy chose 0 next hops")
            return
        }

        // 5. Forward to selected peers
        // In reality, we must clone the packet, decrement TTL, increment hop_count.
        // For this stub, we just pass the packet as-is to simulate forwarding.
        val forwardedPacket = packet.toBuilder()
            .setTtl(packet.ttl - 1)
            .setHopCount(packet.hopCount + 1)
            .build()

        var successCount = 0
        for (peer in nextHops) {
            val result = transportManager.send(forwardedPacket, peer)
            if (result.isSuccess) {
                successCount++
                eventBus.emit(MeshEvent.PacketSent(forwardedPacket, peer, transportId))
            }
        }

        if (successCount > 0) {
            Timber.i("Forwarded packet ${packetIdHex.take(8)} to $successCount peers")
            metricsCollector.recordPacketForwarded(packet.hopCount + 1, "Epidemic")
            eventBus.emit(MeshEvent.PacketForwarded(forwardedPacket, nextHops, "Epidemic"))
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
