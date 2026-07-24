package net.meshnet.simulator

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.random.Random

/**
 * Headless execution engine for testing MeshNet at scale (10 to 1,000+ nodes).
 * Uses a fixed random seed for deterministic, reproducible benchmarks.
 */
class SimulatorEngine(
    private val config: SimulatorConfig
) {
    private val random = Random(config.seed)
    
    private val _nodes = MutableStateFlow<List<SimNode>>(emptyList())
    val nodes = _nodes.asStateFlow()
    
    private var isRunning = false

    fun initialize() {
        val initialNodes = List(config.nodeCount) { id ->
            SimNode(
                id = "node_$id",
                x = random.nextDouble(0.0, config.areaWidth),
                y = random.nextDouble(0.0, config.areaHeight),
                batteryLevel = 1.0f,
                isAlive = true
            )
        }
        _nodes.update { initialNodes }
    }

    suspend fun tick(deltaTimeSeconds: Float) {
        if (!isRunning) return
        
        _nodes.update { currentNodes ->
            currentNodes.map { node ->
                if (!node.isAlive) return@map node
                
                // 1. Apply Mobility Model
                val newPos = config.mobilityModel.calculateNextPosition(
                    node.x, node.y, deltaTimeSeconds, random
                )
                
                // 2. Apply Failures
                val isAlive = !config.failureInjector.shouldCrash(random)
                val battery = config.failureInjector.drainBattery(node.batteryLevel, deltaTimeSeconds)
                
                node.copy(
                    x = newPos.first,
                    y = newPos.second,
                    isAlive = isAlive && battery > 0f,
                    batteryLevel = battery
                )
            }
        }
    }

    fun start() { isRunning = true }
    fun stop() { isRunning = false }
}

data class SimulatorConfig(
    val nodeCount: Int = 100,
    val areaWidth: Double = 1000.0,
    val areaHeight: Double = 1000.0,
    val seed: Long = 42L,
    val mobilityModel: MobilityModel,
    val failureInjector: FailureInjector
)

data class SimNode(
    val id: String,
    val x: Double,
    val y: Double,
    val batteryLevel: Float,
    val isAlive: Boolean
)
