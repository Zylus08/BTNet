package net.meshnet.simulator.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay
import net.meshnet.simulator.RandomWaypointMobility
import net.meshnet.simulator.SimulatorConfig
import net.meshnet.simulator.SimulatorEngine
import net.meshnet.simulator.StandardFailureInjector

@Composable
fun MeshVisualizerScreen() {
    val engine = remember { 
        SimulatorEngine(
            SimulatorConfig(
                nodeCount = 250,
                areaWidth = 800.0,
                areaHeight = 600.0,
                mobilityModel = RandomWaypointMobility(areaWidth = 800.0, areaHeight = 600.0),
                failureInjector = StandardFailureInjector()
            )
        ).apply { 
            initialize()
            start()
        }
    }
    
    val nodes by engine.nodes.collectAsState()

    // Engine loop
    LaunchedEffect(Unit) {
        while (true) {
            engine.tick(0.016f) // ~60 FPS
            delay(16)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.DarkGray)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Draw nodes
            nodes.forEach { node ->
                val color = if (node.isAlive) Color.Green else Color.Red
                drawCircle(
                    color = color,
                    radius = 4f,
                    center = Offset(node.x.toFloat(), node.y.toFloat())
                )
            }
            
            // Draw links (simplified O(N^2) proximity check for visualization)
            // In a real optimized view, we'd use a quadtree or let the engine output the active links
            val rangeSq = 50f * 50f
            for (i in nodes.indices) {
                if (!nodes[i].isAlive) continue
                for (j in i + 1 until nodes.size) {
                    if (!nodes[j].isAlive) continue
                    
                    val dx = nodes[i].x - nodes[j].x
                    val dy = nodes[i].y - nodes[j].y
                    if (dx*dx + dy*dy < rangeSq) {
                        drawLine(
                            color = Color.Cyan.copy(alpha = 0.3f),
                            start = Offset(nodes[i].x.toFloat(), nodes[i].y.toFloat()),
                            end = Offset(nodes[j].x.toFloat(), nodes[j].y.toFloat()),
                            strokeWidth = 1f
                        )
                    }
                }
            }
        }
    }
}
