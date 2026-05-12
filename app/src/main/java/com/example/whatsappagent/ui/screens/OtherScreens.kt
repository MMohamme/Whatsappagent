package com.example.whatsappagent.ui.screens

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.whatsappagent.AgentLogger
import com.example.whatsappagent.ui.components.*
import com.example.whatsappagent.ui.theme.AgentColors
import kotlinx.coroutines.launch

import com.example.whatsappagent.ui.viewmodel.LogLevel

// ─────────────────────────────────────────────────────────────────────────────
// LIVE LOG SCREEN
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LiveLogScreen(
    logEntries: List<AgentLogger.LogEntry>,
    isAutoScroll: Boolean,
    filterLevel: LogLevel?,
    onToggleAutoScroll: () -> Unit,
    onSetFilterLevel: (LogLevel?) -> Unit,
    onClearLogs: () -> Unit,
    C: AgentColors,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val filtered = if (filterLevel == null) logEntries else logEntries.filter { 
        it.type.name.contains(filterLevel.name, ignoreCase = true) 
    }

    LaunchedEffect(logEntries.size) {
        if (isAutoScroll && logEntries.isNotEmpty()) {
            scope.launch { listState.animateScrollToItem(logEntries.size - 1) }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

        // Filter chips + clear + auto-scroll
        AgentCard(C = C, padding = 12) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Log levels
                LogLevel.entries.forEach { level ->
                    val isActive = filterLevel == level
                    FilterChip(
                        selected = isActive,
                        onClick = { onSetFilterLevel(if (isActive) null else level) },
                        label = { Text(level.name, fontSize = 10.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = C.accentLow,
                            selectedLabelColor = C.accent,
                            containerColor = C.surface,
                            labelColor = C.textMuted
                        ),
                        shape = RoundedCornerShape(20.dp)
                    )
                }
                
                Spacer(Modifier.weight(1f))
                
                // Auto-scroll toggle
                FilterChip(
                    selected = isAutoScroll,
                    onClick = onToggleAutoScroll,
                    label = { Text("Auto-Scroll", fontSize = 10.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = C.accentLow,
                        selectedLabelColor = C.accent
                    ),
                    shape = RoundedCornerShape(20.dp)
                )

                IconButton(onClick = onClearLogs, modifier = Modifier.size(32.dp)) {
                    Text("🗑", color = C.red, fontSize = 16.sp)
                }
            }
        }

        // Log box
        AgentCard(
            C = C,
            modifier = Modifier.weight(1f),
            padding = 0,
            backgroundColor = C.inputBg
        ) {
            if (filtered.isEmpty()) {
                EmptyState(
                    title = "Keine Logs",
                    description = "Warte auf System-Aktivitäten...",
                    icon = "📜",
                    C = C,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                LazyColumn(
                    state = listState, 
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    contentPadding = PaddingValues(12.dp)
                ) {
                    items(filtered) { entry ->
                        LogRow(entry = entry, C = C)
                    }
                }
            }
        }
    }
}
