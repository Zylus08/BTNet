package net.meshnet.core.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the device's long-term identity keypair.
 *
 * The Ed25519 private key is generated once and stored in the Android Keystore
 * (hardware-backed on supported devices). The public key is stored alongside it
 * in EncryptedSharedPreferences for fast retrieval.
 *
 * Key lifecycle:
 *   - Generated on first launch inside [ensureIdentityKey].
 *   - Never leaves the device in plaintext.
 *   - Survives app reinstall if the user has a backup-capable Keystore.
 *
 * Rotating identifiers (BLE advertisement IDs) are derived from ephemeral keys
 * generated per-session — managed in [EphemeralKeyManager], not here.
 */
@Singleton
class KeyManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cryptoEngine: CryptoEngine,
) {
    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context, MASTER_KEY_ALIAS)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val prefs by lazy {
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    /**
     * Returns the device's public identity key (32-byte Ed25519 public key).
     * Generates a new keypair on first call.
     */
    fun publicIdentityKey(): ByteArray =
        prefs.getString(PREF_PUBLIC_KEY, null)
            ?.let { android.util.Base64.decode(it, android.util.Base64.NO_WRAP) }
            ?: generateAndStoreIdentityKey()

    /**
     * Signs [data] with the device's long-term Ed25519 identity key.
     * The private key never leaves the Keystore.
     */
    fun sign(data: ByteArray): ByteArray {
        ensureIdentityKey()
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).also { it.load(null) }
        val entry = keyStore.getEntry(SIGNING_KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry
            ?: error("Identity signing key not found in Keystore")
        val signature = java.security.Signature.getInstance(SIGNING_ALGORITHM).apply {
            initSign(entry.privateKey)
            update(data)
        }
        return signature.sign()
    }

    /**
     * Verifies an Ed25519 [signature] over [data] using [publicKey].
     */
    fun verify(data: ByteArray, signature: ByteArray, publicKey: ByteArray): Boolean =
        runCatching {
            val keyFactory = java.security.KeyFactory.getInstance(KEY_ALGORITHM)
            val pubKey = keyFactory.generatePublic(
                java.security.spec.X509EncodedKeySpec(publicKey)
            )
            java.security.Signature.getInstance(SIGNING_ALGORITHM).apply {
                initVerify(pubKey)
                update(data)
            }.verify(signature)
        }.getOrDefault(false)

    // ── Private ───────────────────────────────────────────────────────────────

    private fun ensureIdentityKey() {
        if (prefs.getString(PREF_PUBLIC_KEY, null) == null) {
            generateAndStoreIdentityKey()
        }
    }

    private fun generateAndStoreIdentityKey(): ByteArray {
        val keyPairGenerator = java.security.KeyPairGenerator.getInstance(
            KEY_ALGORITHM,
            ANDROID_KEYSTORE,
        )
        val spec = KeyGenParameterSpec.Builder(
            SIGNING_KEY_ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
        )
            .setAlgorithmParameterSpec(java.security.spec.ECGenParameterSpec("Ed25519"))
            .setDigests(KeyProperties.DIGEST_NONE)
            .setUserAuthenticationRequired(false) // always available; auth gate optional future
            .build()
        keyPairGenerator.initialize(spec)
        val keyPair = keyPairGenerator.generateKeyPair()
        val publicKeyBytes = keyPair.public.encoded
        prefs.edit()
            .putString(PREF_PUBLIC_KEY, android.util.Base64.encodeToString(publicKeyBytes, android.util.Base64.NO_WRAP))
            .apply()
        return publicKeyBytes
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val MASTER_KEY_ALIAS = "meshnet_master_key"
        private const val SIGNING_KEY_ALIAS = "meshnet_identity_signing_key"
        private const val PREFS_FILE = "meshnet_identity_prefs"
        private const val PREF_PUBLIC_KEY = "public_identity_key"
        private const val SIGNING_ALGORITHM = "Ed25519"
        private const val KEY_ALGORITHM = "EC"
    }
}
