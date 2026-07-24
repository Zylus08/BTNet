package net.meshnet.core.mesh.ble

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the rotation of the local device's ephemeral BLE advertiser ID.
 *
 * Privacy requirement: To prevent device tracking via BLE MAC or static UUIDs,
 * the node broadcasts an 8-byte pseudonymous identifier that rotates every
 * [ROTATION_INTERVAL_MS].
 *
 * Other nodes use this ephemeral ID for initial GATT connections, then request
 * the true 32-byte Ed25519 public key over the encrypted connection.
 */
@Singleton
class EphemeralIdManager @Inject constructor() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val random = SecureRandom()

    private val _currentId = MutableStateFlow(generateId())
    
    /**
     * The current 8-byte ephemeral ID. Updates automatically every 15 minutes.
     * Collectors should restart BLE advertising when this changes.
     */
    val currentId: StateFlow<ByteArray> = _currentId.asStateFlow()

    init {
        scope.launch {
            while (true) {
                delay(ROTATION_INTERVAL_MS)
                rotate()
            }
        }
    }

    private fun rotate() {
        val next = generateId()
        _currentId.value = next
        Timber.i("Ephemeral ID rotated to: ${next.toHex()}")
    }

    private fun generateId(): ByteArray {
        val id = ByteArray(ID_LENGTH_BYTES)
        random.nextBytes(id)
        return id
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    companion object {
        /** Size of the ephemeral ID. 8 bytes fits comfortably in a BLE manufacturer data payload. */
        const val ID_LENGTH_BYTES = 8

        /** Rotation interval: 15 minutes. */
        const val ROTATION_INTERVAL_MS = 15 * 60 * 1000L
    }
}
