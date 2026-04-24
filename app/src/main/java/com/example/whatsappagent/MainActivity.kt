package com.example.whatsappagent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.whatsappagent.ui.theme.WhatsappagentTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val _backendConnected = mutableStateOf(false)
    private val _logEntries = mutableStateListOf<AgentLogger.LogEntry>()

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            _backendConnected.value = intent?.getBooleanExtra(AgentService.EXTRA_CONNECTED, false) ?: false
        }
    }

    private val logReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val raw = intent?.getStringExtra(AgentLogger.EXTRA_LOG_ENTRY) ?: return
            val parts = raw.split("|", limit = 3)
            if (parts.size == 3) {
                val type = try { AgentLogger.LogType.valueOf(parts[1]) } catch (e: Exception) { AgentLogger.LogType.INFO }
                _logEntries.add(AgentLogger.LogEntry(parts[0], type, parts[2]))
                if (_logEntries.size > 50) _logEntries.removeAt(0)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AgentLogger.init(applicationContext)
        ContextCompat.startForegroundService(this, Intent(this, AgentService::class.java))
        _logEntries.addAll(AgentLogger.getLogs())

        setContent {
            WhatsappagentTheme {
                AgentUI(
                    backendConnected = _backendConnected.value,
                    logEntries = _logEntries,
                    onClearLogs = { _logEntries.clear(); AgentLogger.clear() },
                    onOpenNotificationSettings = {
                        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    },
                    onOpenAccessibilitySettings = {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        LocalBroadcastManager.getInstance(this).apply {
            registerReceiver(statusReceiver, IntentFilter(AgentService.ACTION_STATUS_UPDATE))
            registerReceiver(logReceiver, IntentFilter(AgentLogger.ACTION_NEW_LOG))
        }
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(this).apply {
            unregisterReceiver(statusReceiver)
            unregisterReceiver(logReceiver)
        }
    }
}

@Composable
fun AgentUI(
    backendConnected: Boolean,
    logEntries: List<AgentLogger.LogEntry>,
    onClearLogs: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit
) {
    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "WhatsApp AI Agent",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            StatusCard(connected = backendConnected)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onOpenNotificationSettings,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Notifications", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = onOpenAccessibilitySettings,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Accessibility", fontSize = 12.sp)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Live Log",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(onClick = onClearLogs) {
                    Text("Leeren", fontSize = 12.sp)
                }
            }

            LogBox(
                entries = logEntries,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
    }
}

@Composable
fun StatusCard(connected: Boolean) {
    val color = if (connected) Color(0xFF4CAF50) else Color(0xFFF44336)
    val label = if (connected) "● Backend verbunden" else "● Backend nicht erreichbar"

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = color, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun LogBox(entries: List<AgentLogger.LogEntry>, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) {
            scope.launch { listState.animateScrollToItem(entries.size - 1) }
        }
    }

    Box(
        modifier = modifier
            .background(Color(0xFF1A1A1A), RoundedCornerShape(12.dp))
            .padding(8.dp)
    ) {
        if (entries.isEmpty()) {
            Text(
                text = "Warte auf Nachrichten...",
                color = Color(0xFF666666),
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            LazyColumn(state = listState) {
                items(entries) { entry -> LogRow(entry) }
            }
        }
    }
}

@Composable
fun LogRow(entry: AgentLogger.LogEntry) {
    val color = when (entry.type) {
        AgentLogger.LogType.MESSAGE -> Color(0xFF64B5F6) // Blau
        AgentLogger.LogType.REPLY   -> Color(0xFF81C784) // Grün
        AgentLogger.LogType.WAIT    -> Color(0xFFFFD54F) // Gelb
        AgentLogger.LogType.ERROR   -> Color(0xFFE57373) // Rot
        AgentLogger.LogType.BACKEND -> Color(0xFFBA68C8) // Lila
        AgentLogger.LogType.INFO    -> Color(0xFF9E9E9E) // Grau
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = entry.time,
            color = Color(0xFF555555),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = entry.message,
            color = color,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}