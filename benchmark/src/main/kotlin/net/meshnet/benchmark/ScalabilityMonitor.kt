package net.meshnet.benchmark

import java.lang.management.ManagementFactory

class ScalabilityMonitor {
    
    private val memoryMXBean = ManagementFactory.getMemoryMXBean()
    private val runtime = Runtime.getRuntime()

    fun captureSnapshot(nodeCount: Int): ScalabilitySnapshot {
        // Force GC to get an accurate heap size, though in a real benchmark we'd just sample passively
        System.gc()
        
        val totalMemory = runtime.totalMemory()
        val freeMemory = runtime.freeMemory()
        val usedMemoryMb = (totalMemory - freeMemory) / (1024 * 1024L)
        
        // Simulating routing table and DB size
        val approxRoutingTableEntries = nodeCount * (nodeCount - 1)
        
        return ScalabilitySnapshot(
            nodeCount = nodeCount,
            usedMemoryMb = usedMemoryMb,
            routingTableSize = approxRoutingTableEntries
        )
    }
}

data class ScalabilitySnapshot(
    val nodeCount: Int,
    val usedMemoryMb: Long,
    val routingTableSize: Int
)
