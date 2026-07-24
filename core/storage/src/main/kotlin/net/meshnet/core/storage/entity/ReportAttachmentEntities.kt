package net.meshnet.core.storage.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reports")
data class ReportEntity(
    @PrimaryKey val id: String,
    val category: String, // FIRE, FLOOD, MEDICAL, etc.
    val description: String?,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val authorId: String,
    val witnessCount: Int,
    val isStale: Boolean
)

@Entity(tableName = "attachments")
data class AttachmentEntity(
    @PrimaryKey val id: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val localUri: String?, // Null if not downloaded yet
    val isVoiceNote: Boolean,
    val durationSeconds: Int?
)
