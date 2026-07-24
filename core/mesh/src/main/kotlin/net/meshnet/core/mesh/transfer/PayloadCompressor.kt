package net.meshnet.core.mesh.transfer

import net.jpountz.lz4.LZ4Factory
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Compresses and decompresses payload data using LZ4.
 * 
 * LZ4 is chosen because it provides extremely fast decompression and fast compression,
 * which is ideal for battery-constrained Android devices on mesh networks where 
 * processing speed is often a bigger bottleneck than bandwidth.
 */
@Singleton
class PayloadCompressor @Inject constructor() {
    
    // Fallback/Mock implementation if lz4-java is not in the classpath
    private val lz4Factory: LZ4Factory? = try {
        LZ4Factory.fastestInstance()
    } catch (e: NoClassDefFoundError) {
        Timber.w("LZ4 library not found on classpath. Compression disabled.")
        null
    }

    /**
     * Compresses the given [plaintext] using LZ4.
     * @return the compressed byte array. If compression is unsupported, returns original array.
     */
    fun compress(plaintext: ByteArray): ByteArray {
        if (lz4Factory == null || plaintext.isEmpty()) return plaintext
        
        return try {
            val compressor = lz4Factory.fastCompressor()
            val maxCompressedLength = compressor.maxCompressedLength(plaintext.size)
            val compressed = ByteArray(maxCompressedLength + 4) // +4 bytes to store original length
            
            // Store original length in first 4 bytes (Little Endian)
            val originalLength = plaintext.size
            compressed[0] = (originalLength and 0xFF).toByte()
            compressed[1] = ((originalLength shr 8) and 0xFF).toByte()
            compressed[2] = ((originalLength shr 16) and 0xFF).toByte()
            compressed[3] = ((originalLength shr 24) and 0xFF).toByte()

            val compressedLength = compressor.compress(plaintext, 0, plaintext.size, compressed, 4, maxCompressedLength)
            
            compressed.copyOfRange(0, compressedLength + 4)
        } catch (e: Exception) {
            Timber.e(e, "Compression failed")
            plaintext
        }
    }

    /**
     * Decompresses the given [compressed] array using LZ4.
     */
    fun decompress(compressed: ByteArray): ByteArray {
        if (lz4Factory == null || compressed.size <= 4) return compressed
        
        return try {
            val decompressor = lz4Factory.fastDecompressor()
            
            // Read original length from first 4 bytes (Little Endian)
            val originalLength = (compressed[0].toInt() and 0xFF) or
                    ((compressed[1].toInt() and 0xFF) shl 8) or
                    ((compressed[2].toInt() and 0xFF) shl 16) or
                    ((compressed[3].toInt() and 0xFF) shl 24)

            val restored = ByteArray(originalLength)
            decompressor.decompress(compressed, 4, restored, 0, originalLength)
            restored
        } catch (e: Exception) {
            Timber.e(e, "Decompression failed")
            compressed
        }
    }
}
