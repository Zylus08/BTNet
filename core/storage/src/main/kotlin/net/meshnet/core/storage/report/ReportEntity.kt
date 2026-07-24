package net.meshnet.core.storage.report

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "reports",
    indices = [
        Index(value = ["category"]),
        Index(value = ["expires_at_ms"]),
        Index(value = ["latitude", "longitude"]),
        Index(value = ["originator_id"]),
    ]
)
data class ReportEntity(
    @PrimaryKey
    @ColumnInfo(name = "report_id")
    val reportId: String,

    @ColumnInfo(name = "originator_id")
    val originatorId: String,            // hex Ed25519 public key

    @ColumnInfo(name = "category")
    val category: String,                // ReportCategory name

    @ColumnInfo(name = "latitude")
    val latitude: Double,

    @ColumnInfo(name = "longitude")
    val longitude: Double,

    @ColumnInfo(name = "description")
    val description: String,

    @ColumnInfo(name = "created_at_ms")
    val createdAtMs: Long,

    @ColumnInfo(name = "expires_at_ms")
    val expiresAtMs: Long,               // max created_at + 86_400_000

    @ColumnInfo(name = "confidence_score")
    val confidenceScore: Float = 0f,     // [0.0, 1.0]; updated by TrustEngine

    @ColumnInfo(name = "witness_count")
    val witnessCount: Int = 0,

    @ColumnInfo(name = "signature")
    val signature: ByteArray,            // Ed25519 signature from originator

    @ColumnInfo(name = "hop_origin_count")
    val hopOriginCount: Int = 0,

    @ColumnInfo(name = "flagged_stale")
    val flaggedStale: Boolean = false,

    @ColumnInfo(name = "raw_proto")
    val rawProto: ByteArray,             // serialised Report protobuf
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ReportEntity) return false
        return reportId == other.reportId
    }

    override fun hashCode(): Int = reportId.hashCode()
}
