package net.meshnet.feature.maps

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    viewModel: MapViewModel = viewModel()
) {
    val state by viewModel.mapState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Situational Awareness") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { /* Open Report Creation */ }) {
                Icon(Icons.Default.Warning, contentDescription = "New Report")
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            
            // MapLibre Stub (Would normally be AndroidView rendering maplibre-android)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "MapLibre Offline Vector Map Rendered Here\n" +
                            "Active Layers: ${state.activeLayers.joinToString { it.name }}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Layer Toggles
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MapLayer.entries.forEach { layer ->
                    FilterChip(
                        selected = state.activeLayers.contains(layer),
                        onClick = { viewModel.toggleLayer(layer) },
                        label = { Text(layer.name.lowercase().capitalize()) }
                    )
                }
            }
            
            // Timeline Scrubber Stub (Phase 7.2)
            // Placed at bottom, above FAB
        }
    }
}
