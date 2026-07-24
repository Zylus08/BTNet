package net.meshnet.core.mesh.transfer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.meshnet.core.mesh.model.Peer
import net.meshnet.core.mesh.transport.TransportManager
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles sending file chunks, listening for ACKs, and retransmitting missing chunks.
 * Also manages a simple congestion window.
 */
@Singleton
class RetransmissionEngine @Inject constructor(
    private val transportManager: TransportManager,
    private val fileTransferDao: FileTransferDao,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // In-flight tracking: fileId -> (chunkIndex -> timestampSent)
    private val inFlight = mutableMapOf<String, MutableMap<Int, Long>>()

    fun startSender(file: File, state: TransferState, peer: Peer) {
        scope.launch {
            inFlight[state.fileId] = mutableMapOf()
            
            var cwnd = INITIAL_CWND
            var windowStart = 0

            while (state.ackedChunks.size < state.totalChunks) {
                // 1. Process ACKs (in a real system, an event listener updates state.ackedChunks)
                
                // 2. Identify missing chunks within the current window
                val toSend = (0 until state.totalChunks)
                    .filter { !state.ackedChunks.contains(it) }
                    .take(cwnd)

                for (chunkIndex in toSend) {
                    val lastSent = inFlight[state.fileId]?.get(chunkIndex) ?: 0L
                    val now = System.currentTimeMillis()
                    
                    if (now - lastSent > RTO_MS) {
                        Timber.d("Sending chunk $chunkIndex of ${state.fileId} to ${peer.id.toHex().take(8)}")
                        // In reality, read chunk from file and encrypt it here
                        val dummyChunk = ByteArray(FileTransferProtocol.CHUNK_SIZE)
                        
                        // Wrap in a MeshPacket (type FILE_CHUNK)
                        // val packet = ...
                        
                        // transportManager.send(packet, peer)
                        
                        inFlight[state.fileId]?.put(chunkIndex, now)
                    }
                }
                
                // 3. Simple Congestion Control
                // If ACKs are received, increase cwnd. If timeouts occur, decrease cwnd.
                // (Omitted for stub brevity, handled in CongestionController in Phase 4.4)

                delay(10) // Yield to allow ACKs to be processed
            }
            
            Timber.i("Transfer ${state.fileId} complete.")
            inFlight.remove(state.fileId)
        }
    }

    fun sendAck(fileId: String, chunkIndex: Int, peer: Peer) {
        scope.launch {
            // Construct ACK packet and send via TransportManager
            // In reality: 
            // val ackPacket = MeshPacket... Type=ACK ...
            // transportManager.send(ackPacket, peer)
            Timber.d("Sent ACK for chunk $chunkIndex of $fileId")
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    companion object {
        const val INITIAL_CWND = 10 // Start by sending 10 chunks at a time
        const val RTO_MS = 1000L // Retransmission Timeout: 1 second
    }
}
