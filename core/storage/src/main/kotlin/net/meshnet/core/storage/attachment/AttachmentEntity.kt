package net.meshnet.core.storage.attachment

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Tracks file transfer state for chunked transfers.
 *
 * Each row represents one file transfer (identified by [transferId]).
 * Individual chunk receipt is tracked in [receivedChunkIndices] (JSON int array).
 * On completion, [fileHash] is verified before the file is committed to storage.
 */
@Entity(
    tableName = "attachments",
    indices = [
        Index(value = ["status"]),
        Index(value = ["associated_packet_id"]),
        Index(value = ["created_at_ms"]),
    ]
)
data class AttachmentEntity(
    @PrimaryKey
    @ColumnInfo(name = "transfer_id")
    val transferId: String,              // hex 16-byte UUID from FileChunk.transfer_id

    @ColumnInfo(name = "associated_packet_id")
    val associatedPacketId: String,      // the MESSAGE packet this attachment belongs to

    @ColumnInfo(name = "file_name")
    val fileName: String,

    @ColumnInfo(name = "mime_type")
    val mimeType: String,

    @ColumnInfo(name = "total_bytes")
    val totalBytes: Long,

    @ColumnInfo(name = "total_chunks")
    val totalChunks: Int,

    @ColumnInfo(name = "received_chunk_indices")
    val receivedChunkIndices: String,    // JSON array of received chunk indices, e.g. "[0,1,3]"

    @ColumnInfo(name = "file_hash")
    val fileHash: ByteArray,             // SHA-256 of complete file

    @ColumnInfo(name = "local_path")
    val localPath: String?,              // path after reassembly; null until complete

    @ColumnInfo(name = "status")
    val status: String,                  // AttachmentStatus enum name

    @ColumnInfo(name = "created_at_ms")
    val createdAtMs: Long,

    @ColumnInfo(name = "completed_at_ms")
    val completedAtMs: Long? = null,

    @ColumnInfo(name = "direction")
    val direction: String,               // "INBOUND" | "OUTBOUND"
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AttachmentEntity) return false
        return transferId == other.transferId
    }

    override fun hashCode(): Int = transferId.hashCode()
}

enum class AttachmentStatus {
    IN_PROGRESS,
    VERIFYING,
    COMPLETE,
    FAILED,
    CANCELLED,
}
