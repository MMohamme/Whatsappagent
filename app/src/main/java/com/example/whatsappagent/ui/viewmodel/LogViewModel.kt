package com.example.whatsappagent.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.whatsappagent.AgentLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for Live Log screen
 * Manages real-time log entries
 */
class LogViewModel : ViewModel() {

    private val _logEntries = MutableStateFlow<List<AgentLogger.LogEntry>>(emptyList())
    val logEntries: StateFlow<List<AgentLogger.LogEntry>> = _logEntries.asStateFlow()

    private val _isAutoScroll = MutableStateFlow(true)
    val isAutoScroll: StateFlow<Boolean> = _isAutoScroll.asStateFlow()

    private val _filterLevel = MutableStateFlow<LogLevel?>(null)
    val filterLevel: StateFlow<LogLevel?> = _filterLevel.asStateFlow()

    init {
        loadLogEntries()
    }

    private fun loadLogEntries() {
        viewModelScope.launch {
            // Load current logs from AgentLogger to populate screen immediately
            _logEntries.value = AgentLogger.getLogs()
        }
    }

    fun addLogEntry(entry: AgentLogger.LogEntry) {
        viewModelScope.launch {
            val currentLogs = _logEntries.value.toMutableList()
            currentLogs.add(entry) // Add to end for LazyColumn auto-scroll logic
            
            // Keep only last 500 entries
            if (currentLogs.size > 500) {
                currentLogs.removeAt(0)
            }
            
            _logEntries.value = currentLogs
        }
    }

    fun toggleAutoScroll() {
        _isAutoScroll.value = !_isAutoScroll.value
    }

    fun setFilterLevel(level: LogLevel?) {
        _filterLevel.value = level
    }

    fun clearLogs() {
        viewModelScope.launch {
            _logEntries.value = emptyList()
            // Also clear AgentLogger if needed
        }
    }
}

enum class LogLevel {
    DEBUG, INFO, WARNING, ERROR
}
