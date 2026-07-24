package net.meshnet.core.storage.peer

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persistent peer record.
 *
 * [id] is the hex-encoded Ed25519 public key (64 chars = 32 bytes).
 * Indexed for fast lookup by [advertisedId] during BLE advertisement matching.
 */
@Entity(
    tableName = "peers",
    indices = [
        Index(value = ["advertised_id"]),
        Index(value = ["last_seen_ms"]),
    ]
)
data class PeerEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,                          // hex Ed25519 public key

    @ColumnInfo(name = "advertised_id")
    val advertisedId: String,                // hex rotating ephemeral ID

    @ColumnInfo(name = "nickname")
    val nickname: String?,

    @ColumnInfo(name = "capabilities_json")
    val capabilitiesJson: String,            // JSON-serialised Capabilities proto

    @ColumnInfo(name = "last_seen_ms")
    val lastSeenMs: Long,

    @ColumnInfo(name = "rssi")
    val rssi: Int,

    @ColumnInfo(name = "trust_level")
    val trustLevel: String,                  // TrustLevel enum name

    @ColumnInfo(name = "created_at_ms")
    val createdAtMs: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "public_key_bytes")
    val publicKeyBytes: ByteArray,           // raw 32-byte Ed25519 public key
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PeerEntity) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
