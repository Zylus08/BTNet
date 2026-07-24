package net.meshnet.core.storage.bloom

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.UUID

class SeenPacketBloomFilterTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var filter: SeenPacketBloomFilter

    @BeforeEach
    fun setup() {
        filter = SeenPacketBloomFilter(File(tempDir, "test_bloom.bin"))
    }

    @Test
    fun `mightContain returns false for unseen packet`() = runTest {
        val packetId = UUID.randomUUID().toString().toByteArray()
        assertFalse(filter.mightContain(packetId))
    }

    @Test
    fun `mightContain returns true after put`() = runTest {
        val packetId = UUID.randomUUID().toString().toByteArray()
        filter.put(packetId)
        assertTrue(filter.mightContain(packetId))
    }

    @Test
    fun `zero false negatives for 10000 insertions`() = runTest {
        val ids = (1..10_000).map { UUID.randomUUID().toString().toByteArray() }
        ids.forEach { filter.put(it) }
        ids.forEach { id ->
            assertTrue(filter.mightContain(id), "False negative detected — Bloom filter contract violated")
        }
    }

    @Test
    fun `false positive rate under 1 percent for 10000 random ids`() = runTest {
        // Insert 10,000 known IDs
        val inserted = (1..10_000).map { UUID.randomUUID().toString().toByteArray() }
        inserted.forEach { filter.put(it) }

        // Test 10,000 different IDs
        val falsePositives = (1..10_000).count {
            val fresh = UUID.randomUUID().toString().toByteArray()
            filter.mightContain(fresh)
        }

        val fpr = falsePositives.toDouble() / 10_000.0
        assertTrue(fpr < 0.01, "False positive rate $fpr exceeds 1%")
    }

    @Test
    fun `reset clears all entries`() = runTest {
        val packetId = UUID.randomUUID().toString().toByteArray()
        filter.put(packetId)
        assertTrue(filter.mightContain(packetId))

        filter.reset()
        assertFalse(filter.mightContain(packetId))
    }

    @Test
    fun `persist and reload restores filter state`() = runTest {
        val persistFile = File(tempDir, "bloom.bin")
        val f1 = SeenPacketBloomFilter(persistFile)
        val packetId = UUID.randomUUID().toString().toByteArray()
        f1.put(packetId)
        f1.persist()

        // Load from disk
        val f2 = SeenPacketBloomFilter(persistFile)
        assertTrue(f2.mightContain(packetId))
    }
}
