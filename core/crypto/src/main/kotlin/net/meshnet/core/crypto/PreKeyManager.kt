package net.meshnet.core.crypto

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages X3DH Pre-Keys for establishing secure sessions.
 * 
 * Responsibilities:
 * - Generate and store the Signed Pre-Key (rotated periodically)
 * - Generate and store One-Time Pre-Keys (OTPKs)
 * - Provide the public Pre-Key Bundle for peers
 * 
 * Storage:
 * EncryptedSharedPreferences is used for simplicity. In a high-volume scenario,
 * a SQLCipher database is preferred.
 */
@Singleton
class PreKeyManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cryptoProvider: CryptoProvider,
) {
    private val mutex = Mutex()

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

    suspend fun getPreKeyBundle(): PreKeyBundle = mutex.withLock {
        ensureSignedPreKey()
        ensureOneTimePreKeys(MIN_OTPK_COUNT)

        val spk = getSignedPreKey() ?: error("Signed Pre-Key missing")
        val otpks = getOneTimePreKeys()
        
        // Take the first available OTPK for the bundle (without deleting it yet)
        val otpk = otpks.firstOrNull()

        return PreKeyBundle(
            identityKey = cryptoProvider.publicIdentityKey(),
            signedPreKeyId = spk.id,
            signedPreKey = spk.publicKey,
            signedPreKeySignature = spk.signature,
            oneTimePreKeyId = otpk?.id,
            oneTimePreKey = otpk?.publicKey
        )
    }

    suspend fun consumeOneTimePreKey(id: Int): X25519KeyPair? = mutex.withLock {
        val otpks = getOneTimePreKeys().toMutableList()
        val index = otpks.indexOfFirst { it.id == id }
        if (index != -1) {
            val key = otpks.removeAt(index)
            saveOneTimePreKeys(otpks)
            return X25519KeyPair(key.privateKey, key.publicKey)
        }
        return null
    }

    suspend fun getSignedPreKeyPair(): X25519KeyPair? = mutex.withLock {
        val spk = getSignedPreKey() ?: return null
        return X25519KeyPair(spk.privateKey, spk.publicKey)
    }

    private fun ensureSignedPreKey() {
        if (!prefs.contains(PREF_SPK)) {
            val keyPair = cryptoProvider.generateX25519KeyPair()
            val signature = cryptoProvider.sign(keyPair.publicKey)
            val spk = SignedPreKeyRecord(1, keyPair.privateKey, keyPair.publicKey, signature)
            saveSignedPreKey(spk)
        }
    }

    private fun ensureOneTimePreKeys(minCount: Int) {
        val current = getOneTimePreKeys().toMutableList()
        if (current.size < minCount) {
            var nextId = (current.maxOfOrNull { it.id } ?: 0) + 1
            while (current.size < MAX_OTPK_COUNT) {
                val kp = cryptoProvider.generateX25519KeyPair()
                current.add(OneTimePreKeyRecord(nextId++, kp.privateKey, kp.publicKey))
            }
            saveOneTimePreKeys(current)
        }
    }

    private fun getSignedPreKey(): SignedPreKeyRecord? {
        val jsonStr = prefs.getString(PREF_SPK, null) ?: return null
        val json = JSONObject(jsonStr)
        return SignedPreKeyRecord(
            id = json.getInt("id"),
            privateKey = json.getString("priv").fromBase64(),
            publicKey = json.getString("pub").fromBase64(),
            signature = json.getString("sig").fromBase64()
        )
    }

    private fun saveSignedPreKey(spk: SignedPreKeyRecord) {
        val json = JSONObject().apply {
            put("id", spk.id)
            put("priv", spk.privateKey.toBase64())
            put("pub", spk.publicKey.toBase64())
            put("sig", spk.signature.toBase64())
        }
        prefs.edit().putString(PREF_SPK, json.toString()).apply()
    }

    private fun getOneTimePreKeys(): List<OneTimePreKeyRecord> {
        val jsonStr = prefs.getString(PREF_OTPKS, "[]")!!
        val array = JSONArray(jsonStr)
        val list = mutableListOf<OneTimePreKeyRecord>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(
                OneTimePreKeyRecord(
                    id = obj.getInt("id"),
                    privateKey = obj.getString("priv").fromBase64(),
                    publicKey = obj.getString("pub").fromBase64()
                )
            )
        }
        return list
    }

    private fun saveOneTimePreKeys(otpks: List<OneTimePreKeyRecord>) {
        val array = JSONArray()
        for (otpk in otpks) {
            val obj = JSONObject().apply {
                put("id", otpk.id)
                put("priv", otpk.privateKey.toBase64())
                put("pub", otpk.publicKey.toBase64())
            }
            array.put(obj)
        }
        prefs.edit().putString(PREF_OTPKS, array.toString()).apply()
    }

    private fun ByteArray.toBase64() = android.util.Base64.encodeToString(this, android.util.Base64.NO_WRAP)
    private fun String.fromBase64() = android.util.Base64.decode(this, android.util.Base64.NO_WRAP)

    companion object {
        private const val MASTER_KEY_ALIAS = "meshnet_prekey_master"
        private const val PREFS_FILE = "meshnet_prekeys"
        private const val PREF_SPK = "signed_pre_key"
        private const val PREF_OTPKS = "one_time_pre_keys"
        
        const val MIN_OTPK_COUNT = 20
        const val MAX_OTPK_COUNT = 100
    }
}

data class SignedPreKeyRecord(
    val id: Int,
    val privateKey: ByteArray,
    val publicKey: ByteArray,
    val signature: ByteArray,
)

data class OneTimePreKeyRecord(
    val id: Int,
    val privateKey: ByteArray,
    val publicKey: ByteArray,
)

data class PreKeyBundle(
    val identityKey: ByteArray,
    val signedPreKeyId: Int,
    val signedPreKey: ByteArray,
    val signedPreKeySignature: ByteArray,
    val oneTimePreKeyId: Int?,
    val oneTimePreKey: ByteArray?,
)
