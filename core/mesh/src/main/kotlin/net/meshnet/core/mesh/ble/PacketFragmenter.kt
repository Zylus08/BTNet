package net.meshnet.core.mesh.ble

import java.nio.ByteBuffer

/**
 * Handles segmentation and reassembly of large byte arrays over BLE GATT.
 * 
 * BLE MTU limits characteristic writes/notifications. While GATT long writes exist,
 * they are notoriously unreliable across Android vendors. This provides a robust
 * application-layer fragmentation protocol.
 *
 * Header (4 bytes):
 *   Byte 0: Flags (0x01 = START, 0x02 = END, 0x03 = START+END, 0x00 = MIDDLE)
 *   Byte 1: Sequence number (0-255)
 *   Byte 2-3: Transfer ID (random 16-bit, groups chunks of the same packet)
 */
object PacketFragmenter {

    const val FLAG_MIDDLE: Byte = 0x00
    const val FLAG_START: Byte = 0x01
    const val FLAG_END: Byte = 0x02
    const val FLAG_SINGLE: Byte = 0x03 // START | END

    const val HEADER_SIZE = 4

    /**
     * Splits a raw byte array into chunks that fit within [maxPayloadSize].
     * [maxPayloadSize] should be (MTU - 3) - [HEADER_SIZE].
     */
    fun fragment(data: ByteArray, transferId: Short, maxPayloadSize: Int): List<ByteArray> {
        require(maxPayloadSize > 0) { "Max payload size must be > 0" }

        if (data.isEmpty()) return emptyList()

        if (data.size <= maxPayloadSize) {
            val chunk = ByteArray(HEADER_SIZE + data.size)
            val buffer = ByteBuffer.wrap(chunk)
            buffer.put(FLAG_SINGLE)
            buffer.put(0) // seq = 0
            buffer.putShort(transferId)
            buffer.put(data)
            return listOf(chunk)
        }

        val chunks = mutableListOf<ByteArray>()
        var offset = 0
        var seq = 0

        while (offset < data.size) {
            val remaining = data.size - offset
            val chunkSize = minOf(remaining, maxPayloadSize)
            
            val flag: Byte = when {
                offset == 0 -> FLAG_START
                offset + chunkSize >= data.size -> FLAG_END
                else -> FLAG_MIDDLE
            }

            val chunk = ByteArray(HEADER_SIZE + chunkSize)
            val buffer = ByteBuffer.wrap(chunk)
            buffer.put(flag)
            buffer.put(seq.toByte())
            buffer.putShort(transferId)
            buffer.put(data, offset, chunkSize)

            chunks.add(chunk)
            offset += chunkSize
            seq = (seq + 1) % 256
        }

        return chunks
    }

    /**
     * Parses a chunk header.
     */
    fun parseHeader(chunk: ByteArray): ChunkHeader {
        require(chunk.size >= HEADER_SIZE) { "Chunk too small to contain header" }
        val buffer = ByteBuffer.wrap(chunk)
        return ChunkHeader(
            flags = buffer.get(),
            seq = buffer.get().toInt() and 0xFF,
            transferId = buffer.short
        )
    }

    data class ChunkHeader(
        val flags: Byte,
        val seq: Int,
        val transferId: Short
    ) {
        val isStart get() = (flags.toInt() and FLAG_START.toInt()) != 0
        val isEnd get() = (flags.toInt() and FLAG_END.toInt()) != 0
    }
}
