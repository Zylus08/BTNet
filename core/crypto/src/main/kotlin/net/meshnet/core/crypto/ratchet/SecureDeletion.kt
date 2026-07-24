package net.meshnet.core.crypto.ratchet

import timber.log.Timber
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles the secure wiping of cryptographic state from memory.
 * 
 * In a JVM environment, true memory wiping is difficult due to garbage collection
 * moving objects. This utility relies on overwriting byte arrays directly before
 * allowing them to be GC'd.
 */
@Singleton
class SecureDeletion @Inject constructor() {
    private val secureRandom = SecureRandom()

    /**
     * Overwrites the provided [byteArray] with secure random data, then zeros.
     */
    fun wipe(byteArray: ByteArray?) {
        if (byteArray == null || byteArray.isEmpty()) return
        
        // 1. Overwrite with random data to frustrate memory inspection
        secureRandom.nextBytes(byteArray)
        
        // 2. Overwrite with zeros
        for (i in byteArray.indices) {
            byteArray[i] = 0
        }
    }

    /**
     * Securely deletes an entire session from memory.
     */
    fun wipeSession(state: RatchetState) {
        wipe(state.DHs.privateKey)
        wipe(state.DHs.publicKey)
        wipe(state.DHr)
        wipe(state.RK)
        wipe(state.CKs)
        wipe(state.CKr)
        
        state.MKSKIPS.values.forEach { mk -> wipe(mk) }
        state.MKSKIPS.clear()
        
        Timber.d("Securely wiped session state from memory")
    }
}
