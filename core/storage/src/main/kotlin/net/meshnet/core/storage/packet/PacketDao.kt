package net.meshnet.core.storage.packet

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PacketDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(packet: PacketEntity): Long

    @Query("SELECT * FROM packets WHERE packet_id = :packetId")
    suspend fun findById(packetId: String): PacketEntity?

    /** Outbound queue: pending packets ordered by priority (desc) then age (asc). */
    @Query("""
        SELECT * FROM packets 
        WHERE status = 'PENDING' AND expires_at_ms > :nowMs
        ORDER BY priority DESC, created_at_ms ASC
        LIMIT :limit
    """)
    suspend fun pendingQueue(nowMs: Long, limit: Int = 50): List<PacketEntity>

    @Query("""
        SELECT * FROM packets 
        WHERE recipient_id = :recipientId AND status IN ('PENDING', 'FORWARDED')
        ORDER BY priority DESC, created_at_ms ASC
    """)
    fun observeForRecipient(recipientId: String): Flow<List<PacketEntity>>

    @Query("UPDATE packets SET status = :status WHERE packet_id = :packetId")
    suspend fun updateStatus(packetId: String, status: String)

    @Query("""
        UPDATE packets 
        SET status = 'FORWARDED', 
            last_forward_attempt_ms = :nowMs, 
            forward_attempt_count = forward_attempt_count + 1 
        WHERE packet_id = :packetId
    """)
    suspend fun markForwarded(packetId: String, nowMs: Long)

    @Query("""
        UPDATE packets SET status = 'EXPIRED' 
        WHERE expires_at_ms < :nowMs AND status NOT IN ('DELIVERED', 'EXPIRED')
    """)
    suspend fun expireStale(nowMs: Long): Int

    @Query("""
        DELETE FROM packets 
        WHERE status IN ('DELIVERED', 'EXPIRED', 'FAILED') 
        AND created_at_ms < :olderThanMs
    """)
    suspend fun deleteCompleted(olderThanMs: Long): Int

    @Query("SELECT COUNT(*) FROM packets WHERE status = 'PENDING'")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM packets")
    suspend fun count(): Int
}
