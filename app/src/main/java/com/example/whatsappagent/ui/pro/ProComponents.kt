package com.example.whatsappagent.ui.pro

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.whatsappagent.AgentLogger
import com.example.whatsappagent.data.remote.EventTicketResponse
import com.example.whatsappagent.ui.model.UiContact
import java.util.Locale

@Composable
fun ProTopBar(
    screen: Screen,
    backendConnected: Boolean,
    autoPaused: Boolean,
    notificationEnabled: Boolean,
    contactsEnabled: Boolean,
    accessibilityEnabled: Boolean,
    a11yFallback: Boolean,
    onTogglePause: (Boolean) -> Unit,
    onToggleFallback: (Boolean) -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenAccessibility: () -> Unit,
    colors: ProColors,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(screen.icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(screen.label, color = colors.text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text("Pro Control Center", color = colors.textMuted, fontSize = 11.sp)
            }
            IconButton(onClick = onOpenNotifications, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Default.Notifications, contentDescription = "Notification settings", tint = if (notificationEnabled) colors.success else colors.warning)
            }
            IconButton(onClick = onOpenAccessibility, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Default.Accessibility, contentDescription = "Accessibility settings", tint = if (accessibilityEnabled) colors.success else colors.textSubtle)
            }
        }
        Spacer(Modifier.height(7.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            StatusChip("Backend", backendConnected, colors)
            StatusChip("Contacts", contactsEnabled, colors)
            StatusChip("NLS", notificationEnabled, colors)
            ToggleLine(
                label = if (autoPaused) "Auto paused" else "Auto live",
                checked = autoPaused,
                onChecked = onTogglePause,
                color = if (autoPaused) colors.warning else colors.success,
                modifier = Modifier.width(132.dp),
                colors = colors
            )
            ToggleLine(
                label = "A11y fallback",
                checked = a11yFallback,
                onChecked = onToggleFallback,
                color = if (a11yFallback) colors.info else colors.textSubtle,
                modifier = Modifier.width(142.dp),
                colors = colors,
                enabled = accessibilityEnabled
            )
        }
    }
}

@Composable
fun BottomNav(current: Screen, reviewCount: Int, onSelect: (Screen) -> Unit, colors: ProColors) {
    BottomAppBar(containerColor = colors.surface, contentColor = colors.text) {
        Screen.entries.forEach { item ->
            NavigationBarItem(
                selected = current == item,
                onClick = { onSelect(item) },
                icon = {
                    Box {
                        Icon(item.icon, contentDescription = item.label)
                        if (item == Screen.QUEUE && reviewCount > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(colors.warning)
                            )
                        }
                    }
                },
                label = { Text(item.label, fontSize = 10.sp, maxLines = 1) }
            )
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onRefresh: () -> Unit,
    colors: ProColors,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.primary.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = colors.text, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            Text(subtitle, color = colors.textMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (actionLabel != null && onAction != null) {
            Button(
                onClick = onAction,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text(actionLabel, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        IconButton(onClick = onRefresh, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = colors.textMuted)
        }
    }
}

@Composable
fun MetricTile(label: String, value: String, color: Color, icon: ImageVector, colors: ProColors, modifier: Modifier = Modifier) {
    ProCard(colors, modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Column {
                Text(value, color = color, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                Text(label.uppercase(Locale.getDefault()), color = colors.textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun ProCard(
    colors: ProColors,
    modifier: Modifier = Modifier,
    borderColor: Color = colors.outline,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surface)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .padding(12.dp),
        content = content
    )
}

@Composable
fun HealthLine(label: String, ok: Boolean, colors: ProColors) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Icon(if (ok) Icons.Default.Check else Icons.Default.Close, contentDescription = null, tint = if (ok) colors.success else colors.danger, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = colors.textMuted, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(if (ok) "OK" else "Action", color = if (ok) colors.success else colors.warning, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun StatusChip(label: String, ok: Boolean, colors: ProColors, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (ok) colors.success.copy(alpha = 0.12f) else colors.warning.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusDot(if (ok) colors.success else colors.warning)
        Spacer(Modifier.width(6.dp))
        Text(label, color = colors.text, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun ToggleLine(
    label: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
    color: Color,
    modifier: Modifier,
    colors: ProColors,
    enabled: Boolean = true,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1)
        Switch(checked = checked, enabled = enabled, onCheckedChange = onChecked)
    }
}

@Composable
fun StatusPill(label: String, color: Color, colors: ProColors) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
fun StatusDot(color: Color) {
    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
}

@Composable
fun EmptyPanel(text: String, colors: ProColors) {
    ProCard(colors) {
        Box(modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp), contentAlignment = Alignment.Center) {
            Text(text, color = colors.textMuted, fontSize = 13.sp)
        }
    }
}

@Composable
fun EmptyActionPanel(text: String, actionLabel: String, onAction: () -> Unit, colors: ProColors) {
    ProCard(colors) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(min = 112.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text, color = colors.textMuted, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onAction,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(5.dp))
                Text(actionLabel, color = Color.White)
            }
        }
    }
}

@Composable
fun LoadingPanel(colors: ProColors) {
    ProCard(colors) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = colors.primary, strokeWidth = 2.dp)
            Text("Loading current control state", color = colors.textMuted, fontSize = 13.sp)
        }
    }
}

@Composable
fun LogLine(entry: AgentLogger.LogEntry, colors: ProColors, raised: Boolean = false) {
    val color = when (entry.type) {
        AgentLogger.LogType.ERROR -> colors.danger
        AgentLogger.LogType.WAIT -> colors.warning
        AgentLogger.LogType.REPLY -> colors.success
        AgentLogger.LogType.MESSAGE -> colors.info
        else -> colors.textSubtle
    }
    val modifier = if (raised) {
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surface)
            .border(1.dp, colors.outline, RoundedCornerShape(8.dp))
            .padding(10.dp)
    } else {
        Modifier.fillMaxWidth().padding(vertical = 5.dp)
    }
    Row(modifier = modifier, verticalAlignment = Alignment.Top) {
        StatusDot(color)
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.message.cleanUiText(), color = colors.textMuted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(entry.time, color = colors.textSubtle, fontSize = 10.sp)
        }
    }
}

@Composable
fun LifecycleBar(tickets: List<EventTicketResponse>, colors: ProColors) {
    val statuses = listOf("DRAFT", "PREPARED", "APPROVED", "SENT", "FAILED")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        statuses.forEach { status ->
            val count = tickets.count {
                if (status == "FAILED") it.status.contains("FAILED") else it.status == status
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(statusColor(status, colors).copy(alpha = 0.10f))
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(count.toString(), color = statusColor(status, colors), fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                Text(status.shortStatus(), color = colors.textMuted, fontSize = 9.sp, maxLines = 1)
            }
        }
    }
}

@Composable
fun ActionButton(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    colors: ProColors,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
) {
    val color = if (danger) colors.danger else colors.primary
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(34.dp),
        contentPadding = PaddingValues(horizontal = 8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = if (enabled) color else colors.textSubtle, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, color = if (enabled) color else colors.textSubtle, fontSize = 11.sp)
    }
}

@Composable
fun DetailLine(label: String, value: String, colors: ProColors) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, color = colors.textSubtle, fontSize = 12.sp, modifier = Modifier.width(86.dp))
        Text(value.ifBlank { "-" }, color = colors.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
    }
}

fun contactTone(contact: UiContact): String = when {
    !contact.active -> "Off"
    contact.category.name == "WORK" -> "Review"
    contact.category.name == "UNKNOWN" -> "Guarded"
    else -> "Trusted"
}
