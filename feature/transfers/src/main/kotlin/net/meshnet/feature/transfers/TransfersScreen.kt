package net.meshnet.feature.transfers

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransfersScreen() {
    val transfers = listOf(
        TransferStub("Map_Data_Region_A.mbtiles", "Receiving from Alice", 0.45f, "1.2 MB/s", "2m 10s left"),
        TransferStub("Medical_Supplies_List.pdf", "Sending to Bob", 0.95f, "450 KB/s", "10s left"),
        TransferStub("Emergency_Broadcast.mp4", "Queued (Waiting for Wi-Fi Direct)", 0.0f, "--", "--")
    )

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Transfers") })
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            items(transfers) { transfer ->
                TransferItem(transfer)
            }
        }
    }
}

@Composable
fun TransferItem(transfer: TransferStub) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(transfer.filename, style = MaterialTheme.typography.titleMedium)
        Text(transfer.status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        
        Spacer(modifier = Modifier.height(8.dp))
        
        LinearProgressIndicator(
            progress = transfer.progress,
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(transfer.speed, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
            Text(transfer.eta, style = MaterialTheme.typography.labelSmall)
        }
    }
}

data class TransferStub(
    val filename: String,
    val status: String,
    val progress: Float,
    val speed: String,
    val eta: String
)
