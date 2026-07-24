package net.meshnet.benchmark

import java.io.File

class MarkdownReportGenerator {

    fun generateReport(results: List<BenchmarkResult>, outputFile: File) {
        val sb = java.lang.StringBuilder()
        sb.appendLine("# MeshNet Routing Benchmark Report")
        sb.appendLine("*Generated at: ${java.time.Instant.now()}*")
        sb.appendLine()
        
        sb.appendLine("## Summary Statistics")
        sb.appendLine("| Scenario | Nodes | Delivery Ratio | Avg Latency (ms) | Avg Hops | Duplicates | Bandwidth (MB) | Energy (J) |")
        sb.appendLine("|----------|-------|----------------|------------------|----------|------------|----------------|------------|")
        
        results.forEach { r ->
            val bandwidthMb = String.format("%.2f", r.bandwidthBytes / (1024.0 * 1024.0))
            val ratio = String.format("%.1f%%", r.deliveryRatio * 100)
            val hops = String.format("%.1f", r.averageHopCount)
            sb.appendLine("| ${r.scenarioName} | ${r.nodeCount} | $ratio | ${r.averageLatencyMs} | $hops | ${r.duplicatePackets} | $bandwidthMb | ${r.energyCostJoules} |")
        }
        
        sb.appendLine()
        sb.appendLine("## CSV Data")
        sb.appendLine("```csv")
        sb.appendLine("Scenario,Nodes,Duration,DeliveryRatio,AvgLatency,AvgHops,Duplicates,Bandwidth,Energy,Timestamp")
        results.forEach { r ->
            sb.appendLine("${r.scenarioName},${r.nodeCount},${r.durationSeconds},${r.deliveryRatio},${r.averageLatencyMs},${r.averageHopCount},${r.duplicatePackets},${r.bandwidthBytes},${r.energyCostJoules},${r.timestamp}")
        }
        sb.appendLine("```")

        outputFile.writeText(sb.toString())
    }
}
