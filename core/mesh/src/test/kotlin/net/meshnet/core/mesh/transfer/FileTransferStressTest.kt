package net.meshnet.core.mesh.transfer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class FileTransferStressTest {

    private lateinit var congestionController: CongestionController

    @BeforeEach
    fun setup() {
        congestionController = CongestionController()
    }

    @Test
    fun `Congestion window increases on successful ACKs`() {
        val fileId = "test_file_1"
        
        // Initial window should be 10
        assertEquals(10, congestionController.getWindowSize(fileId))

        // Simulate 10 ACKs (should increase window to 11)
        for (i in 0 until 10) {
            congestionController.onAckReceived(fileId)
        }
        
        assertEquals(11, congestionController.getWindowSize(fileId))

        // Simulate 11 more ACKs (should increase window to 12)
        for (i in 0 until 11) {
            congestionController.onAckReceived(fileId)
        }

        assertEquals(12, congestionController.getWindowSize(fileId))
    }

    @Test
    fun `Congestion window halves on timeout and prevents starvation`() {
        val fileId = "test_file_2"
        
        // Push window up to 20
        for (i in 0 until 150) {
            congestionController.onAckReceived(fileId)
        }
        
        val currentCwnd = congestionController.getWindowSize(fileId)
        
        // Simulate packet loss
        congestionController.onTimeout(fileId)
        
        assertEquals(currentCwnd / 2, congestionController.getWindowSize(fileId))
        
        // Multiple timeouts should not drop below MIN_CWND (2)
        for (i in 0 until 10) {
            congestionController.onTimeout(fileId)
        }
        
        assertEquals(2, congestionController.getWindowSize(fileId))
    }
}
