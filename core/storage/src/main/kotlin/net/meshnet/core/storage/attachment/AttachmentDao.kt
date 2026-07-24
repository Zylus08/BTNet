package net.meshnet.core.storage.attachment

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AttachmentDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(attachment: AttachmentEntity): Long

    @Query("SELECT * FROM attachments WHERE transfer_id = :transferId")
    suspend fun findById(transferId: String): AttachmentEntity?

    @Query("SELECT * FROM attachments WHERE associated_packet_id = :packetId")
    suspend fun findByPacketId(packetId: String): List<AttachmentEntity>

    @Query("SELECT * FROM attachments WHERE status = 'IN_PROGRESS' ORDER BY created_at_ms ASC")
    fun observeInProgress(): Flow<List<AttachmentEntity>>

    @Query("""
        UPDATE attachments 
        SET received_chunk_indices = :indicesJson 
        WHERE transfer_id = :transferId
    """)
    suspend fun updateReceivedChunks(transferId: String, indicesJson: String)

    @Query("""
        UPDATE attachments 
        SET status = :status, local_path = :localPath, completed_at_ms = :completedAtMs 
        WHERE transfer_id = :transferId
    """)
    suspend fun updateStatus(
        transferId: String,
        status: String,
        localPath: String?,
        completedAtMs: Long?,
    )

    /** Returns chunk indices that have NOT been received yet. */
    @Query("SELECT received_chunk_indices FROM attachments WHERE transfer_id = :transferId")
    suspend fun getReceivedChunkIndicesJson(transferId: String): String?

    @Query("""
        DELETE FROM attachments 
        WHERE status IN ('COMPLETE', 'FAILED', 'CANCELLED') 
        AND completed_at_ms < :olderThanMs
    """)
    suspend fun deleteCompleted(olderThanMs: Long): Int
}
