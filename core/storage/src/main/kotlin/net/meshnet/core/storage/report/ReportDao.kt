package net.meshnet.core.storage.report

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReportDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(report: ReportEntity): Long

    @Query("SELECT * FROM reports WHERE report_id = :reportId")
    suspend fun findById(reportId: String): ReportEntity?

    /** All non-expired, non-stale reports ordered by confidence score descending. */
    @Query("""
        SELECT * FROM reports 
        WHERE expires_at_ms > :nowMs AND flagged_stale = 0
        ORDER BY confidence_score DESC, created_at_ms DESC
    """)
    fun observeActive(nowMs: Long): Flow<List<ReportEntity>>

    /** Reports within bounding box for map display. */
    @Query("""
        SELECT * FROM reports 
        WHERE expires_at_ms > :nowMs 
        AND flagged_stale = 0
        AND latitude BETWEEN :minLat AND :maxLat 
        AND longitude BETWEEN :minLon AND :maxLon
        ORDER BY confidence_score DESC
    """)
    fun observeInBounds(
        nowMs: Long,
        minLat: Double, maxLat: Double,
        minLon: Double, maxLon: Double,
    ): Flow<List<ReportEntity>>

    @Query("""
        UPDATE reports 
        SET confidence_score = :score, witness_count = :witnessCount 
        WHERE report_id = :reportId
    """)
    suspend fun updateConfidence(reportId: String, score: Float, witnessCount: Int)

    @Query("UPDATE reports SET flagged_stale = 1 WHERE report_id = :reportId")
    suspend fun flagStale(reportId: String)

    /** Hard-delete expired and stale reports older than threshold. */
    @Query("""
        DELETE FROM reports 
        WHERE expires_at_ms < :nowMs OR (flagged_stale = 1 AND created_at_ms < :olderThanMs)
    """)
    suspend fun deleteExpired(nowMs: Long, olderThanMs: Long): Int

    @Query("SELECT COUNT(*) FROM reports WHERE expires_at_ms > :nowMs AND flagged_stale = 0")
    fun observeActiveCount(nowMs: Long): Flow<Int>
}
