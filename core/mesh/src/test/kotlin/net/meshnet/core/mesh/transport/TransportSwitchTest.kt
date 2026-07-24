package net.meshnet.core.mesh.transport

import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import net.meshnet.core.events.EventBus
import net.meshnet.core.events.MeshEvent
import net.meshnet.core.mesh.model.Peer
import net.meshnet.core.protocol.Capabilities
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TransportSwitchTest {

    private lateinit var eventBus: EventBus
    private lateinit var capabilityNegotiator: CapabilityNegotiator
    private lateinit var upgradeManager: TransportUpgradeManager

    @BeforeEach
    fun setup() {
        eventBus = mockk(relaxed = true)
        capabilityNegotiator = mockk(relaxed = true)
        // TransportManager mock isn't strictly needed for the isolated unit logic of UpgradeManager
        upgradeManager = TransportUpgradeManager(eventBus, mockk(relaxed = true), capabilityNegotiator)
    }

    @Test
    fun `Large file transfer intent triggers Wi-Fi Direct upgrade if supported`() = runTest {
        val peer = Peer(ByteArray(32) { 1 }, capabilities = Capabilities(wifiDirect = true))
        
        io.mockk.every { capabilityNegotiator.supportsWifiDirect(peer) } returns true

        upgradeManager.start()
        
        // Simulating private method call via reflection or modifying access for testing
        // Since it's private, we'll verify the event bus emission in integration instead
        val method = TransportUpgradeManager::class.java.getDeclaredMethod("evaluateUpgrade", Peer::class.java)
        method.isAccessible = true
        method.invoke(upgradeManager, peer)

        verify { 
            eventBus.emit(MeshEvent.TransportUpgradeRequested(peer, "wifidirect")) 
        }
    }
}
