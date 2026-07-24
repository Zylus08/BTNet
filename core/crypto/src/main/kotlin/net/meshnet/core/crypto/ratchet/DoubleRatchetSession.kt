package net.meshnet.core.crypto.ratchet

import net.meshnet.core.crypto.CryptoProvider
import net.meshnet.core.crypto.X25519KeyPair
import timber.log.Timber

/**
 * State and logic for a single 1:1 Double Ratchet session.
 * 
 * Based on the Signal Double Ratchet algorithm.
 */
class DoubleRatchetSession(
    private val cryptoProvider: CryptoProvider,
    private val kdfChain: KdfChain,
    var state: RatchetState,
) {

    /**
     * Encrypts a message payload.
     * Steps:
     * 1. KDF Message Chain to get Message Key (MK) and new Chain Key (CKs).
     * 2. Encrypt plaintext with MK.
     * 3. Construct header (Our DH Public Key, Previous Chain Length, Message Number).
     */
    fun encrypt(plaintext: ByteArray, aad: ByteArray = ByteArray(0)): RatchetMessage {
        val (newCkS, mk) = kdfChain.kdfMessage(state.CKs)
        state.CKs = newCkS

        val header = RatchetHeader(
            dhPublicKey = state.DHs.publicKey,
            previousChainLength = state.PN,
            messageNumber = state.Ns
        )
        
        state.Ns += 1

        // Use AEAD to encrypt payload. AD = header bytes + custom AAD
        val headerBytes = header.toByteArray()
        val ad = headerBytes + aad
        
        val encryptResult = cryptoProvider.encrypt(plaintext, mk, ad)

        return RatchetMessage(
            header = header,
            ciphertext = encryptResult.ciphertext,
            nonce = encryptResult.nonce
        )
    }

    /**
     * Decrypts a message payload.
     * Steps:
     * 1. Check if it's a skipped message.
     * 2. If new DH Key received, perform DH Ratchet step.
     * 3. KDF Message Chain to get Message Key (MK).
     * 4. Decrypt ciphertext.
     */
    fun decrypt(message: RatchetMessage, aad: ByteArray = ByteArray(0)): ByteArray {
        // 1. Try skipped message keys
        val skippedKey = trySkippedMessageKeys(message.header)
        if (skippedKey != null) {
            return decryptWithKey(message, skippedKey, aad)
        }

        // 2. DH Ratchet Step if new public key received
        val headerDh = message.header.dhPublicKey
        if (state.DHr == null || !state.DHr.contentEquals(headerDh)) {
            skipMessageKeys(message.header.previousChainLength)
            dhRatchetStep(headerDh)
        }

        // 3. Skip missed messages in current chain
        skipMessageKeys(message.header.messageNumber)

        // 4. Derive MK and decrypt
        val (newCkR, mk) = kdfChain.kdfMessage(state.CKr!!)
        state.CKr = newCkR
        state.Nr += 1

        return decryptWithKey(message, mk, aad)
    }

    private fun dhRatchetStep(newTheirDh: ByteArray) {
        state.PN = state.Ns
        state.Ns = 0
        state.Nr = 0
        state.DHr = newTheirDh

        // DH1: Our current DHs (private) * Their new DHr (public)
        val dhOut1 = cryptoProvider.computeSharedSecret(state.DHs.privateKey, state.DHr!!)
        val (rk1, ckr) = kdfChain.kdfRoot(state.RK, dhOut1)
        state.RK = rk1
        state.CKr = ckr

        // Generate our new DHs
        state.DHs = cryptoProvider.generateX25519KeyPair()

        // DH2: Our new DHs (private) * Their new DHr (public)
        val dhOut2 = cryptoProvider.computeSharedSecret(state.DHs.privateKey, state.DHr!!)
        val (rk2, cks) = kdfChain.kdfRoot(state.RK, dhOut2)
        state.RK = rk2
        state.CKs = cks
    }

    private fun skipMessageKeys(until: Int) {
        if (state.CKr == null) return
        while (state.Nr < until) {
            val (newCkR, mk) = kdfChain.kdfMessage(state.CKr!!)
            state.CKr = newCkR
            val keyHash = hashHeader(state.DHr!!, state.Nr)
            state.MKSKIPS[keyHash] = mk
            state.Nr += 1
            
            // Limit skip keys to prevent OOM / DoS
            if (state.MKSKIPS.size > MAX_SKIP_KEYS) {
                Timber.w("Exceeded max skip keys, dropping oldest")
                val oldest = state.MKSKIPS.keys.first()
                state.MKSKIPS.remove(oldest)
            }
        }
    }

    private fun trySkippedMessageKeys(header: RatchetHeader): ByteArray? {
        val keyHash = hashHeader(header.dhPublicKey, header.messageNumber)
        return state.MKSKIPS.remove(keyHash)
    }

    private fun decryptWithKey(message: RatchetMessage, mk: ByteArray, aad: ByteArray): ByteArray {
        val ad = message.header.toByteArray() + aad
        return cryptoProvider.decrypt(message.ciphertext, mk, ad) // Decrypt throws if auth fails
    }

    private fun hashHeader(dhPublicKey: ByteArray, messageNumber: Int): String {
        return "${dhPublicKey.toHex()}_$messageNumber"
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    companion object {
        const val MAX_SKIP_KEYS = 1000
    }
}

data class RatchetState(
    var DHs: X25519KeyPair,       // Our DH Key Pair
    var DHr: ByteArray?,          // Their DH Public Key
    var RK: ByteArray,            // Root Key
    var CKs: ByteArray,           // Sender Chain Key
    var CKr: ByteArray?,          // Receiver Chain Key
    var Ns: Int = 0,              // Sending Message Number
    var Nr: Int = 0,              // Receiving Message Number
    var PN: Int = 0,              // Previous Chain Length
    val MKSKIPS: MutableMap<String, ByteArray> = mutableMapOf() // Skipped Message Keys
)

data class RatchetHeader(
    val dhPublicKey: ByteArray,
    val previousChainLength: Int,
    val messageNumber: Int,
) {
    fun toByteArray(): ByteArray {
        // Simplified encoding for the stub
        return dhPublicKey + previousChainLength.toByte() + messageNumber.toByte()
    }
}

data class RatchetMessage(
    val header: RatchetHeader,
    val ciphertext: ByteArray,
    val nonce: ByteArray, // AEAD Nonce
)
