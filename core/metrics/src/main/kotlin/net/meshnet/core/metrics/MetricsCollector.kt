package net.meshnet.core.metrics

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.meshnet.core.events.EventBus
import net.meshnet.core.events.MeshEvent
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Aggregates runtime statistics and diagnostics for the hidden Developer Console.
 * Acts like a "Wireshark for MeshNet".
 */
@Singleton
class MetricsCollector @Inject constructor(
    private val eventBus: EventBus
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _snapshot = MutableStateFlow(MetricsSnapshot())
    val snapshot = _snapshot.asStateFlow()

    // Circular buffer for recent packet traces
    private val packetTraceBuffer = ConcurrentLinkedQueue<MeshEvent.PacketTraced>()
    private val MAX_TRACE_ENTRIES = 1000

    init {
        scope.launch {
            eventBus.on<MeshEvent.PacketTraced>().collect { trace ->
                packetTraceBuffer.add(trace)
                if (packetTraceBuffer.size > MAX_TRACE_ENTRIES) {
                    packetTraceBuffer.poll() // Remove oldest
                }
            }
        }

        scope.launch {
            eventBus.on<MeshEvent.PacketSent>().collect {
                _snapshot.update { it.copy(packetsSent = it.packetsSent + 1) }
            }
        }

        scope.launch {
            eventBus.on<MeshEvent.PacketReceived>().collect {
                _snapshot.update { it.copy(packetsReceived = it.packetsReceived + 1) }
            }
        }

        scope.launch {
            eventBus.on<MeshEvent.PacketDropped>().collect {
                _snapshot.update { it.copy(packetsDropped = it.packetsDropped + 1) }
            }
        }
        
        scope.launch {
            eventBus.on<MeshEvent.DeliveryAcknowledged>().collect { ack ->
                _snapshot.update { 
                    it.copy(
                        averageRttMs = calculateMovingAverage(it.averageRttMs, ack.latencyMs),
                        successfulDeliveries = it.successfulDeliveries + 1
                    )
                }
            }
        }
    }

    /** Returns the recent packet trace history for the Developer Console. */
    fun getRecentTraces(): List<MeshEvent.PacketTraced> {
        return packetTraceBuffer.toList()
    }

    private fun calculateMovingAverage(currentAvg: Long, newValue: Long): Long {
        if (currentAvg == 0L) return newValue
        // Simple Exponential Moving Average
        return (currentAvg * 0.9 + newValue * 0.1).toLong()
    }
}

data class MetricsSnapshot(
    val packetsSent: Long = 0,
    val packetsReceived: Long = 0,
    val packetsDropped: Long = 0,
    val successfulDeliveries: Long = 0,
    val averageRttMs: Long = 0,
    val activeTransports: Int = 0,
    val bloomFilterOccupancyPercent: Float = 0f
)
