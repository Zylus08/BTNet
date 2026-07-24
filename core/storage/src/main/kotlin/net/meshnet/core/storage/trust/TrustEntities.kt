package net.meshnet.core.storage.trust

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Records an independent corroboration of a community report by a witness node.
 *
 * A corroboration is valid only if:
 *  - The witness [witnessId] differs from the report originator.
 *  - The witness [signature] verifies against the witness's public key.
 *  - The witness [observedAtMs] falls within the report's active window.
 *
 * The TrustEngine aggregates corroborations per report to compute confidence.
 */
@Entity(
    tableName = "corroborations",
    indices = [
        Index(value = ["report_id"]),
        Index(value = ["witness_id"]),
        Index(value = ["report_id", "witness_id"], unique = true),
    ]
)
data class CorroborationEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "report_id")
    val reportId: String,

    @ColumnInfo(name = "witness_id")
    val witnessId: String,               // hex Ed25519 public key of witness

    @ColumnInfo(name = "witness_latitude")
    val witnessLatitude: Double,

    @ColumnInfo(name = "witness_longitude")
    val witnessLongitude: Double,

    @ColumnInfo(name = "observed_at_ms")
    val observedAtMs: Long,

    @ColumnInfo(name = "signature")
    val signature: ByteArray,            // Ed25519 signature from witness

    @ColumnInfo(name = "received_at_ms")
    val receivedAtMs: Long = System.currentTimeMillis(),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CorroborationEntity) return false
        return reportId == other.reportId && witnessId == other.witnessId
    }

    override fun hashCode(): Int = 31 * reportId.hashCode() + witnessId.hashCode()
}

/**
 * Tracks historical report accuracy for a given originator (local only, not shared).
 * Used by TrustEngine to weight reporter history in confidence scoring.
 */
@Entity(
    tableName = "reporter_history",
    indices = [Index(value = ["originator_id"], unique = true)]
)
data class ReporterHistoryEntity(
    @PrimaryKey
    @ColumnInfo(name = "originator_id")
    val originatorId: String,

    @ColumnInfo(name = "reports_created")
    val reportsCreated: Int = 0,

    @ColumnInfo(name = "reports_corroborated")
    val reportsCorroborated: Int = 0,   // confirmed by ≥ 3 independent witnesses

    @ColumnInfo(name = "reports_flagged_stale")
    val reportsFlaggedStale: Int = 0,

    @ColumnInfo(name = "last_updated_ms")
    val lastUpdatedMs: Long = System.currentTimeMillis(),
)
