package net.meshnet.core.storage.peer

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PeerDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(peer: PeerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(peers: List<PeerEntity>)

    @Update
    suspend fun update(peer: PeerEntity)

    @Query("SELECT * FROM peers WHERE id = :id")
    suspend fun findById(id: String): PeerEntity?

    @Query("SELECT * FROM peers WHERE advertised_id = :advertisedId LIMIT 1")
    suspend fun findByAdvertisedId(advertisedId: String): PeerEntity?

    @Query("SELECT * FROM peers ORDER BY last_seen_ms DESC")
    fun observeAll(): Flow<List<PeerEntity>>

    @Query("SELECT * FROM peers WHERE last_seen_ms > :sinceMs ORDER BY last_seen_ms DESC")
    fun observeRecent(sinceMs: Long): Flow<List<PeerEntity>>

    @Query("SELECT * FROM peers WHERE trust_level = :trustLevel")
    suspend fun findByTrustLevel(trustLevel: String): List<PeerEntity>

    @Query("UPDATE peers SET last_seen_ms = :lastSeenMs, rssi = :rssi WHERE id = :id")
    suspend fun updateLastSeen(id: String, lastSeenMs: Long, rssi: Int)

    @Query("UPDATE peers SET trust_level = :trustLevel WHERE id = :id")
    suspend fun updateTrustLevel(id: String, trustLevel: String)

    @Query("UPDATE peers SET nickname = :nickname WHERE id = :id")
    suspend fun updateNickname(id: String, nickname: String?)

    @Query("DELETE FROM peers WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM peers WHERE last_seen_ms < :olderThanMs")
    suspend fun deleteStale(olderThanMs: Long)

    @Query("SELECT COUNT(*) FROM peers")
    suspend fun count(): Int
}
