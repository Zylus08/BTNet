package net.meshnet.core.routing

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import net.meshnet.core.crypto.KeyManager
import net.meshnet.core.events.EventBus
import net.meshnet.core.events.MeshEvent
import net.meshnet.core.mesh.model.Peer
import net.meshnet.core.mesh.transport.TransportManager
import net.meshnet.core.metrics.MetricsCollector
import net.meshnet.core.protocol.MeshPacket
import net.meshnet.core.storage.bloom.SeenPacketBloomFilter
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DuplicateSuppressionTest {

    private val eventBus = mockk<EventBus>(relaxed = true)
    private val transportManager = mockk<TransportManager>(relaxed = true)
    private val routingStrategy = mockk<RoutingStrategy>(relaxed = true)
    private val bloomFilter = mockk<SeenPacketBloomFilter>(relaxed = true)
    private val keyManager = mockk<KeyManager>(relaxed = true)
    private val metricsCollector = mockk<MetricsCollector>(relaxed = true)

    private lateinit var relayEngine: RelayEngine
    private val packetReceivedFlow = MutableSharedFlow<MeshEvent>()

    @BeforeEach
    fun setup() {
        coEvery { transportManager.connectedPeers() } returns MutableSharedFlow()
        coEvery { eventBus.on<MeshEvent.PacketReceived>() } returns packetReceivedFlow as kotlinx.coroutines.flow.Flow<MeshEvent.PacketReceived>
        coEvery { keyManager.identityPublicKey } returns ByteArray(32) { 1 }

        relayEngine = RelayEngine(
            eventBus, transportManager, routingStrategy,
            bloomFilter, keyManager, metricsCollector
        )
    }

    @Test
    fun `Duplicate packets are dropped by BloomFilter and not forwarded`() = runTest {
        val packetId = ByteArray(16) { 9 }
        val packet = MeshPacket.newBuilder()
            .setPacketId(com.google.protobuf.ByteString.copyFrom(packetId))
            .setRecipientId(com.google.protobuf.ByteString.copyFrom(ByteArray(32) { 2 }))
            .setTtl(10)
            .build()

        val fromPeer = Peer(ByteArray(32) { 3 })
        
        // Setup bloom filter to claim it has seen it
        coEvery { bloomFilter.mightContain(any()) } returns true

        relayEngine.start()
        
        // Simulate receive
        packetReceivedFlow.emit(MeshEvent.PacketReceived(packet, fromPeer, "ble"))

        // Verify it was dropped and NOT forwarded
        coVerify(exactly = 1) { 
            metricsCollector.recordPacketDropped(net.meshnet.core.metrics.DropReason.BLOOM_FILTER, "ble")
        }
        coVerify(exactly = 0) { routingStrategy.nextHops(any(), any(), any()) }
        coVerify(exactly = 0) { transportManager.send(any(), any()) }
    }
}
