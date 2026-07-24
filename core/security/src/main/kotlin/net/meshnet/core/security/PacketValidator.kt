package net.meshnet.core.security

import net.meshnet.core.protocol.MeshPacket
import net.meshnet.core.protocol.PacketType
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Validates incoming [MeshPacket]s before they enter the routing layer.
 *
 * Checks performed (in order, fail-fast):
 *   1. Protocol version compatibility
 *   2. Packet ID size (must be 16 bytes)
 *   3. Sender ID size (must be 32 bytes)
 *   4. Recipient ID size (must be 0 or 32 bytes; 0 = broadcast)
 *   5. TTL bounds (1 – MAX_TTL)
 *   6. Timestamp within replay window (±REPLAY_WINDOW_MS of now)
 *   7. Nonce size (must be 12 bytes)
 *   8. Payload non-empty for payload-bearing types
 *   9. Signature non-empty (unsigned packets rejected)
 *  10. Packet type known (not UNKNOWN)
 */
@Singleton
class PacketValidator @Inject constructor() {

    /**
     * Validates [packet].
     *
     * @return [ValidationResult.Valid] if all checks pass.
     * @return [ValidationResult.Invalid] with a reason on the first failure.
     */
    fun validate(packet: MeshPacket, nowMs: Long = System.currentTimeMillis()): ValidationResult {
        // 1. Protocol version
        if (packet.protocolVersion < MIN_PROTOCOL_VERSION || packet.protocolVersion > MAX_PROTOCOL_VERSION) {
            return invalid("Unsupported protocol version: ${packet.protocolVersion}")
        }

        // 2. Packet ID
        if (packet.packetId.size() != PACKET_ID_BYTES) {
            return invalid("Invalid packet_id size: ${packet.packetId.size()}")
        }

        // 3. Sender ID
        if (packet.senderId.size() != PUBLIC_KEY_BYTES) {
            return invalid("Invalid sender_id size: ${packet.senderId.size()}")
        }

        // 4. Recipient ID (0 = broadcast, 32 = unicast)
        val recipientSize = packet.recipientId.size()
        if (recipientSize != 0 && recipientSize != PUBLIC_KEY_BYTES) {
            return invalid("Invalid recipient_id size: $recipientSize")
        }

        // 5. TTL
        if (packet.ttl < 1 || packet.ttl > MAX_TTL) {
            return invalid("TTL out of bounds: ${packet.ttl}")
        }

        // 6. Replay window
        val delta = kotlin.math.abs(packet.timestampMs - nowMs)
        if (delta > REPLAY_WINDOW_MS) {
            return invalid("Timestamp outside replay window: delta=${delta}ms")
        }

        // 7. Nonce
        if (packet.nonce.size() != NONCE_BYTES) {
            return invalid("Invalid nonce size: ${packet.nonce.size()}")
        }

        // 8. Payload non-empty for payload-bearing types
        if (packet.type in PAYLOAD_REQUIRED_TYPES && packet.payload.isEmpty) {
            return invalid("Empty payload for type ${packet.type}")
        }

        // 9. Signature required
        if (packet.signature.isEmpty) {
            return invalid("Missing Ed25519 signature — packet rejected")
        }

        // 10. Known packet type
        if (packet.type == PacketType.UNKNOWN || packet.type == PacketType.UNRECOGNIZED) {
            return invalid("Unknown packet type: ${packet.type}")
        }

        return ValidationResult.Valid
    }

    private fun invalid(reason: String): ValidationResult.Invalid {
        Timber.w("Packet rejected: $reason")
        return ValidationResult.Invalid(reason)
    }

    companion object {
        const val MIN_PROTOCOL_VERSION = 1
        const val MAX_PROTOCOL_VERSION = 1
        const val PACKET_ID_BYTES = 16
        const val PUBLIC_KEY_BYTES = 32
        const val NONCE_BYTES = 12
        const val MAX_TTL = 64
        /** ±5 minutes replay window. */
        const val REPLAY_WINDOW_MS = 5 * 60 * 1000L

        private val PAYLOAD_REQUIRED_TYPES = setOf(
            PacketType.MESSAGE,
            PacketType.REPORT,
            PacketType.FILE_CHUNK,
            PacketType.VOICE_CHUNK,
            PacketType.ROUTING_UPDATE,
        )
    }
}

sealed interface ValidationResult {
    data object Valid : ValidationResult
    data class Invalid(val reason: String) : ValidationResult
}
