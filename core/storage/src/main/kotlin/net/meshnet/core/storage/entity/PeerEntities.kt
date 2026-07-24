package net.meshnet.core.storage.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
    tableName = "peers",
    indices = [androidx.room.Index("geohash")]
)
data class PeerEntity(
    @PrimaryKey val id: String, // Hex string of public key
    val alias: String?,
    val lastSeenTimestamp: Long,
    val capabilities: Int, // Bitmask
    val wifiDirectMac: String?,
    val geohash: String? // Nullable as peer may not broadcast location
)

@Entity(tableName = "trust_scores")
data class TrustEntity(
    @PrimaryKey val peerId: String,
    val score: Float, // 0.0 to 1.0
    val reportsVerified: Int,
    val reportsFlagged: Int,
    val lastUpdated: Long
)
