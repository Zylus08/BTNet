package net.meshnet.domain.model

/**
 * Domain-layer message model.
 * Decoupled from both the protobuf wire format and Room entity.
 * Mapped to/from [net.meshnet.core.protocol.MeshPacket] at the data layer boundary.
 */
data class Message(
    val id: String,
    val senderId: String,
    val recipientId: String,
    val content: String,
    val timestampMs: Long,
    val isRead: Boolean = false,
    val isDelivered: Boolean = false,
    val attachmentTransferId: String? = null,
)

/**
 * Domain-layer community report model.
 * Confidence score is computed by TrustEngine and surfaced here for the UI.
 */
data class CommunityReport(
    val id: String,
    val originatorId: String,
    val category: ReportCategory,
    val latitude: Double,
    val longitude: Double,
    val description: String,
    val createdAtMs: Long,
    val expiresAtMs: Long,
    val confidenceScore: Float,
    val witnessCount: Int,
    val isFlaggedStale: Boolean,
)

enum class ReportCategory {
    ROAD_CLOSED, MEDICAL, FIRE, FLOOD, HEAVY_CROWD,
    ACCESSIBILITY, WATER, SHELTER, OBSTRUCTION, HAZARD,
}
