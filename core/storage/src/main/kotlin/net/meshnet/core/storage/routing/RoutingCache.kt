package net.meshnet.core.storage.routing

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Database
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

// ── Entity ────────────────────────────────────────────────────────────────────

/**
 * Persisted PRoPHET delivery probability cache.
 *
 * [localId] is always this device's hex public key.
 * [destinationId] is the target peer's hex public key.
 * [probability] is the current P(local, destination) value in [0.0, 1.0].
 *
 * Indexed for fast range queries needed by anti-entropy sync.
 */
@Entity(
    tableName = "routing_cache",
    indices = [
        Index(value = ["destination_id"]),
        Index(value = ["probability"]),
        Index(value = ["last_updated_ms"]),
    ]
)
data class RoutingCacheEntity(
    @PrimaryKey
    @ColumnInfo(name = "destination_id")
    val destinationId: String,           // hex Ed25519 public key

    @ColumnInfo(name = "probability")
    val probability: Float,              // PRoPHET P(local, destination)

    @ColumnInfo(name = "last_encounter_ms")
    val lastEncounterMs: Long,

    @ColumnInfo(name = "last_updated_ms")
    val lastUpdatedMs: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "encounter_count")
    val encounterCount: Int = 0,
)

// ── DAO ───────────────────────────────────────────────────────────────────────

@Dao
interface RoutingCacheDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: RoutingCacheEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<RoutingCacheEntity>)

    @Query("SELECT * FROM routing_cache WHERE destination_id = :destinationId")
    suspend fun findByDestination(destinationId: String): RoutingCacheEntity?

    @Query("SELECT * FROM routing_cache ORDER BY probability DESC")
    suspend fun allEntries(): List<RoutingCacheEntity>

    @Query("SELECT * FROM routing_cache WHERE probability > :minProbability ORDER BY probability DESC")
    fun observeAboveThreshold(minProbability: Float): Flow<List<RoutingCacheEntity>>

    @Query("""
        UPDATE routing_cache 
        SET probability = :probability, 
            last_encounter_ms = :lastEncounterMs,
            last_updated_ms = :nowMs,
            encounter_count = encounter_count + 1 
        WHERE destination_id = :destinationId
    """)
    suspend fun updateProbability(
        destinationId: String,
        probability: Float,
        lastEncounterMs: Long,
        nowMs: Long,
    )

    /** Prune entries with very low probability to keep table lean. */
    @Query("DELETE FROM routing_cache WHERE probability < :minProbability")
    suspend fun pruneBelow(minProbability: Float): Int

    @Query("SELECT COUNT(*) FROM routing_cache")
    suspend fun count(): Int
}

// ── Database ──────────────────────────────────────────────────────────────────

@Database(entities = [RoutingCacheEntity::class], version = 1, exportSchema = true)
abstract class RoutingCacheDatabase : RoomDatabase() {
    abstract fun routingCacheDao(): RoutingCacheDao
    companion object { const val NAME = "meshnet_routing_cache.db" }
}
