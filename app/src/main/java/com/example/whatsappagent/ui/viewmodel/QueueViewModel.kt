package com.example.whatsappagent.ui.viewmodel

import androidx.lifecycle.viewModelScope
import com.example.whatsappagent.data.AppDatabase
import com.example.whatsappagent.data.MessageEntity
import com.example.whatsappagent.data.remote.AgentRepository
import com.example.whatsappagent.data.remote.QueueMessageResponse
import com.example.whatsappagent.worker.WorkManagerHelper
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel for Queue screen
 * Manages pending and recent synced messages from backend
 */
class QueueViewModel(
    private val database: AppDatabase,
    private val repository: AgentRepository,
    private val application: Application
) : AndroidViewModel(application) {

    private val _queue = MutableStateFlow<List<QueueMessageResponse>>(emptyList())
    val queue: StateFlow<List<QueueMessageResponse>> = _queue.asStateFlow()

    private val _statusFilter = MutableStateFlow<String?>(null)
    val statusFilter: StateFlow<String?> = _statusFilter.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val pendingMessages: StateFlow<List<MessageEntity>> = 
        database.messageDao().getPendingMessagesFlow()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    init {
        loadQueue()
    }

    fun loadQueue() {
        viewModelScope.launch {
            _isLoading.value = true
            repository.getQueue(status = _statusFilter.value)
                .onSuccess { _queue.value = it }
                .onFailure { /* Log error */ }
            _isLoading.value = false
        }
    }

    fun setFilter(status: String?) {
        _statusFilter.value = if (status == "ALL") null else status
        loadQueue()
    }

    fun triggerSync() {
        viewModelScope.launch {
            WorkManagerHelper.triggerSync(application)
            loadQueue()
        }
    }

    fun triggerContactIndexing() {
        viewModelScope.launch {
            WorkManagerHelper.triggerContactIndexing(application)
        }
    }

    fun retryMessage(messageId: String) {
        viewModelScope.launch {
            // Mark message as pending again locally first
            database.messageDao().updateStatus(messageId, com.example.whatsappagent.data.MessageStatus.CAPTURED)
            // Then trigger sync and reload
            triggerSync()
        }
    }

    fun retryAll() {
        viewModelScope.launch {
            WorkManagerHelper.triggerSync(application)
            loadQueue()
        }
    }
}
