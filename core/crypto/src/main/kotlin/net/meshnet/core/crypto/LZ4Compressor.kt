package net.meshnet.core.crypto

import net.jpountz.lz4.LZ4Factory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LZ4 block compression / decompression.
 *
 * LZ4 is chosen for its sub-millisecond latency on mobile hardware — critical
 * for the real-time mesh forwarding path. Compression ratio is secondary.
 *
 * Applies compression only when the compressed output is smaller than the input
 * (returns original bytes otherwise, with [CompressResult.compressed] = false).
 *
 * Wire format for compressed payloads:
 *   [4 bytes: original length as big-endian int32][N bytes: LZ4 block]
 *
 * This header is required by LZ4's block decompressor to allocate the output buffer.
 */
@Singleton
class LZ4Compressor @Inject constructor() {

    private val factory: LZ4Factory = LZ4Factory.fastestInstance()
    private val compressor by lazy { factory.fastCompressor() }
    private val decompressor by lazy { factory.fastDecompressor() }

    /**
     * Compresses [input] using LZ4 fast compression.
     *
     * @return [CompressResult] with compressed bytes if beneficial, or original bytes
     */
    fun compress(input: ByteArray): CompressResult {
        val maxCompressedLength = compressor.maxCompressedLength(input.size)
        val compressed = ByteArray(maxCompressedLength)
        val compressedLength = compressor.compress(input, 0, input.size, compressed, 0, maxCompressedLength)

        return if (compressedLength < input.size) {
            val payload = ByteArray(HEADER_BYTES + compressedLength)
            // Write original length as 4-byte big-endian header
            payload[0] = (input.size ushr 24).toByte()
            payload[1] = (input.size ushr 16).toByte()
            payload[2] = (input.size ushr 8).toByte()
            payload[3] = input.size.toByte()
            System.arraycopy(compressed, 0, payload, HEADER_BYTES, compressedLength)
            CompressResult(data = payload, compressed = true, originalSize = input.size)
        } else {
            CompressResult(data = input, compressed = false, originalSize = input.size)
        }
    }

    /**
     * Decompresses [input] that was compressed by [compress].
     *
     * @throws IllegalArgumentException if the header is malformed
     */
    fun decompress(input: ByteArray): ByteArray {
        require(input.size > HEADER_BYTES) { "Input too small to contain LZ4 header" }
        val originalSize = ((input[0].toInt() and 0xFF) shl 24) or
            ((input[1].toInt() and 0xFF) shl 16) or
            ((input[2].toInt() and 0xFF) shl 8) or
            (input[3].toInt() and 0xFF)
        require(originalSize in 1..MAX_DECOMPRESSED_BYTES) {
            "Invalid original size in LZ4 header: $originalSize"
        }
        val output = ByteArray(originalSize)
        decompressor.decompress(input, HEADER_BYTES, output, 0, originalSize)
        return output
    }

    companion object {
        private const val HEADER_BYTES = 4
        /** Guard against decompression bombs: max 16 MB per packet payload. */
        const val MAX_DECOMPRESSED_BYTES = 16 * 1024 * 1024
    }
}

data class CompressResult(
    val data: ByteArray,
    val compressed: Boolean,
    val originalSize: Int,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CompressResult) return false
        return data.contentEquals(other.data) &&
            compressed == other.compressed &&
            originalSize == other.originalSize
    }

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + compressed.hashCode()
        result = 31 * result + originalSize
        return result
    }
}
