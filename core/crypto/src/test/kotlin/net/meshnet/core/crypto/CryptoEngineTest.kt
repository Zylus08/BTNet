package net.meshnet.core.crypto

import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.hybrid.HybridConfig
import com.google.crypto.tink.signature.SignatureConfig
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class CryptoEngineTest {

    private val engine = CryptoEngine()

    companion object {
        @JvmStatic
        @BeforeAll
        fun registerTink() {
            AeadConfig.register()
            HybridConfig.register()
            SignatureConfig.register()
        }
    }

    // ── AES-256-GCM ──────────────────────────────────────────────────────────

    @Test
    fun `encrypt then decrypt returns original plaintext`() {
        val key = ByteArray(32) { it.toByte() }
        val plaintext = "Hello, MeshNet!".toByteArray()
        val aad = "header".toByteArray()

        val result = engine.encrypt(plaintext, key, aad)
        val decrypted = engine.decrypt(result.ciphertext, key, aad)

        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun `nonce is 12 bytes`() {
        val key = ByteArray(32) { 0x42 }
        val result = engine.encrypt("test".toByteArray(), key)
        assertEquals(CryptoEngine.NONCE_BYTES, result.nonce.size)
    }

    @Test
    fun `different plaintexts produce different ciphertexts`() {
        val key = ByteArray(32) { 0x01 }
        val ct1 = engine.encrypt("message1".toByteArray(), key).ciphertext
        val ct2 = engine.encrypt("message2".toByteArray(), key).ciphertext
        assertFalse(ct1.contentEquals(ct2))
    }

    @Test
    fun `tampered ciphertext fails decryption`() {
        val key = ByteArray(32) { 0x55 }
        val plaintext = "sensitive data".toByteArray()
        val result = engine.encrypt(plaintext, key)
        val tampered = result.ciphertext.clone().also { it[it.size - 1] = it[it.size - 1].xor(0xFF.toByte()) }

        assertThrows<Exception> {
            engine.decrypt(tampered, key)
        }
    }

    @Test
    fun `wrong key fails decryption`() {
        val key1 = ByteArray(32) { 0xAA.toByte() }
        val key2 = ByteArray(32) { 0xBB.toByte() }
        val ct = engine.encrypt("secret".toByteArray(), key1).ciphertext

        assertThrows<Exception> {
            engine.decrypt(ct, key2)
        }
    }

    @Test
    fun `wrong aad fails decryption`() {
        val key = ByteArray(32) { 0x33 }
        val ct = engine.encrypt("data".toByteArray(), key, "aad1".toByteArray()).ciphertext

        assertThrows<Exception> {
            engine.decrypt(ct, key, "aad2".toByteArray())
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [0, 1, 16, 255, 1024, 65536])
    fun `encrypt-decrypt works for various plaintext sizes`(size: Int) {
        val key = ByteArray(32) { 0x7F }
        val plaintext = ByteArray(size) { it.toByte() }
        val result = engine.encrypt(plaintext, key)
        assertArrayEquals(plaintext, engine.decrypt(result.ciphertext, key))
    }

    // ── X25519 + HKDF ────────────────────────────────────────────────────────

    @Test
    fun `x25519 shared secret is symmetric`() {
        val alice = engine.generateX25519KeyPair()
        val bob = engine.generateX25519KeyPair()

        val aliceSecret = engine.x25519SharedSecret(alice.privateKey, bob.publicKey)
        val bobSecret = engine.x25519SharedSecret(bob.privateKey, alice.publicKey)

        assertArrayEquals(aliceSecret, bobSecret)
    }

    @Test
    fun `x25519 public key is 32 bytes`() {
        val kp = engine.generateX25519KeyPair()
        assertEquals(32, kp.publicKey.size)
    }

    @Test
    fun `x25519 different key pairs produce different secrets`() {
        val alice = engine.generateX25519KeyPair()
        val bob = engine.generateX25519KeyPair()
        val charlie = engine.generateX25519KeyPair()

        val aliceBob = engine.x25519SharedSecret(alice.privateKey, bob.publicKey)
        val aliceCharlie = engine.x25519SharedSecret(alice.privateKey, charlie.publicKey)

        assertFalse(aliceBob.contentEquals(aliceCharlie))
    }

    @Test
    fun `hkdf produces 32-byte output`() {
        val secret = ByteArray(32) { 0x42 }
        val salt = ByteArray(32) { 0x00 }
        val info = "meshnet-session-v1".toByteArray()

        val derived = engine.hkdf(secret, salt, info)
        assertEquals(32, derived.size)
    }

    @Test
    fun `hkdf is deterministic for same inputs`() {
        val secret = ByteArray(32) { 0x11 }
        val salt = ByteArray(32) { 0x22 }
        val info = "test".toByteArray()

        val key1 = engine.hkdf(secret, salt, info)
        val key2 = engine.hkdf(secret, salt, info)

        assertArrayEquals(key1, key2)
    }

    @Test
    fun `hkdf different info produces different keys`() {
        val secret = ByteArray(32) { 0x55 }
        val salt = ByteArray(32) { 0x00 }

        val key1 = engine.hkdf(secret, salt, "info1".toByteArray())
        val key2 = engine.hkdf(secret, salt, "info2".toByteArray())

        assertFalse(key1.contentEquals(key2))
    }

    // ── Ed25519 ──────────────────────────────────────────────────────────────

    @Test
    fun `sign then verify succeeds`() {
        val keypair = engine.generateEd25519KeyPair()
        val data = "authentic message".toByteArray()
        val signature = engine.sign(data, keypair.signer)
        engine.verify(data, signature, keypair.verifier) // must not throw
    }

    @Test
    fun `tampered data fails verification`() {
        val keypair = engine.generateEd25519KeyPair()
        val data = "authentic message".toByteArray()
        val signature = engine.sign(data, keypair.signer)
        val tampered = "different message".toByteArray()

        assertThrows<Exception> {
            engine.verify(tampered, signature, keypair.verifier)
        }
    }

    @Test
    fun `signature from different keypair fails verification`() {
        val alice = engine.generateEd25519KeyPair()
        val bob = engine.generateEd25519KeyPair()
        val data = "message".toByteArray()
        val aliceSig = engine.sign(data, alice.signer)

        assertThrows<Exception> {
            engine.verify(data, aliceSig, bob.verifier)
        }
    }

    // ── Full pipeline: X25519 → HKDF → AES-GCM ───────────────────────────────

    @Test
    fun `full key exchange and encrypt-decrypt pipeline`() {
        val alice = engine.generateX25519KeyPair()
        val bob = engine.generateX25519KeyPair()
        val salt = ByteArray(32)
        val info = "meshnet-session-v1".toByteArray()

        val aliceSession = engine.hkdf(
            engine.x25519SharedSecret(alice.privateKey, bob.publicKey), salt, info
        )
        val bobSession = engine.hkdf(
            engine.x25519SharedSecret(bob.privateKey, alice.publicKey), salt, info
        )

        assertArrayEquals(aliceSession, bobSession)

        val plaintext = "end-to-end encrypted".toByteArray()
        val encrypted = engine.encrypt(plaintext, aliceSession)
        val decrypted = engine.decrypt(encrypted.ciphertext, bobSession)

        assertArrayEquals(plaintext, decrypted)
    }
}
