package net.meshnet.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToDeveloperConsole: () -> Unit
) {
    var versionTapCount by remember { mutableIntStateOf(0) }
    var backgroundEnabled by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Settings") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            ListItem(
                headlineContent = { Text("Run in Background") },
                supportingContent = { Text("Keep mesh active when app is closed") },
                trailingContent = {
                    Switch(
                        checked = backgroundEnabled,
                        onCheckedChange = { backgroundEnabled = it }
                    )
                }
            )
            Divider()
            
            ListItem(
                headlineContent = { Text("Identity Backup") },
                supportingContent = { Text("Export your identity key as a QR code") },
                modifier = Modifier.clickable { /* Show QR code */ }
            )
            Divider()
            
            ListItem(
                headlineContent = { Text("Battery Optimization") },
                supportingContent = { Text("Manage Android battery restrictions") },
                modifier = Modifier.clickable { /* Launch OS intent */ }
            )
            Divider()

            ListItem(
                headlineContent = { Text("Version") },
                supportingContent = { Text("1.0.0 (Build 42)") },
                modifier = Modifier.clickable {
                    versionTapCount++
                    if (versionTapCount >= 7) {
                        versionTapCount = 0
                        onNavigateToDeveloperConsole()
                    }
                }
            )
        }
    }
}
