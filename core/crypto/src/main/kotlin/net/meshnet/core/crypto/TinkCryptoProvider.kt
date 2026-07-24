package net.meshnet.core.crypto

import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [CryptoProvider] backed by Google Tink via [CryptoEngine]
 * and the Android Keystore via [KeyManager].
 */
@Singleton
class TinkCryptoProvider @Inject constructor(
    private val cryptoEngine: CryptoEngine,
    private val keyManager: KeyManager,
) : CryptoProvider {

    private val secureRandom = SecureRandom()

    override fun encrypt(plaintext: ByteArray, key: ByteArray, aad: ByteArray): EncryptResult {
        return cryptoEngine.encrypt(plaintext, key, aad)
    }

    override fun decrypt(ciphertext: ByteArray, key: ByteArray, aad: ByteArray): ByteArray {
        return cryptoEngine.decrypt(ciphertext, key, aad)
    }

    override fun publicIdentityKey(): ByteArray {
        return keyManager.publicIdentityKey()
    }

    override fun sign(data: ByteArray): ByteArray {
        return keyManager.sign(data)
    }

    override fun verify(data: ByteArray, signature: ByteArray, publicKey: ByteArray): Boolean {
        // Convert the raw 32-byte Ed25519 public key into a Tink verifier
        return try {
            // Note: CryptoEngine needs to expose a way to verify with a raw public key,
            // or we must build the keyset handle for it. 
            // We'll assume CryptoEngine has a verify function that takes a raw public key.
            cryptoEngine.verifyRaw(data, signature, publicKey)
        } catch (e: Exception) {
            false
        }
    }

    override fun generateX25519KeyPair(): X25519KeyPair {
        return cryptoEngine.generateX25519KeyPair()
    }

    override fun computeSharedSecret(ourPrivateKey: ByteArray, theirPublicKey: ByteArray): ByteArray {
        return cryptoEngine.x25519SharedSecret(ourPrivateKey, theirPublicKey)
    }

    override fun hkdf(inputKeyMaterial: ByteArray, salt: ByteArray, info: ByteArray, outputLength: Int): ByteArray {
        return cryptoEngine.hkdf(inputKeyMaterial, salt, info, outputLength)
    }

    override fun secureRandomBytes(length: Int): ByteArray {
        val bytes = ByteArray(length)
        secureRandom.nextBytes(bytes)
        return bytes
    }
}
