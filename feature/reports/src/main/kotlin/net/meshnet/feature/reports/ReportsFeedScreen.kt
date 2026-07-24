package net.meshnet.feature.reports

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
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsFeedScreen() {
    val reports = listOf(
        ReportStub("ROAD_BLOCKED", "Fallen tree blocking main highway", 0.95f, "5 mins ago", 4),
        ReportStub("MEDICAL", "Need first aid kit at camp", 0.60f, "12 mins ago", 1),
        ReportStub("WATER", "Clean water distribution point", 0.99f, "1 hour ago", 15)
    )

    // Timeline Scrubber state (0.0 = Now, 1.0 = 24 hours ago)
    var timelinePosition by remember { mutableFloatStateOf(0f) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Community Reports") })
        },
        bottomBar = {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Timeline: ${if (timelinePosition == 0f) "Now" else "${(timelinePosition * 24).toInt()} hours ago"}",
                    style = MaterialTheme.typography.labelMedium
                )
                Slider(
                    value = timelinePosition,
                    onValueChange = { timelinePosition = it }
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            items(reports) { report ->
                ReportCard(report)
            }
        }
    }
}

@Composable
fun ReportCard(report: ReportStub) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = report.category,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = report.timeAgo,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(report.description, style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Confidence: ${(report.confidence * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "Witnesses: ${report.witnessCount}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

data class ReportStub(
    val category: String,
    val description: String,
    val confidence: Float,
    val timeAgo: String,
    val witnessCount: Int
)
