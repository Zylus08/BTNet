package net.meshnet.core.crypto.ratchet

import net.meshnet.core.crypto.CryptoProvider
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implements the Key Derivation Function (KDF) chains for the Double Ratchet.
 * 
 * Includes:
 * 1. Root Chain (Ratchet step on Diffie-Hellman update)
 * 2. Sending/Receiving Header Chains
 * 3. Message Chains (Ratchet step per message)
 */
@Singleton
class KdfChain @Inject constructor(
    private val cryptoProvider: CryptoProvider,
) {
    /**
     * KDF for the Root Chain.
     * Takes the current Root Key (RK) and the new DH shared secret.
     * Returns a Pair of (new Root Key, new Chain Key).
     */
    fun kdfRoot(rk: ByteArray, dhOut: ByteArray): Pair<ByteArray, ByteArray> {
        val hkdfOutput = cryptoProvider.hkdf(
            inputKeyMaterial = dhOut,
            salt = rk,
            info = "MeshNetRootChain".toByteArray(),
            outputLength = 64
        )
        val newRk = hkdfOutput.copyOfRange(0, 32)
        val newCk = hkdfOutput.copyOfRange(32, 64)
        return Pair(newRk, newCk)
    }

    /**
     * KDF for the Message Chain.
     * Takes the current Chain Key (CK).
     * Returns a Pair of (new Chain Key, Message Key).
     */
    fun kdfMessage(ck: ByteArray): Pair<ByteArray, ByteArray> {
        // HMAC-SHA256 with constant input
        val mac = Mac.getInstance("HmacSHA256")
        
        // Output 1: Message Key (MAC with 0x01)
        mac.init(SecretKeySpec(ck, "HmacSHA256"))
        val mk = mac.doFinal(byteArrayOf(0x01))
        
        // Output 2: Next Chain Key (MAC with 0x02)
        mac.init(SecretKeySpec(ck, "HmacSHA256"))
        val newCk = mac.doFinal(byteArrayOf(0x02))

        return Pair(newCk, mk)
    }
}
