package com.example.whatsappagent.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.whatsappagent.data.AppDatabase
import com.example.whatsappagent.data.remote.AgentRepository
import com.example.whatsappagent.data.remote.StatsResponse
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel for Dashboard screen
 * Provides statistics and metrics from backend and local database
 */
class DashboardViewModel(
    private val database: AppDatabase,
    private val repository: AgentRepository
) : ViewModel() {

    private val _stats = MutableStateFlow<StatsResponse?>(null)
    val stats: StateFlow<StatsResponse?> = _stats.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadStats()
    }

    fun loadStats() {
        viewModelScope.launch {
            _isLoading.value = true
            repository.getStats().onSuccess {
                _stats.value = it
            }.onFailure {
                // Log error if needed
            }
            _isLoading.value = false
        }
    }

    fun refreshStats() {
        loadStats()
    }
}
