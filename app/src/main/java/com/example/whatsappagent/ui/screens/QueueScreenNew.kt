package com.example.whatsappagent.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.whatsappagent.data.remote.QueueMessageResponse
import com.example.whatsappagent.ui.components.*
import com.example.whatsappagent.ui.theme.AgentColors
import java.text.SimpleDateFormat
import java.util.*

/**
 * QueueScreen updated to use backend models (QueueMessageResponse)
 */
enum class QueueStatusUI { ALL, CAPTURED, REPLY_PENDING, REPLY_FAILED }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QueueScreenNew(
    queue: List<QueueMessageResponse>,
    currentFilter: String?,
    isLoading: Boolean,
    onFilterChange: (String?) -> Unit,
    onRetryMessage: (String) -> Unit = {},
    onRetryAll: () -> Unit = {},
    onTriggerSync: () -> Unit = {},
    onTriggerIndex: () -> Unit = {},
    C: AgentColors
) {
    Column(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

        // Summary row (Stats from the current list)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(
                Triple("Captured", "CAPTURED", C.yellow),
                Triple("Pending",  "REPLY_PENDING", C.accent),
                Triple("Sent",     "DONE", C.green),
                Triple("Failed",   "REPLY_FAILED", C.red),
            ).forEach { (label, status, color) ->
                AgentCard(
                    C = C,
                    modifier = Modifier.weight(1f),
                    borderColor = color.copy(alpha = 0.3f),
                    backgroundColor = color.copy(alpha = 0.05f),
                    padding = 10
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = queue.count { it.status == status }.toString(),
                            color = color,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = label.uppercase(),
                            color = color.copy(alpha = 0.7f),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }

        // Filter chips + Sync/Index
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            FlowRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(null, "CAPTURED", "REPLY_PENDING", "REPLY_FAILED").forEach { status ->
                    val isSelected = currentFilter == status
                    val label = status ?: "ALL"
                    
                    FilterChip(
                        selected = isSelected,
                        onClick = { onFilterChange(status) },
                        label = { Text(label, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = C.accentLow,
                            selectedLabelColor = C.accent,
                            containerColor = C.surface,
                            labelColor = C.textMuted
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = if (isSelected) C.accent else C.border,
                            enabled = true,
                            selected = isSelected
                        ),
                        shape = RoundedCornerShape(20.dp)
                    )
                }
            }
            
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (queue.any { it.status == "REPLY_FAILED" || it.status == "CAPTURED" }) {
                    TextButton(onClick = onRetryAll) {
                        Text("Alle erneut", color = C.red, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                IconButton(onClick = onTriggerSync) {
                    Icon(Icons.Default.Refresh, contentDescription = "Sync", tint = C.accent)
                }
                
                Button(
                    onClick = onTriggerIndex,
                    colors = ButtonDefaults.buttonColors(containerColor = C.purple),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text("Index", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().clip(CircleShape), color = C.accent, trackColor = C.border)
        }

        // Queue list
        if (queue.isEmpty() && !isLoading) {
            EmptyState(
                title = "Warteschlange leer",
                description = "Es gibt aktuell keine Nachrichten, die verarbeitet werden müssen.",
                icon = "📋",
                C = C,
                modifier = Modifier.weight(1f)
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(queue, key = { it.msgId }) { item ->
                    QueueItemRow(item, C, onRetryMessage)
                }
            }
        }
    }
}

@Composable
private fun QueueItemRow(item: QueueMessageResponse, C: AgentColors, onRetry: (String) -> Unit) {
    val statusColor = when (item.status) {
        "CAPTURED" -> C.yellow
        "SYNCING", "REPLY_PENDING" -> C.accent
        "DONE", "REPLY_SENT" -> C.green
        "REPLY_FAILED", "SYNC_FAILED" -> C.red
        else -> C.textMuted
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(C.surface)
            .border(1.dp, statusColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Status indicator
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(statusColor, RoundedCornerShape(4.dp))
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            // Sender + timestamp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = item.contactName,
                        color = C.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    // Category Chip
                    if (!item.category.isNullOrBlank()) {
                        AgentPill(item.category, C.accent)
                    }
                }
                Text(
                    text = item.timestamp?.let { formatIsoTime(it) } ?: "Gerade",
                    color = C.textMuted,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Message text
            Text(
                text = item.content,
                color = C.textSecondary,
                fontSize = 13.sp,
                maxLines = 2
            )
        }

        // Action button
        if (item.status == "CAPTURED" || item.status == "REPLY_FAILED") {
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = { onRetry(item.msgId) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Retry",
                    tint = C.accent,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

private fun formatIsoTime(iso: String): String {
    return try {
        // Assume ISO 8601 (yyyy-MM-dd'T'HH:mm:ss...)
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        val date = sdf.parse(iso)
        val now = System.currentTimeMillis()
        val diff = now - (date?.time ?: now)
        
        when {
            diff < 60_000 -> "vor ${diff / 1000}s"
            diff < 3600_000 -> "vor ${diff / 60_000}min"
            diff < 86400_000 -> "vor ${diff / 3600_000}h"
            else -> SimpleDateFormat("dd.MM", Locale.getDefault()).format(date!!)
        }
    } catch (_: Exception) {
        iso
    }
}
