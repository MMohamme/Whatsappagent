package com.example.whatsappagent.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.whatsappagent.AgentService
import com.example.whatsappagent.AgentLogger
import com.example.whatsappagent.AgentSafetySettings
import com.example.whatsappagent.data.AppDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

import com.example.whatsappagent.ui.theme.AgentTheme

/**
 * Main ViewModel for global app state
 * Handles backend connectivity, theme, and log entries
 */
class MainViewModel(
    private val database: AppDatabase,
    application: Application
) : AndroidViewModel(application) {

    private val _backendConnected = MutableStateFlow(false)
    val backendConnected: StateFlow<Boolean> = _backendConnected.asStateFlow()

    private val _logEntries = MutableStateFlow<List<String>>(emptyList())
    val logEntries: StateFlow<List<String>> = _logEntries.asStateFlow()

    private val _pingLatency = MutableStateFlow<List<Int>>(emptyList())
    val pingLatency: StateFlow<List<Int>> = _pingLatency.asStateFlow()

    private val _isDarkTheme = MutableStateFlow(false)
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    private val _currentTheme = MutableStateFlow(AgentTheme.DARK)
    val currentTheme: StateFlow<AgentTheme> = _currentTheme.asStateFlow()

    private val _isNotificationEnabled = MutableStateFlow(false)
    val isNotificationEnabled: StateFlow<Boolean> = _isNotificationEnabled.asStateFlow()

    private val _isAccessibilityEnabled = MutableStateFlow(false)
    val isAccessibilityEnabled: StateFlow<Boolean> = _isAccessibilityEnabled.asStateFlow()

    private val _isContactsEnabled = MutableStateFlow(false)
    val isContactsEnabled: StateFlow<Boolean> = _isContactsEnabled.asStateFlow()

    private val safetyPrefs = application.getSharedPreferences(AgentSafetySettings.PREFS_NAME, Context.MODE_PRIVATE)

    private val _isAutoSendPaused = MutableStateFlow(
        safetyPrefs.getBoolean(AgentSafetySettings.PREF_AUTO_SEND_PAUSED, false)
    )
    val isAutoSendPaused: StateFlow<Boolean> = _isAutoSendPaused.asStateFlow()

    private val _isAccessibilityFallbackEnabled = MutableStateFlow(
        safetyPrefs.getBoolean(AgentSafetySettings.PREF_ACCESSIBILITY_FALLBACK_ENABLED, false)
    )
    val isAccessibilityFallbackEnabled: StateFlow<Boolean> = _isAccessibilityFallbackEnabled.asStateFlow()

    init {
        // Initialize with current backend status
        _backendConnected.value = true // Assume connected initially
        
        // Load recent log entries
        loadRecentLogEntries()
        checkPermissions()
    }

    fun updateBackendStatus(connected: Boolean) {
        _backendConnected.value = connected
    }

    fun checkPermissions() {
        val application = getApplication<Application>()
        _isNotificationEnabled.value = isNotificationServiceEnabled(application)
        _isAccessibilityEnabled.value = isAccessibilityServiceEnabled(application)
        _isContactsEnabled.value = isContactsPermissionGranted(application)
    }

    private fun isContactsPermissionGranted(context: Context): Boolean {
        return androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CONTACTS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun isNotificationServiceEnabled(context: Context): Boolean {
        val packageNames = android.provider.Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        return packageNames != null && packageNames.contains(context.packageName)
    }

    private fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_GENERIC)
        return enabledServices.any { it.resolveInfo.serviceInfo.packageName == context.packageName }
    }

    fun setTheme(theme: AgentTheme) {
        _currentTheme.value = theme
    }

    fun toggleTheme() {
        _currentTheme.value = when (_currentTheme.value) {
            AgentTheme.DARK -> AgentTheme.LIGHT
            AgentTheme.LIGHT -> AgentTheme.AMOLED
            AgentTheme.AMOLED -> AgentTheme.DARK
        }
    }

    fun setAutoSendPaused(paused: Boolean) {
        safetyPrefs.edit().putBoolean(AgentSafetySettings.PREF_AUTO_SEND_PAUSED, paused).apply()
        _isAutoSendPaused.value = paused
        AgentLogger.log(
            AgentLogger.LogType.INFO,
            if (paused) "Auto-Send pausiert" else "Auto-Send wieder aktiv"
        )
    }

    fun setAccessibilityFallbackEnabled(enabled: Boolean) {
        safetyPrefs.edit().putBoolean(AgentSafetySettings.PREF_ACCESSIBILITY_FALLBACK_ENABLED, enabled).apply()
        _isAccessibilityFallbackEnabled.value = enabled
        AgentLogger.log(
            AgentLogger.LogType.INFO,
            if (enabled) "Accessibility-Fallback freigegeben" else "Accessibility-Fallback deaktiviert"
        )
    }

    fun addLogEntry(entry: String) {
        viewModelScope.launch {
            val currentLogs = _logEntries.value.toMutableList()
            currentLogs.add(0, entry) // Add to beginning
            
            // Keep only last 100 entries
            if (currentLogs.size > 100) {
                currentLogs.removeAt(currentLogs.size - 1)
            }
            
            _logEntries.value = currentLogs
        }
    }

    fun updatePingLatency(latencyMs: Int) {
        viewModelScope.launch {
            val currentLatency = _pingLatency.value.toMutableList()
            currentLatency.add(0, latencyMs)
            
            // Keep only last 20 measurements
            if (currentLatency.size > 20) {
                currentLatency.removeAt(currentLatency.size - 1)
            }
            
            _pingLatency.value = currentLatency
        }
    }


    fun refreshStats() {
        loadRecentLogEntries()
    }

    private fun loadRecentLogEntries() {
        viewModelScope.launch {
            // Load initial log entries from AgentLogger if available
            // For now, start with empty list
            _logEntries.value = emptyList()
        }
    }
}

/**
 * Factory for creating MainViewModel with proper dependencies
 */
class MainViewModelFactory(
    private val database: AppDatabase,
    private val application: Application
) : ViewModelProvider.Factory {
    
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            return MainViewModel(database, application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
