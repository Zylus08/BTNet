package net.meshnet.core.crypto

/**
 * Interface abstracting all cryptographic operations.
 * Allows decoupling the messaging protocol from the underlying crypto implementation
 * (e.g., swapping Tink for hardware-backed keys or another library in the future).
 */
interface CryptoProvider {
    
    /** Encrypts [plaintext] using AES-256-GCM. */
    fun encrypt(plaintext: ByteArray, key: ByteArray, aad: ByteArray = ByteArray(0)): EncryptResult
    
    /** Decrypts [ciphertext] using AES-256-GCM. */
    fun decrypt(ciphertext: ByteArray, key: ByteArray, aad: ByteArray = ByteArray(0)): ByteArray

    /** Returns the device's public identity key (32-byte Ed25519 public key). */
    fun publicIdentityKey(): ByteArray

    /** Signs [data] using the active Ed25519 identity key. */
    fun sign(data: ByteArray): ByteArray

    /** Verifies an Ed25519 [signature] over [data] against [publicKey]. */
    fun verify(data: ByteArray, signature: ByteArray, publicKey: ByteArray): Boolean

    /** Generates a fresh X25519 key pair for ECDH. */
    fun generateX25519KeyPair(): X25519KeyPair

    /** Computes the X25519 shared secret. */
    fun computeSharedSecret(ourPrivateKey: ByteArray, theirPublicKey: ByteArray): ByteArray

    /** Derives a key using HKDF-SHA256. */
    fun hkdf(inputKeyMaterial: ByteArray, salt: ByteArray, info: ByteArray, outputLength: Int = 32): ByteArray
    
    /** Generates cryptographically secure random bytes. */
    fun secureRandomBytes(length: Int): ByteArray
}
