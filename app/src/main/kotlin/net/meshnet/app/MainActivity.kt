package net.meshnet.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import net.meshnet.app.ui.theme.MeshNetTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MeshNetTheme {
                val navController = rememberNavController()
                
                // Temporary stub for identity check. In real implementation, 
                // this would be observed from IdentityManager/DataStore.
                val hasIdentity = false 
                
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        // Only show bottom bar if we are past onboarding
                        val navBackStackEntry by navController.currentBackStackEntryAsState()
                        val currentDestination = navBackStackEntry?.destination
                        val isRouteOnboarding = currentDestination?.route == "onboarding"
                        
                        if (!isRouteOnboarding) {
                            MeshBottomBar(navController)
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = if (hasIdentity) "chat" else "onboarding",
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable("onboarding") { OnboardingStub(navController) }
                        composable("chat") { ChatStub() }
                        composable("peers") { PeersStub() }
                        composable("transfers") { TransfersStub() }
                        composable("reports") { ReportsStub() }
                        composable("settings") { SettingsStub(navController) }
                        composable("developer_console") { DeveloperConsoleStub() }
                    }
                }
            }
        }
    }
}

@Composable
fun MeshBottomBar(navController: NavHostController) {
    val items = listOf(
        BottomNavItem("chat", "Chat", Icons.Filled.Send),
        BottomNavItem("peers", "Peers", Icons.Filled.Person),
        BottomNavItem("transfers", "Transfers", Icons.Filled.List),
        BottomNavItem("reports", "Reports", Icons.Filled.LocationOn),
        BottomNavItem("settings", "Settings", Icons.Filled.Settings),
    )
    
    NavigationBar {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination
        
        items.forEach { item ->
            NavigationBarItem(
                icon = { Icon(item.icon, contentDescription = item.title) },
                label = { Text(item.title) },
                selected = currentDestination?.hierarchy?.any { it.route == item.route } == true,
                onClick = {
                    navController.navigate(item.route) {
                        // Pop up to the start destination of the graph to avoid building up a large stack
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        // Avoid multiple copies of the same destination
                        launchSingleTop = true
                        // Restore state when reselecting a previously selected item
                        restoreState = true
                    }
                }
            )
        }
    }
}

data class BottomNavItem(val route: String, val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

// Stubs to allow compilation
@Composable fun OnboardingStub(navController: NavHostController) { Text("Onboarding Screen") }
@Composable fun ChatStub() { Text("Chat Screen") }
@Composable fun PeersStub() { Text("Peers Screen") }
@Composable fun TransfersStub() { Text("Transfers Screen") }
@Composable fun ReportsStub() { Text("Reports Feed Screen") }
@Composable fun SettingsStub(navController: NavHostController) { Text("Settings Screen") }
@Composable fun DeveloperConsoleStub() { Text("Developer Console Screen") }
