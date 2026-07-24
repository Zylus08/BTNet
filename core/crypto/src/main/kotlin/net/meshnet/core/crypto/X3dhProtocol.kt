package net.meshnet.core.crypto

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implements the Extended Triple Diffie-Hellman (X3DH) key agreement protocol.
 * Used to establish a shared secret session key between two peers securely.
 */
@Singleton
class X3dhProtocol @Inject constructor(
    private val cryptoProvider: CryptoProvider,
) {

    /**
     * Alice initiates the X3DH handshake using Bob's Pre-Key Bundle.
     * 
     * @param bobBundle Bob's public keys.
     * @param aliceIdentityKeyPair Alice's long-term identity key pair (X25519 equivalent).
     *        Note: In Signal, Ed25519 keys are converted to X25519 for DH.
     *        For simplicity in this implementation, if our IK is strictly Ed25519,
     *        we might use a separate long-term X25519 key or perform the conversion.
     *        We assume [aliceIdentityPrivateKey] is a valid X25519 scalar.
     * @return [X3dhInitiationResult] containing the Shared Secret and Alice's Ephemeral Public Key to send to Bob.
     */
    fun initiate(
        bobBundle: PreKeyBundle,
        aliceIdentityPrivateKey: ByteArray,
    ): X3dhInitiationResult {
        // Verify Bob's Signed Pre-Key signature using his Identity Key
        val spkValid = cryptoProvider.verify(
            data = bobBundle.signedPreKey,
            signature = bobBundle.signedPreKeySignature,
            publicKey = bobBundle.identityKey
        )
        if (!spkValid) {
            throw SecurityException("Bob's Signed Pre-Key signature is invalid")
        }

        // Alice generates an Ephemeral Key Pair
        val aliceEphemeral = cryptoProvider.generateX25519KeyPair()

        // DH1 = DH(IK_A, SPK_B)
        val dh1 = cryptoProvider.computeSharedSecret(aliceIdentityPrivateKey, bobBundle.signedPreKey)
        // DH2 = DH(EK_A, IK_B)
        val dh2 = cryptoProvider.computeSharedSecret(aliceEphemeral.privateKey, bobBundle.identityKey)
        // DH3 = DH(EK_A, SPK_B)
        val dh3 = cryptoProvider.computeSharedSecret(aliceEphemeral.privateKey, bobBundle.signedPreKey)

        var km = dh1 + dh2 + dh3

        // DH4 = DH(EK_A, OPK_B) (if OPK present)
        if (bobBundle.oneTimePreKey != null) {
            val dh4 = cryptoProvider.computeSharedSecret(aliceEphemeral.privateKey, bobBundle.oneTimePreKey)
            km += dh4
        }

        // SK = KDF(F || KM) where F is 32 bytes of 0xFF
        val f = ByteArray(32) { 0xFF.toByte() }
        val inputKeyMaterial = f + km
        val salt = ByteArray(32) { 0 }
        
        val sharedSecret = cryptoProvider.hkdf(
            inputKeyMaterial = inputKeyMaterial,
            salt = salt,
            info = "MeshNetX3DH".toByteArray()
        )

        return X3dhInitiationResult(
            sharedSecret = sharedSecret,
            aliceEphemeralPublicKey = aliceEphemeral.publicKey,
            usedOneTimePreKeyId = bobBundle.oneTimePreKeyId
        )
    }

    /**
     * Bob completes the X3DH handshake when receiving Alice's initial message.
     */
    fun respond(
        aliceIdentityPublicKey: ByteArray,
        aliceEphemeralPublicKey: ByteArray,
        bobIdentityPrivateKey: ByteArray,
        bobSignedPreKeyPrivateKey: ByteArray,
        bobOneTimePreKeyPrivateKey: ByteArray?, // Null if Alice didn't use an OTPK
    ): ByteArray {
        // DH1 = DH(SPK_B, IK_A)
        val dh1 = cryptoProvider.computeSharedSecret(bobSignedPreKeyPrivateKey, aliceIdentityPublicKey)
        // DH2 = DH(IK_B, EK_A)
        val dh2 = cryptoProvider.computeSharedSecret(bobIdentityPrivateKey, aliceEphemeralPublicKey)
        // DH3 = DH(SPK_B, EK_A)
        val dh3 = cryptoProvider.computeSharedSecret(bobSignedPreKeyPrivateKey, aliceEphemeralPublicKey)

        var km = dh1 + dh2 + dh3

        // DH4 = DH(OPK_B, EK_A)
        if (bobOneTimePreKeyPrivateKey != null) {
            val dh4 = cryptoProvider.computeSharedSecret(bobOneTimePreKeyPrivateKey, aliceEphemeralPublicKey)
            km += dh4
        }

        val f = ByteArray(32) { 0xFF.toByte() }
        val inputKeyMaterial = f + km
        val salt = ByteArray(32) { 0 }

        return cryptoProvider.hkdf(
            inputKeyMaterial = inputKeyMaterial,
            salt = salt,
            info = "MeshNetX3DH".toByteArray()
        )
    }
}

data class X3dhInitiationResult(
    val sharedSecret: ByteArray,
    val aliceEphemeralPublicKey: ByteArray,
    val usedOneTimePreKeyId: Int?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is X3dhInitiationResult) return false
        return sharedSecret.contentEquals(other.sharedSecret) &&
               aliceEphemeralPublicKey.contentEquals(other.aliceEphemeralPublicKey) &&
               usedOneTimePreKeyId == other.usedOneTimePreKeyId
    }

    override fun hashCode(): Int {
        var result = sharedSecret.contentHashCode()
        result = 31 * result + aliceEphemeralPublicKey.contentHashCode()
        result = 31 * result + (usedOneTimePreKeyId ?: 0)
        return result
    }
}
