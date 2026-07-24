package net.meshnet.core.crypto.ratchet

import io.mockk.every
import io.mockk.mockk
import net.meshnet.core.crypto.CryptoEngine
import net.meshnet.core.crypto.KeyManager
import net.meshnet.core.crypto.TinkCryptoProvider
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.security.SecureRandom

class DoubleRatchetTest {

    private lateinit var cryptoProvider: TinkCryptoProvider
    private lateinit var kdfChain: KdfChain

    @BeforeEach
    fun setup() {
        val engine = CryptoEngine()
        val mockKeyManager = mockk<KeyManager>(relaxed = true)
        cryptoProvider = TinkCryptoProvider(engine, mockKeyManager)
        kdfChain = KdfChain(cryptoProvider)
    }

    @Test
    fun `Ping pong messages decrypt correctly`() {
        // Shared secret from X3DH
        val sk = ByteArray(32) { 42 }
        
        // Alice starts with Bob's DH Public Key
        val aliceDh = cryptoProvider.generateX25519KeyPair()
        val bobDh = cryptoProvider.generateX25519KeyPair()

        // Alice's Session (Initiator)
        val (rkA, ckSa) = kdfChain.kdfRoot(sk, cryptoProvider.computeSharedSecret(aliceDh.privateKey, bobDh.publicKey))
        val aliceState = RatchetState(
            DHs = aliceDh,
            DHr = bobDh.publicKey,
            RK = rkA,
            CKs = ckSa,
            CKr = null
        )
        val aliceSession = DoubleRatchetSession(cryptoProvider, kdfChain, aliceState)

        // Bob's Session (Responder)
        val bobState = RatchetState(
            DHs = bobDh,
            DHr = null, // Will be set on first message
            RK = sk,
            CKs = ByteArray(32), // Will be set on first reply
            CKr = null
        )
        val bobSession = DoubleRatchetSession(cryptoProvider, kdfChain, bobState)

        // Alice sends message to Bob
        val msg1 = "Hello Bob".toByteArray()
        val encrypted1 = aliceSession.encrypt(msg1)
        val decrypted1 = bobSession.decrypt(encrypted1)
        assertArrayEquals(msg1, decrypted1)

        // Bob replies to Alice
        val msg2 = "Hi Alice".toByteArray()
        val encrypted2 = bobSession.encrypt(msg2)
        val decrypted2 = aliceSession.decrypt(encrypted2)
        assertArrayEquals(msg2, decrypted2)
        
        // Alice sends another
        val msg3 = "How are you?".toByteArray()
        val encrypted3 = aliceSession.encrypt(msg3)
        val decrypted3 = bobSession.decrypt(encrypted3)
        assertArrayEquals(msg3, decrypted3)
    }

    @Test
    fun `Out of order messages decrypt correctly using skipped keys`() {
        // Setup shared state (omitted full init for brevity, assuming Alice sends 3 messages)
        val sk = ByteArray(32) { 42 }
        val aliceDh = cryptoProvider.generateX25519KeyPair()
        val bobDh = cryptoProvider.generateX25519KeyPair()

        val (rkA, ckSa) = kdfChain.kdfRoot(sk, cryptoProvider.computeSharedSecret(aliceDh.privateKey, bobDh.publicKey))
        
        val aliceSession = DoubleRatchetSession(cryptoProvider, kdfChain, RatchetState(aliceDh, bobDh.publicKey, rkA, ckSa, null))
        val bobSession = DoubleRatchetSession(cryptoProvider, kdfChain, RatchetState(bobDh, null, sk, ByteArray(32), null))

        // Alice encrypts 3 messages
        val m1 = aliceSession.encrypt("Msg 1".toByteArray())
        val m2 = aliceSession.encrypt("Msg 2".toByteArray())
        val m3 = aliceSession.encrypt("Msg 3".toByteArray())

        // Bob receives m3 FIRST
        val d3 = bobSession.decrypt(m3)
        assertEquals("Msg 3", String(d3))

        // Bob then receives m1
        val d1 = bobSession.decrypt(m1)
        assertEquals("Msg 1", String(d1))

        // Bob then receives m2
        val d2 = bobSession.decrypt(m2)
        assertEquals("Msg 2", String(d2))
    }
}
