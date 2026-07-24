package net.meshnet.feature.settings.diagnostics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperConsoleScreen(
    onNavigateUp: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Developer Console") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            MetricCard("Mesh Routing Engine", "State: ACTIVE\nBloom Filter: 12% full\nRouting Table: 15 entries")
            MetricCard("Network Latency (RTT)", "Avg RTT: 420ms\nJitter: 50ms\nCongestion Window: 12")
            MetricCard("Packets", "Sent: 1,402\nReceived: 5,910\nDropped: 14\nRelayed: 423")
            
            Text("Recent Packet Trace", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
            PacketLogTerminal()
        }
    }
}

@Composable
fun MetricCard(title: String, content: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(16.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Divider(modifier = Modifier.padding(vertical = 8.dp))
        Text(content, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun PacketLogTerminal() {
    val logs = listOf(
        "13:42:01 [BLE] RX <Packet(ID=a1b2, Type=ANNOUNCE)>",
        "13:42:05 [WIFI] TX <Packet(ID=c3d4, Type=FILE_CHUNK, size=4096)>",
        "13:42:06 [WIFI] RX <Packet(ID=c3d4, Type=ACK)>",
        "13:42:10 [CORE] Congestion Window increased to 13"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black, RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        logs.forEach { log ->
            Text(log, color = Color(0xFF00FF00), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
        }
    }
}
