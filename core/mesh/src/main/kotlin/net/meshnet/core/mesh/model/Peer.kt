package net.meshnet.core.mesh.model

import net.meshnet.core.protocol.Capabilities

/**
 * Represents a discovered peer node in the mesh.
 *
 * @property id              32-byte Ed25519 public key used as the stable peer identifier.
 * @property advertisedId    Rotating 8-byte ephemeral ID seen in BLE advertisements.
 *                           Changes every 15 minutes to prevent long-term tracking.
 * @property nickname        Optional human-readable label (set only after QR exchange).
 * @property capabilities    Features this peer supports, as declared in PEER_ANNOUNCE.
 * @property rssi            Last observed RSSI in dBm; used for transport selection.
 * @property lastSeenMs      Unix millis of last packet received from this peer.
 * @property trustLevel      [TrustLevel] assigned by the local user or via QR verification.
 */
data class Peer(
    val id: ByteArray,
    val advertisedId: ByteArray,
    val nickname: String? = null,
    val capabilities: Capabilities = Capabilities.getDefaultInstance(),
    val rssi: Int = Int.MIN_VALUE,
    val lastSeenMs: Long = 0L,
    val trustLevel: TrustLevel = TrustLevel.UNKNOWN,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Peer) return false
        return id.contentEquals(other.id)
    }

    override fun hashCode(): Int = id.contentHashCode()
}

enum class TrustLevel {
    /** Never interacted; default for newly discovered peers. */
    UNKNOWN,
    /** Verified via out-of-band QR code exchange. */
    VERIFIED,
    /** Manually promoted by the local user. */
    TRUSTED,
}
