package net.meshnet.core.storage.trust

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TrustDao {

    // ── Corroborations ────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCorroboration(c: CorroborationEntity): Long

    @Query("SELECT * FROM corroborations WHERE report_id = :reportId")
    suspend fun corroborationsForReport(reportId: String): List<CorroborationEntity>

    @Query("SELECT COUNT(*) FROM corroborations WHERE report_id = :reportId")
    fun observeWitnessCount(reportId: String): Flow<Int>

    @Query("""
        SELECT COUNT(DISTINCT witness_id) FROM corroborations 
        WHERE report_id = :reportId
    """)
    suspend fun uniqueWitnessCount(reportId: String): Int

    @Query("DELETE FROM corroborations WHERE report_id = :reportId")
    suspend fun deleteForReport(reportId: String)

    // ── Reporter history ──────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertReporterHistory(h: ReporterHistoryEntity)

    @Query("SELECT * FROM reporter_history WHERE originator_id = :originatorId")
    suspend fun reporterHistory(originatorId: String): ReporterHistoryEntity?

    @Query("""
        UPDATE reporter_history 
        SET reports_created = reports_created + 1, last_updated_ms = :nowMs 
        WHERE originator_id = :originatorId
    """)
    suspend fun incrementCreated(originatorId: String, nowMs: Long)

    @Query("""
        UPDATE reporter_history 
        SET reports_corroborated = reports_corroborated + 1, last_updated_ms = :nowMs 
        WHERE originator_id = :originatorId
    """)
    suspend fun incrementCorroborated(originatorId: String, nowMs: Long)

    @Query("""
        UPDATE reporter_history 
        SET reports_flagged_stale = reports_flagged_stale + 1, last_updated_ms = :nowMs 
        WHERE originator_id = :originatorId
    """)
    suspend fun incrementFlaggedStale(originatorId: String, nowMs: Long)
}
