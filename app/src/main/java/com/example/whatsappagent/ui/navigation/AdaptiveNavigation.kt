package com.example.whatsappagent.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.material3.windowsizeclass.*
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.whatsappagent.ui.theme.AgentColors

/**
 * Adaptive navigation that works on both phones and tablets
 * Uses WindowSizeClass to determine layout
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdaptiveNavigation(
    navController: NavController,
    windowSizeClass: WindowSizeClass,
    C: AgentColors,
    content: @Composable (PaddingValues) -> Unit
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    
    when (windowSizeClass.widthSizeClass) {
        WindowWidthSizeClass.Compact -> {
            // Phone layout: Bottom navigation
            PhoneNavigationLayout(
                currentRoute = currentRoute,
                onNavigate = { route -> navController.navigate(route) },
                C = C,
                content = content
            )
        }
        WindowWidthSizeClass.Medium, WindowWidthSizeClass.Expanded -> {
            // Tablet layout: Navigation rail or drawer
            TabletNavigationLayout(
                currentRoute = currentRoute,
                onNavigate = { route -> navController.navigate(route) },
                C = C,
                content = content
            )
        }
    }
}

/**
 * Phone navigation with bottom navigation bar
 */
@Composable
private fun PhoneNavigationLayout(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    C: AgentColors,
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = C.surface,
                contentColor = C.textPrimary
            ) {
                NavigationItems.forEach { item ->
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.title) },
                        label = { Text(item.title) },
                        selected = currentRoute == item.route,
                        onClick = { onNavigate(item.route) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = C.accent,
                            selectedTextColor = C.accent,
                            indicatorColor = C.accent.copy(alpha = 0.2f)
                        )
                    )
                }
            }
        }
    ) { paddingValues ->
        content(paddingValues)
    }
}

/**
 * Tablet navigation with navigation rail
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TabletNavigationLayout(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    C: AgentColors,
    content: @Composable (PaddingValues) -> Unit
) {
    PermanentNavigationDrawer(
        drawerContent = {
            NavigationDrawer(
                currentRoute = currentRoute,
                onNavigate = onNavigate,
                C = C
            )
        }
    ) {
        content(PaddingValues(0.dp))
    }
}

/**
 * Navigation drawer content for tablets
 */
@Composable
private fun NavigationDrawer(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    C: AgentColors
) {
    ModalDrawerSheet(
        modifier = Modifier.width(280.dp),
        drawerContainerColor = C.surface,
        drawerContentColor = C.textPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "WhatsApp Agent",
                style = MaterialTheme.typography.headlineSmall,
                color = C.textPrimary,
                modifier = Modifier.padding(bottom = 24.dp)
            )
            
            NavigationItems.forEach { item ->
                NavigationDrawerItem(
                    icon = { Icon(item.icon, contentDescription = item.title) },
                    label = { Text(item.title) },
                    selected = currentRoute == item.route,
                    onClick = { onNavigate(item.route) },
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedIconColor = C.accent,
                        selectedTextColor = C.accent,
                        selectedContainerColor = C.accent.copy(alpha = 0.1f)
                    )
                )
            }
        }
    }
}

/**
 * Navigation items configuration
 */
private data class NavigationItem(
    val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val route: String
)

private val NavigationItems = listOf(
    NavigationItem(
        title = "Dashboard",
        icon = Icons.Filled.Dashboard,
        route = "dashboard"
    ),
    NavigationItem(
        title = "Kontakte",
        icon = Icons.Filled.People,
        route = "contacts"
    ),
    NavigationItem(
        title = "Events",
        icon = Icons.Filled.Event,
        route = "events"
    ),
    NavigationItem(
        title = "Queue",
        icon = Icons.Filled.List,
        route = "queue"
    ),
    NavigationItem(
        title = "Chat",
        icon = Icons.Filled.Chat,
        route = "chat"
    ),
    NavigationItem(
        title = "Log",
        icon = Icons.Filled.Article,
        route = "log"
    )
)
