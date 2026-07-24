package net.meshnet.core.mesh.transfer

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stub DAO for persisting file transfer state.
 * In production, this would be a Room DAO to allow resuming transfers
 * after an app crash or device reboot.
 */
@Singleton
class FileTransferDao @Inject constructor() {

    private val storage = ConcurrentHashMap<String, TransferState>()

    fun saveState(state: TransferState) {
        storage[state.fileId] = state
    }

    fun getState(fileId: String): TransferState? {
        return storage[fileId]
    }

    fun getAllIncomplete(): List<TransferState> {
        return storage.values.filter { it.status == TransferStatus.IN_PROGRESS }
    }
}

data class TransferState(
    val fileId: String,
    val peerId: String,
    val direction: TransferDirection,
    val totalChunks: Int,
    val ackedChunks: MutableSet<Int>, // In Room, store as JSON or separate table
    var status: TransferStatus,
    val fileHash: String
)

enum class TransferDirection { INCOMING, OUTGOING }
enum class TransferStatus { IN_PROGRESS, PAUSED, COMPLETE, FAILED }
