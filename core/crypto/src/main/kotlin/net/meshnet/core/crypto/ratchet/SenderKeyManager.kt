package net.meshnet.core.crypto.ratchet

import net.meshnet.core.crypto.CryptoProvider
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implements the Sender Key protocol for efficient group messaging.
 * 
 * Instead of encrypting a message N times for N group members (like 1:1 sessions),
 * the sender encrypts it once using a ratcheting Sender Key. The sender distributes
 * their initial Sender Key to all group members via their secure 1:1 Double Ratchet sessions.
 */
@Singleton
class SenderKeyManager @Inject constructor(
    private val cryptoProvider: CryptoProvider,
) {

    /**
     * Generates a new Sender Key Record for a group.
     */
    fun generateSenderKey(): SenderKeyRecord {
        val chainKey = cryptoProvider.secureRandomBytes(32)
        val signatureKeyPair = cryptoProvider.generateX25519KeyPair() // In reality Ed25519 is used for signatures, we'll use X25519 placeholder or rely on Tink
        
        return SenderKeyRecord(
            iteration = 0,
            chainKey = chainKey,
            signaturePrivateKey = signatureKeyPair.privateKey,
            signaturePublicKey = signatureKeyPair.publicKey
        )
    }

    /**
     * Ratchets the Sender Key to produce a Message Key and the next Chain Key.
     */
    fun ratchetSenderKey(record: SenderKeyRecord): Pair<SenderKeyRecord, ByteArray> {
        val mac = Mac.getInstance("HmacSHA256")
        
        // Message Key
        mac.init(SecretKeySpec(record.chainKey, "HmacSHA256"))
        val messageKey = mac.doFinal(byteArrayOf(0x01))
        
        // Next Chain Key
        mac.init(SecretKeySpec(record.chainKey, "HmacSHA256"))
        val nextChainKey = mac.doFinal(byteArrayOf(0x02))

        val nextRecord = record.copy(
            iteration = record.iteration + 1,
            chainKey = nextChainKey
        )

        return Pair(nextRecord, messageKey)
    }

    /**
     * Encrypts a payload for a group using the sender's current Sender Key.
     */
    fun encryptGroupMessage(
        plaintext: ByteArray,
        senderKeyRecord: SenderKeyRecord,
        aad: ByteArray = ByteArray(0)
    ): GroupCiphertext {
        val (nextRecord, messageKey) = ratchetSenderKey(senderKeyRecord)
        
        val header = GroupHeader(
            iteration = senderKeyRecord.iteration,
            signaturePublicKey = senderKeyRecord.signaturePublicKey
        )

        val headerBytes = header.toByteArray()
        val ad = headerBytes + aad

        val encryptResult = cryptoProvider.encrypt(plaintext, messageKey, ad)

        // The entire payload (header + ciphertext) is then signed
        val payloadToSign = headerBytes + encryptResult.ciphertext
        // In a real Signal protocol, the SenderKey generates an Ed25519 keypair for signing.
        // For this stub, we'll assume CryptoProvider.sign(payload) uses the Identity Key 
        // or a dedicated Group Signature Key.
        val signature = cryptoProvider.sign(payloadToSign)

        return GroupCiphertext(
            nextSenderKeyRecord = nextRecord,
            header = header,
            ciphertext = encryptResult.ciphertext,
            nonce = encryptResult.nonce,
            signature = signature
        )
    }

    /**
     * Decrypts a group message from a specific sender.
     */
    fun decryptGroupMessage(
        ciphertext: GroupCiphertext,
        senderState: SenderKeyState,
        aad: ByteArray = ByteArray(0)
    ): ByteArray {
        // 1. Verify Signature
        val payloadToVerify = ciphertext.header.toByteArray() + ciphertext.ciphertext
        val valid = cryptoProvider.verify(
            data = payloadToVerify,
            signature = ciphertext.signature,
            publicKey = ciphertext.header.signaturePublicKey
        )
        if (!valid) throw SecurityException("Invalid group message signature")

        // 2. Ratchet forward if necessary to reach the message's iteration
        var currentRecord = senderState.record
        while (currentRecord.iteration < ciphertext.header.iteration) {
            val (next, _) = ratchetSenderKey(currentRecord)
            currentRecord = next
        }

        if (currentRecord.iteration > ciphertext.header.iteration) {
            throw SecurityException("Message iteration is in the past (replay or missed key)")
        }

        // 3. Derive message key for THIS iteration
        val (_, messageKey) = ratchetSenderKey(currentRecord)

        // 4. Decrypt
        val ad = ciphertext.header.toByteArray() + aad
        return cryptoProvider.decrypt(ciphertext.ciphertext, messageKey, ad)
    }
}

data class SenderKeyRecord(
    val iteration: Int,
    val chainKey: ByteArray,
    val signaturePrivateKey: ByteArray, // Kept only for OUR sender key
    val signaturePublicKey: ByteArray,  // Shared with others
)

data class SenderKeyState(
    val senderId: ByteArray,
    var record: SenderKeyRecord,
)

data class GroupHeader(
    val iteration: Int,
    val signaturePublicKey: ByteArray,
) {
    fun toByteArray(): ByteArray {
        return iteration.toString().toByteArray() + signaturePublicKey
    }
}

data class GroupCiphertext(
    val nextSenderKeyRecord: SenderKeyRecord, // Returned so the sender can update their state
    val header: GroupHeader,
    val ciphertext: ByteArray,
    val nonce: ByteArray,
    val signature: ByteArray,
)
