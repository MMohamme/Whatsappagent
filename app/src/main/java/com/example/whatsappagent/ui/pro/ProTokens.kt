package com.example.whatsappagent.ui.pro

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.People
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.whatsappagent.ui.model.ContactCategory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class Screen(val label: String, val icon: ImageVector) {
    DASHBOARD("Home", Icons.Default.Home),
    QUEUE("Review", Icons.Default.ListAlt),
    CONTACTS("Kontakte", Icons.Default.People),
    EVENTS("Events", Icons.Default.Event),
    LOG("Logs", Icons.Default.Info),
}

data class ProColors(
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val outline: Color,
    val primary: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val info: Color,
    val text: Color,
    val textMuted: Color,
    val textSubtle: Color,
)

val ProDark = ProColors(
    background = Color(0xFF0B0F14),
    surface = Color(0xFF111820),
    surfaceRaised = Color(0xFF18212B),
    outline = Color(0xFF25313D),
    primary = Color(0xFF2DD4BF),
    success = Color(0xFF22C55E),
    warning = Color(0xFFF59E0B),
    danger = Color(0xFFEF4444),
    info = Color(0xFF60A5FA),
    text = Color(0xFFF3F7FA),
    textMuted = Color(0xFFA7B4C0),
    textSubtle = Color(0xFF6B7A88),
)

val ProLight = ProColors(
    background = Color(0xFFF4F7FA),
    surface = Color.White,
    surfaceRaised = Color(0xFFEAF0F5),
    outline = Color(0xFFD6E0E8),
    primary = Color(0xFF0F766E),
    success = Color(0xFF15803D),
    warning = Color(0xFFB45309),
    danger = Color(0xFFB91C1C),
    info = Color(0xFF2563EB),
    text = Color(0xFF101820),
    textMuted = Color(0xFF52616F),
    textSubtle = Color(0xFF8794A1),
)

fun statusColor(status: String, colors: ProColors): Color = when {
    status == "NEEDS_REVIEW" || status == "PREPARED" -> colors.warning
    status == "SEND_PENDING" || status == "SENDING" || status == "APPROVED" -> colors.info
    status == "SENT" || status == "DONE" -> colors.success
    status == "BLOCKED" || status == "CANCELLED" || status.contains("FAILED") -> colors.danger
    status == "DRAFT" -> colors.textSubtle
    else -> colors.primary
}

fun ContactCategory.toColor(colors: ProColors): Color = when (this) {
    ContactCategory.CORE_FAMILY, ContactCategory.EXTENDED_FAMILY -> colors.primary
    ContactCategory.FRIEND -> colors.info
    ContactCategory.WORK -> colors.warning
    ContactCategory.UNKNOWN -> colors.danger
}

fun String.shortStatus(): String = when (this) {
    "NEEDS_REVIEW" -> "REVIEW"
    "SEND_PENDING" -> "PENDING"
    "AUTO_SEND_ALLOWED" -> "AUTO"
    "PARTIAL_FAILED" -> "PARTIAL"
    else -> this
}

fun String.formatIsoTime(): String {
    return runCatching {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        val date = parser.parse(take(19)) ?: return@runCatching take(10)
        SimpleDateFormat("dd.MM. HH:mm", Locale.getDefault()).format(date)
    }.getOrDefault(take(16))
}

fun String.cleanUiText(): String =
    replace("Ã°Å¸â€œÂ¨", "")
        .replace("Ã¢Å“â€¦", "")
        .replace("Ã¢ÂÅ’", "")
        .replace("Ã°Å¸â€â€ž", "")
        .replace("Ã¢ÂÂ±", "")
        .trim()

fun Long.toIsoLocal(): String =
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date(this))

fun Long.toDisplayDate(): String =
    SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(this))
