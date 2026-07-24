package net.meshnet.core.mesh.ble

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import net.meshnet.core.mesh.transport.TransportEvent
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import android.content.Context

class BLETransportTest {

    private val context: Context = mockk(relaxed = true)
    private val advertiser: BLEAdvertiser = mockk(relaxed = true)
    private val scanner: BLEScanner = mockk(relaxed = true)
    private val discoveryManager: PeerDiscoveryManager = mockk(relaxed = true)
    private val gattConnectionManager: GattConnectionManager = mockk(relaxed = true)
    private val gattClient: MeshGattClient = mockk(relaxed = true)
    private val gattServer: MeshGattServer = mockk(relaxed = true)

    private lateinit var transport: BLETransport

    @BeforeEach
    fun setup() {
        coEvery { gattConnectionManager.peerConnected } returns MutableSharedFlow()
        coEvery { gattConnectionManager.peerDisconnected } returns MutableSharedFlow()
        coEvery { gattClient.incomingPayloads } returns MutableSharedFlow()
        coEvery { gattClient.connectionStateChanges } returns MutableSharedFlow()
        coEvery { gattServer.incomingPayloads } returns MutableSharedFlow()

        transport = BLETransport(
            context, advertiser, scanner, discoveryManager,
            gattConnectionManager, gattClient, gattServer
        )
    }

    @Test
    fun `start() initializes all subcomponents`() = runTest {
        transport.start()

        coVerify(exactly = 1) { gattServer.start() }
        coVerify(exactly = 1) { discoveryManager.start() }
    }

    @Test
    fun `advertise() and scan() start components if transport is running`() = runTest {
        transport.start()
        transport.advertise()
        transport.scan()

        coVerify(exactly = 1) { advertiser.start() }
        coVerify(exactly = 1) { scanner.start() }
    }

    @Test
    fun `stop() cleans up all components`() = runTest {
        transport.start()
        transport.stop()

        coVerify(exactly = 1) { advertiser.stop() }
        coVerify(exactly = 1) { scanner.stop() }
        coVerify(exactly = 1) { gattClient.disconnectAll() }
        coVerify(exactly = 1) { gattServer.stop() }
    }
}
