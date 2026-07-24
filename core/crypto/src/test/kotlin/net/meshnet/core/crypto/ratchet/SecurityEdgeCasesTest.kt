package net.meshnet.core.crypto.ratchet

import io.mockk.mockk
import net.meshnet.core.crypto.CryptoEngine
import net.meshnet.core.crypto.KeyManager
import net.meshnet.core.crypto.TinkCryptoProvider
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.security.GeneralSecurityException

class SecurityEdgeCasesTest {

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
    fun `Tampered ciphertext throws exception`() {
        val sk = ByteArray(32) { 42 }
        val aliceDh = cryptoProvider.generateX25519KeyPair()
        val bobDh = cryptoProvider.generateX25519KeyPair()

        val (rkA, ckSa) = kdfChain.kdfRoot(sk, cryptoProvider.computeSharedSecret(aliceDh.privateKey, bobDh.publicKey))
        val aliceSession = DoubleRatchetSession(cryptoProvider, kdfChain, RatchetState(aliceDh, bobDh.publicKey, rkA, ckSa, null))
        val bobSession = DoubleRatchetSession(cryptoProvider, kdfChain, RatchetState(bobDh, null, sk, ByteArray(32), null))

        val encrypted = aliceSession.encrypt("Secret message".toByteArray())
        
        // Tamper with ciphertext (flip a bit)
        val tamperedCiphertext = encrypted.ciphertext.clone()
        tamperedCiphertext[0] = (tamperedCiphertext[0].toInt() xor 0x01).toByte()

        val tamperedMsg = encrypted.copy(ciphertext = tamperedCiphertext)

        assertThrows(GeneralSecurityException::class.java) {
            bobSession.decrypt(tamperedMsg)
        }
    }

    @Test
    fun `Replayed message with same message number throws exception`() {
        val sk = ByteArray(32) { 42 }
        val aliceDh = cryptoProvider.generateX25519KeyPair()
        val bobDh = cryptoProvider.generateX25519KeyPair()

        val (rkA, ckSa) = kdfChain.kdfRoot(sk, cryptoProvider.computeSharedSecret(aliceDh.privateKey, bobDh.publicKey))
        val aliceSession = DoubleRatchetSession(cryptoProvider, kdfChain, RatchetState(aliceDh, bobDh.publicKey, rkA, ckSa, null))
        val bobSession = DoubleRatchetSession(cryptoProvider, kdfChain, RatchetState(bobDh, null, sk, ByteArray(32), null))

        val encrypted = aliceSession.encrypt("Secret message".toByteArray())
        
        // Bob decrypts normally
        bobSession.decrypt(encrypted)

        // Replay attack: Eve sends the exact same message again
        // Bob should throw an exception because the message key is no longer in MKSKIPS
        assertThrows(GeneralSecurityException::class.java) {
            bobSession.decrypt(encrypted)
        }
    }
}
