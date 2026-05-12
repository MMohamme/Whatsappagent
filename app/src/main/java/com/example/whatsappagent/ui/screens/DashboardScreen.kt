package com.example.whatsappagent.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.expandVertically
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.whatsappagent.AgentLogger
import com.example.whatsappagent.data.remote.StatsResponse
import com.example.whatsappagent.ui.components.*
import com.example.whatsappagent.ui.theme.AgentColors

private val PING_HISTORY_DEFAULT = listOf(22, 18, 31, 45, 19, 24, 28, 15, 21, 33, 17, 26)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    stats: StatsResponse?,
    pingLatency: List<Int>,
    backendConnected: Boolean,
    logEntries: List<AgentLogger.LogEntry>,
    backendUrl: String = "humming-opposite-deforest.ngrok-free.dev",
    backendModel: String = "gemini-2.5-flash-lite",
    onRefresh: () -> Unit,
    C: AgentColors,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // --- STAT CARDS ---
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            maxItemsInEachRow = 5
        ) {
            val cardModifier = Modifier.weight(1f).widthIn(min = 90.dp)
            
            StatCard("Heute",    (stats?.chartDaily?.lastOrNull()?.count ?: 0).toString(),   C.accent,  "📈", C, cardModifier)
            StatCard("Fehler",   (stats?.messages?.failedReplies ?: 0).toString(),    C.red,     "⚠", C, cardModifier)
            StatCard("Queue",    (stats?.messages?.pendingReplies ?: 0).toString(),    C.yellow,  "⏳", C, cardModifier)
            StatCard("Total",    (stats?.messages?.total ?: 0).toString(), C.green, "📊", C, cardModifier)
            StatCard("Kontakte", (stats?.contacts?.total ?: 0).toString(), C.purple, "👥", C, cardModifier)
        }

        // ── Backend + Kategorie row ──────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Backend Card
            AgentCard(C = C, modifier = Modifier.weight(1.2f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionLabel("Backend Status", C)
                    IconButton(onClick = onRefresh, modifier = Modifier.size(24.dp)) {
                        Text("↻", color = C.accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(if (backendConnected) C.green else C.red)
                    )
                    Text(
                        if (backendConnected) "Online" else "Offline",
                        color = if (backendConnected) C.green else C.red,
                        fontWeight = FontWeight.Bold, fontSize = 16.sp,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(backendUrl, color = C.textSecondary, fontSize = 12.sp)
                Text("$backendModel · 30s Delay", color = C.textMuted, fontSize = 11.sp)
                
                Spacer(Modifier.height(16.dp))
                SectionLabel("Latenz History", C)
                Spacer(Modifier.height(8.dp))
                SparklineChart(values = if (pingLatency.isEmpty()) PING_HISTORY_DEFAULT else pingLatency, threshold = 35, C = C)
            }

            // Themen-Verteilung (Topic Distribution)
            AgentCard(C = C, modifier = Modifier.weight(1f)) {
                SectionLabel("Themen", C)
                Spacer(Modifier.height(16.dp))
                val total = stats?.chartCategories?.sumOf { it.count }?.coerceAtLeast(1) ?: 1
                
                if (stats?.chartCategories.isNullOrEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Keine Daten", color = C.textMuted, fontSize = 12.sp)
                    }
                } else {
                    stats?.chartCategories?.take(4)?.forEach { item ->
                        val color = when(item.category.uppercase()) {
                            "CORE_FAMILY", "FAMILIE" -> C.purple
                            "WORK", "ARBEIT"  -> C.yellow
                            "FRIEND", "FREUNDE" -> C.accent
                            else      -> C.blue
                        }
                        CategoryBar(item.category, item.count, total, color, C)
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }

        // ── Recent Activity ──────────────────────────────────────────────────
        AgentCard(C = C, modifier = Modifier.fillMaxWidth(), padding = 0) {
            Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                SectionLabel("Letzte Aktivität", C)
            }
            AgentDivider(C)
            
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                val recent = logEntries.takeLast(8).reversed()
                if (recent.isEmpty()) {
                    EmptyState(
                        title = "Keine Aktivität",
                        description = "Sobald Nachrichten verarbeitet werden, erscheinen sie hier.",
                        icon = "📭",
                        C = C
                    )
                } else {
                    recent.forEachIndexed { i, entry ->
                        AnimatedVisibility(
                            visible = visible,
                            enter = fadeIn() + expandVertically()
                        ) {
                            Column {
                                LogRow(entry = entry, C = C)
                                if (i < recent.lastIndex) {
                                    AgentDivider(C)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Sparkline chart ─────────────────────────────────────────────────────────
@Composable
private fun SparklineChart(values: List<Int>, threshold: Int, C: AgentColors) {
    val max = values.maxOrNull() ?: 1
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        values.forEachIndexed { i, v ->
            val fraction = v.toFloat() / max
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(fraction)
                    .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                    .background(
                        color = (if (v > threshold) C.yellow else C.accent)
                            .copy(alpha = 0.4f + (i.toFloat() / values.size) * 0.6f)
                    )
            )
        }
    }
}

// ── Category bar ─────────────────────────────────────────────────────────────
@Composable
private fun CategoryBar(label: String, count: Int, total: Int, color: Color, C: AgentColors) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = C.textSecondary, fontSize = 12.sp)
        Text(count.toString(), color = color, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
    }
    Spacer(Modifier.height(4.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(C.border)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(count.toFloat() / total)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
    }
}

// ── Log row ──────────────────────────────────────────────────────────────────
@Composable
fun LogRow(entry: AgentLogger.LogEntry, C: AgentColors) {
    val color = when (entry.type) {
        AgentLogger.LogType.MESSAGE -> C.blue
        AgentLogger.LogType.REPLY   -> C.green
        AgentLogger.LogType.WAIT    -> C.yellow
        AgentLogger.LogType.ERROR   -> C.red
        AgentLogger.LogType.BACKEND -> C.purple
        AgentLogger.LogType.INFO    -> C.textSecondary
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(entry.time, color = C.textMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(52.dp))
        
        val icon = when(entry.type) {
            AgentLogger.LogType.ERROR -> "❌ "
            AgentLogger.LogType.REPLY -> "✅ "
            AgentLogger.LogType.MESSAGE -> "📨 "
            AgentLogger.LogType.WAIT -> "⏱ "
            AgentLogger.LogType.BACKEND -> "🌐 "
            else -> "• "
        }
        
        Text(
            text = icon + entry.message, 
            color = color, 
            fontSize = 12.sp, 
            fontFamily = FontFamily.Monospace, 
            modifier = Modifier.weight(1f),
            lineHeight = 16.sp
        )
    }
}
