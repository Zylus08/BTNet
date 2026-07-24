package net.meshnet.core.crypto

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class LZ4CompressorTest {

    private val compressor = LZ4Compressor()

    @Test
    fun `compress then decompress returns original`() {
        val original = ByteArray(1024) { it.toByte() }
        val result = compressor.compress(original)
        val decompressed = compressor.decompress(result.data)
        assertArrayEquals(original, decompressed)
    }

    @Test
    fun `compressible data marks compressed true`() {
        val repetitive = ByteArray(4096) { 0x42 } // highly compressible
        val result = compressor.compress(repetitive)
        assertTrue(result.compressed)
        assertTrue(result.data.size < repetitive.size)
    }

    @Test
    fun `incompressible data returns original bytes`() {
        // Random-looking data doesn't compress well
        val random = java.security.SecureRandom().let { rng ->
            ByteArray(256).also { rng.nextBytes(it) }
        }
        val result = compressor.compress(random)
        // compressed flag may be false; if so data == original
        if (!result.compressed) {
            assertArrayEquals(random, result.data)
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [1, 16, 255, 1024, 65536])
    fun `roundtrip for various sizes`(size: Int) {
        val data = ByteArray(size) { (it % 127).toByte() }
        val compressed = compressor.compress(data)
        val decompressed = if (compressed.compressed) {
            compressor.decompress(compressed.data)
        } else {
            compressed.data
        }
        assertArrayEquals(data, decompressed)
    }

    @Test
    fun `originalSize field matches input size`() {
        val data = ByteArray(512) { 0xAB.toByte() }
        val result = compressor.compress(data)
        assertEquals(512, result.originalSize)
    }

    @Test
    fun `decompress with malformed header throws`() {
        assertThrows<IllegalArgumentException> {
            compressor.decompress(ByteArray(3)) // too small for header
        }
    }

    @Test
    fun `decompress rejects oversized claimed original size`() {
        val malicious = ByteArray(8)
        // Write MAX_DECOMPRESSED_BYTES + 1 as big-endian int in first 4 bytes
        val evil = LZ4Compressor.MAX_DECOMPRESSED_BYTES + 1
        malicious[0] = (evil ushr 24).toByte()
        malicious[1] = (evil ushr 16).toByte()
        malicious[2] = (evil ushr 8).toByte()
        malicious[3] = evil.toByte()

        assertThrows<IllegalArgumentException> {
            compressor.decompress(malicious)
        }
    }
}
