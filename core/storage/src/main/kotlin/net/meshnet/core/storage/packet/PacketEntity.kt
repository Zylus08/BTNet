package net.meshnet.core.storage.packet

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persisted packet for store-and-forward delivery.
 *
 * Packets remain in this table until:
 *  - Delivery ACK received ([status] = [PacketStatus.DELIVERED])
 *  - TTL expires ([status] = [PacketStatus.EXPIRED])
 *  - Manual deletion
 *
 * [priority] drives dispatch ordering: EMERGENCY > NORMAL > BACKGROUND.
 */
@Entity(
    tableName = "packets",
    indices = [
        Index(value = ["sender_id"]),
        Index(value = ["recipient_id"]),
        Index(value = ["status"]),
        Index(value = ["priority", "created_at_ms"]),
        Index(value = ["expires_at_ms"]),
    ]
)
data class PacketEntity(
    @PrimaryKey
    @ColumnInfo(name = "packet_id")
    val packetId: String,                // hex 16-byte UUID

    @ColumnInfo(name = "sender_id")
    val senderId: String,                // hex Ed25519 public key

    @ColumnInfo(name = "recipient_id")
    val recipientId: String,             // hex; "broadcast" for broadcast packets

    @ColumnInfo(name = "packet_type")
    val packetType: Int,                 // PacketType ordinal

    @ColumnInfo(name = "payload")
    val payload: ByteArray,              // encrypted payload bytes

    @ColumnInfo(name = "status")
    val status: String,                  // PacketStatus enum name

    @ColumnInfo(name = "priority")
    val priority: Int,                   // 0=BACKGROUND, 1=NORMAL, 2=EMERGENCY

    @ColumnInfo(name = "ttl")
    val ttl: Int,

    @ColumnInfo(name = "hop_count")
    val hopCount: Int,

    @ColumnInfo(name = "created_at_ms")
    val createdAtMs: Long,

    @ColumnInfo(name = "expires_at_ms")
    val expiresAtMs: Long,

    @ColumnInfo(name = "last_forward_attempt_ms")
    val lastForwardAttemptMs: Long = 0L,

    @ColumnInfo(name = "forward_attempt_count")
    val forwardAttemptCount: Int = 0,

    @ColumnInfo(name = "raw_proto")
    val rawProto: ByteArray,             // serialised MeshPacket protobuf
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PacketEntity) return false
        return packetId == other.packetId
    }

    override fun hashCode(): Int = packetId.hashCode()
}

enum class PacketStatus {
    PENDING,     // waiting for a route / peer
    FORWARDED,   // sent to at least one peer; awaiting ACK
    DELIVERED,   // ACK received
    EXPIRED,     // TTL elapsed or expiry time passed
    FAILED,      // max retry count exceeded
}
