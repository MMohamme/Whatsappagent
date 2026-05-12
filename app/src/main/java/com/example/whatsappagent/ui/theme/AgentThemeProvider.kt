package com.example.whatsappagent.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember

/**
 * Theme provider that sets up CompositionLocal for AgentColors
 * This eliminates the need to pass C parameter through all composables
 */
@Composable
fun AgentThemeProvider(
    theme: AgentTheme = AgentTheme.DARK,
    content: @Composable () -> Unit
) {
    val colors = remember(theme) { agentColors(theme) }
    
    CompositionLocalProvider(LocalAgentColors provides colors) {
        content()
    }
}

/**
 * Helper composable to get current AgentColors from CompositionLocal
 */
@Composable
fun useAgentColors(): AgentColors = LocalAgentColors.current
