package net.meshnet.feature.peers

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeersScreen() {
    val peers = listOf(
        PeerStub("Alice", "BLE", "Trust: 98%", "Active now", true),
        PeerStub("Bob", "Wi-Fi Direct", "Trust: 95%", "Active now", true),
        PeerStub("Charlie", "BLE", "Trust: 50%", "2m ago", false)
    )

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Mesh Network") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Mesh Visualization (Bonus Feature)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                MeshVisualizationCanvas()
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "Nearby Peers (${peers.size})",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            LazyColumn(
                modifier = Modifier.fillMaxWidth()
            ) {
                items(peers) { peer ->
                    PeerCard(peer)
                }
            }
        }
    }
}

@Composable
fun MeshVisualizationCanvas() {
    val infiniteTransition = rememberInfiniteTransition()
    
    // Animate a packet traveling between nodes
    val packetProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    Canvas(modifier = Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = 150f

        // 3 Nodes in a triangle
        val node1 = Offset(center.x, center.y - radius)
        val node2 = Offset(center.x - radius * 0.866f, center.y + radius * 0.5f)
        val node3 = Offset(center.x + radius * 0.866f, center.y + radius * 0.5f)

        // Draw links
        drawLine(color = onSurfaceColor.copy(alpha = 0.3f), start = node1, end = node2, strokeWidth = 2f)
        drawLine(color = onSurfaceColor.copy(alpha = 0.3f), start = node2, end = node3, strokeWidth = 2f)
        drawLine(color = primaryColor.copy(alpha = 0.5f), start = node1, end = node3, strokeWidth = 4f) // Active link

        // Draw nodes
        drawCircle(color = onSurfaceColor, radius = 20f, center = node1)
        drawCircle(color = onSurfaceColor, radius = 20f, center = node2)
        drawCircle(color = primaryColor, radius = 24f, center = node3) // "Us"

        // Draw packet traveling from node1 to node3
        val packetX = node1.x + (node3.x - node1.x) * packetProgress
        val packetY = node1.y + (node3.y - node1.y) * packetProgress
        drawCircle(color = primaryColor, radius = 8f, center = Offset(packetX, packetY))
    }
}

@Composable
fun PeerCard(peer: PeerStub) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = peer.alias,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = peer.transport,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(peer.trust, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(peer.lastSeen, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (peer.hasWifiDirect) {
                Text(
                    "Supports High-Speed Transfer (Wi-Fi Direct)",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Green.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

data class PeerStub(
    val alias: String,
    val transport: String,
    val trust: String,
    val lastSeen: String,
    val hasWifiDirect: Boolean
)
