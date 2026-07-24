package net.meshnet.core.crypto

import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.AeadKeyTemplates
import com.google.crypto.tink.hybrid.HybridConfig
import com.google.crypto.tink.signature.SignatureConfig
import com.google.crypto.tink.signature.SignatureKeyTemplates
import com.google.crypto.tink.subtle.Ed25519Sign
import com.google.crypto.tink.subtle.Ed25519Verify
import com.google.crypto.tink.subtle.X25519
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides all cryptographic operations required by MeshNet.
 *
 * Algorithms:
 *  - Key exchange : X25519 ECDH
 *  - Key derivation: HKDF-SHA256
 *  - Encryption   : AES-256-GCM (via Tink AEAD)
 *  - Signing      : Ed25519
 *
 * Thread-safe: all state is immutable after construction.
 */
@Singleton
class CryptoEngine @Inject constructor() {

    init {
        AeadConfig.register()
        HybridConfig.register()
        SignatureConfig.register()
    }

    // ── AES-256-GCM ───────────────────────────────────────────────────────────

    /**
     * Encrypts [plaintext] with AES-256-GCM.
     *
     * @param plaintext  raw bytes to encrypt
     * @param key        32-byte session key (derived via HKDF)
     * @param aad        associated data bound to the ciphertext; use packet header bytes
     * @return [EncryptResult] containing ciphertext and 12-byte nonce
     */
    fun encrypt(
        plaintext: ByteArray,
        key: ByteArray,
        aad: ByteArray = ByteArray(0),
    ): EncryptResult {
        require(key.size == AES_KEY_BYTES) { "Key must be $AES_KEY_BYTES bytes" }
        val keysetHandle = buildAesGcmKeysetHandle(key)
        val aead = keysetHandle.getPrimitive(com.google.crypto.tink.Aead::class.java)
        // Tink prepends a 5-byte key ID + 12-byte nonce to the ciphertext; we extract below.
        val ciphertext = aead.encrypt(plaintext, aad)
        // First 5 bytes: Tink keyset prefix; next 12: nonce; remainder: actual ciphertext + tag
        val nonce = ciphertext.copyOfRange(TINK_PREFIX_SIZE, TINK_PREFIX_SIZE + NONCE_BYTES)
        return EncryptResult(ciphertext = ciphertext, nonce = nonce)
    }

    /**
     * Decrypts [ciphertext] with AES-256-GCM.
     *
     * @throws javax.crypto.AEADBadTagException on authentication failure
     */
    fun decrypt(
        ciphertext: ByteArray,
        key: ByteArray,
        aad: ByteArray = ByteArray(0),
    ): ByteArray {
        require(key.size == AES_KEY_BYTES) { "Key must be $AES_KEY_BYTES bytes" }
        val keysetHandle = buildAesGcmKeysetHandle(key)
        val aead = keysetHandle.getPrimitive(com.google.crypto.tink.Aead::class.java)
        return aead.decrypt(ciphertext, aad)
    }

    // ── X25519 ECDH ──────────────────────────────────────────────────────────

    /** Generates a fresh X25519 key pair for ephemeral key exchange. */
    fun generateX25519KeyPair(): X25519KeyPair {
        val privateKey = X25519.generatePrivateKey()
        val publicKey = X25519.publicFromPrivate(privateKey)
        return X25519KeyPair(privateKey = privateKey, publicKey = publicKey)
    }

    /**
     * Computes the X25519 shared secret.
     *
     * @param ourPrivateKey  our 32-byte X25519 private key
     * @param theirPublicKey peer's 32-byte X25519 public key
     * @return 32-byte raw shared secret (pass through HKDF before use as session key)
     */
    fun x25519SharedSecret(ourPrivateKey: ByteArray, theirPublicKey: ByteArray): ByteArray =
        X25519.computeSharedSecret(ourPrivateKey, theirPublicKey)

    // ── HKDF-SHA256 ──────────────────────────────────────────────────────────

    /**
     * Derives a session key from a shared secret using HKDF-SHA256.
     *
     * @param inputKeyMaterial the raw DH shared secret
     * @param salt             optional random salt (use 32 zero bytes if absent)
     * @param info             context label (e.g. "meshnet-session-v1".toByteArray())
     * @param outputLength     desired key length in bytes (default 32 for AES-256)
     */
    fun hkdf(
        inputKeyMaterial: ByteArray,
        salt: ByteArray,
        info: ByteArray,
        outputLength: Int = AES_KEY_BYTES,
    ): ByteArray =
        com.google.crypto.tink.subtle.Hkdf.computeHkdf(
            MAC_ALGORITHM,
            inputKeyMaterial,
            salt,
            info,
            outputLength,
        )

    // ── Ed25519 ──────────────────────────────────────────────────────────────

    /** Generates an Ed25519 signing key pair for long-term identity. */
    fun generateEd25519KeyPair(): Ed25519KeyPair {
        val handle = KeysetHandle.generateNew(SignatureKeyTemplates.ED25519)
        // Extract raw private key bytes via Tink's JsonKeysetWriter for test vector validation.
        // In production, the private key remains inside the Tink keyset handle / Keystore.
        val signer = handle.getPrimitive(com.google.crypto.tink.PublicKeySign::class.java)
        val publicHandle = handle.publicKeysetHandle
        val verifier = publicHandle.getPrimitive(com.google.crypto.tink.PublicKeyVerify::class.java)
        return Ed25519KeyPair(signer = signer, verifier = verifier, keysetHandle = handle)
    }

    /**
     * Signs [data] with the given Ed25519 signer.
     *
     * @return 64-byte Ed25519 signature
     */
    fun sign(data: ByteArray, signer: com.google.crypto.tink.PublicKeySign): ByteArray =
        signer.sign(data)

    /**
     * Verifies an Ed25519 [signature] over [data].
     *
     * @throws com.google.crypto.tink.subtle.Validators on invalid signature
     */
    fun verify(
        data: ByteArray,
        signature: ByteArray,
        verifier: com.google.crypto.tink.PublicKeyVerify,
    ) = verifier.verify(signature, data)

    // ── Private helpers ───────────────────────────────────────────────────────

    @Suppress("TooGenericExceptionCaught")
    private fun buildAesGcmKeysetHandle(key: ByteArray): KeysetHandle {
        // Wrap raw key bytes into a Tink AES-GCM keyset.
        val keyData = com.google.crypto.tink.proto.KeyData.newBuilder()
            .setTypeUrl("type.googleapis.com/google.crypto.tink.AesGcmKey")
            .setValue(
                com.google.crypto.tink.proto.AesGcmKey.newBuilder()
                    .setKeyValue(com.google.protobuf.ByteString.copyFrom(key))
                    .setVersion(0)
                    .build()
                    .toByteString()
            )
            .setKeyMaterialType(com.google.crypto.tink.proto.KeyData.KeyMaterialType.SYMMETRIC)
            .build()
        val keysetKey = com.google.crypto.tink.proto.Keyset.Key.newBuilder()
            .setKeyData(keyData)
            .setStatus(com.google.crypto.tink.proto.KeyStatusType.ENABLED)
            .setKeyId(1)
            .setOutputPrefixType(com.google.crypto.tink.proto.OutputPrefixType.RAW)
            .build()
        val keyset = com.google.crypto.tink.proto.Keyset.newBuilder()
            .addKey(keysetKey)
            .setPrimaryKeyId(1)
            .build()
        return KeysetHandle.fromKeyset(keyset)
    }

    companion object {
        const val AES_KEY_BYTES = 32
        const val NONCE_BYTES = 12
        const val TINK_PREFIX_SIZE = 5
        private const val MAC_ALGORITHM = "HMACSHA256"
    }
}

// ── Value types ───────────────────────────────────────────────────────────────

data class EncryptResult(
    val ciphertext: ByteArray,
    val nonce: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncryptResult) return false
        return ciphertext.contentEquals(other.ciphertext) && nonce.contentEquals(other.nonce)
    }

    override fun hashCode(): Int = 31 * ciphertext.contentHashCode() + nonce.contentHashCode()
}

data class X25519KeyPair(
    val privateKey: ByteArray,
    val publicKey: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is X25519KeyPair) return false
        return privateKey.contentEquals(other.privateKey) && publicKey.contentEquals(other.publicKey)
    }

    override fun hashCode(): Int = 31 * privateKey.contentHashCode() + publicKey.contentHashCode()
}

data class Ed25519KeyPair(
    val signer: com.google.crypto.tink.PublicKeySign,
    val verifier: com.google.crypto.tink.PublicKeyVerify,
    val keysetHandle: KeysetHandle,
)
