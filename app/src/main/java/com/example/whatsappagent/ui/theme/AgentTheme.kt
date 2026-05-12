package com.example.whatsappagent.ui.theme

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color

// ─────────────────────────────────────────────────────────────────────────────
// AgentColors — komplettes Farbsystem für alle 3 Themes
// ─────────────────────────────────────────────────────────────────────────────
data class AgentColors(
    val bg: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val border: Color,
    val borderHigh: Color,
    val accent: Color,
    val accentLow: Color,
    val green: Color,
    val greenLow: Color,
    val red: Color,
    val redLow: Color,
    val yellow: Color,
    val yellowLow: Color,
    val blue: Color,
    val purple: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val inputBg: Color,
)

enum class AgentTheme { DARK, LIGHT, AMOLED }

val DarkColors = AgentColors(
    bg           = Color(0xFF0A0A0F),
    surface      = Color(0xFF111118),
    surfaceHigh  = Color(0xFF17171F),
    border       = Color(0xFF1E1E2A),
    borderHigh   = Color(0xFF2A2A3A),
    accent       = Color(0xFF6366F1),
    accentLow    = Color(0x1F6366F1),
    green        = Color(0xFF22C55E),
    greenLow     = Color(0x2022C55E),
    red          = Color(0xFFEF4444),
    redLow       = Color(0x20EF4444),
    yellow       = Color(0xFFF59E0B),
    yellowLow    = Color(0x20F59E0B),
    blue         = Color(0xFF3B82F6),
    purple       = Color(0xFFA855F7),
    textPrimary  = Color(0xFFF1F1F5),
    textSecondary= Color(0xFF8888A8),
    textMuted    = Color(0xFF44445A),
    inputBg      = Color(0xFF0A0A0F),
)

val LightColors = AgentColors(
    bg           = Color(0xFFF4F4F8),
    surface      = Color(0xFFFFFFFF),
    surfaceHigh  = Color(0xFFF0F0F6),
    border       = Color(0xFFE0E0EA),
    borderHigh   = Color(0xFFC8C8D8),
    accent       = Color(0xFF4F46E5),
    accentLow    = Color(0x144F46E5),
    green        = Color(0xFF16A34A),
    greenLow     = Color(0x1A16A34A),
    red          = Color(0xFFDC2626),
    redLow       = Color(0x1ADC2626),
    yellow       = Color(0xFFD97706),
    yellowLow    = Color(0x1AD97706),
    blue         = Color(0xFF2563EB),
    purple       = Color(0xFF7C3AED),
    textPrimary  = Color(0xFF111118),
    textSecondary= Color(0xFF44445A),
    textMuted    = Color(0xFF9090A8),
    inputBg      = Color(0xFFF4F4F8),
)

val AmoledColors = AgentColors(
    bg           = Color(0xFF000000),
    surface      = Color(0xFF0D0D0D),
    surfaceHigh  = Color(0xFF111111),
    border       = Color(0xFF1A1A1A),
    borderHigh   = Color(0xFF222222),
    accent       = Color(0xFFA78BFA),
    accentLow    = Color(0x1FA78BFA),
    green        = Color(0xFF4ADE80),
    greenLow     = Color(0x204ADE80),
    red          = Color(0xFFF87171),
    redLow       = Color(0x20F87171),
    yellow       = Color(0xFFFBBF24),
    yellowLow    = Color(0x20FBBF24),
    blue         = Color(0xFF60A5FA),
    purple       = Color(0xFFC084FC),
    textPrimary  = Color(0xFFFFFFFF),
    textSecondary= Color(0xFF888888),
    textMuted    = Color(0xFF333333),
    inputBg      = Color(0xFF000000),
)

fun agentColors(theme: AgentTheme) = when (theme) {
    AgentTheme.DARK   -> DarkColors
    AgentTheme.LIGHT  -> LightColors
    AgentTheme.AMOLED -> AmoledColors
}

// CompositionLocal so alle Composables direkt auf C zugreifen können
val LocalAgentColors = staticCompositionLocalOf { DarkColors }
