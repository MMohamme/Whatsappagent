package com.example.whatsappagent.ui.viewmodel

import android.content.Intent
import androidx.lifecycle.viewModelScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.whatsappagent.AgentLogger
import com.example.whatsappagent.WhatsAppListener
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

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

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
                .onSuccess {
                    _queue.value = it
                    _errorMessage.value = null
                }
                .onFailure { _errorMessage.value = it.message ?: "Queue konnte nicht geladen werden" }
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
            loadQueue()
        }
    }

    fun approveDraft(draftId: String) {
        viewModelScope.launch {
            draftId.toLongOrNull()?.let {
                repository.updateDraftDecision(it, "AUTO_SEND_ALLOWED", "Approved in Android review queue")
            }
            WorkManagerHelper.triggerSync(application)
            loadQueue()
        }
    }

    fun approveDraft(item: QueueMessageResponse, replyText: String? = null) {
        updateDraft(item, "AUTO_SEND_ALLOWED", "Approved in Android review queue", replyText)
    }

    fun blockDraft(draftId: String) {
        viewModelScope.launch {
            draftId.toLongOrNull()?.let {
                repository.updateDraftDecision(it, "BLOCKED", "Blocked in Android review queue")
            }
            loadQueue()
        }
    }

    fun blockDraft(item: QueueMessageResponse, reason: String? = null) {
        updateDraft(item, "BLOCKED", reason?.trim()?.takeIf { it.isNotEmpty() } ?: "Blocked in Android review queue", null)
    }

    fun retryAll() {
        viewModelScope.launch {
            loadQueue()
        }
    }

    private fun updateDraft(
        item: QueueMessageResponse,
        decision: String,
        reason: String,
        replyText: String?
    ) {
        viewModelScope.launch {
            val draftId = item.draftId
            if (draftId == null) {
                _errorMessage.value = "Draft-ID fehlt fuer ${item.contactName}"
                return@launch
            }
            repository.updateDraftDecision(draftId, decision, reason, replyText?.trim()?.takeIf { it.isNotEmpty() })
                .onSuccess { updated ->
                    _errorMessage.value = null
                    if (decision == "AUTO_SEND_ALLOWED") {
                        dispatchApprovedDraft(updated)
                    }
                }
                .onFailure { _errorMessage.value = it.message ?: "Draft konnte nicht aktualisiert werden" }
            WorkManagerHelper.triggerSync(application)
            loadQueue()
        }
    }

    private fun dispatchApprovedDraft(item: QueueMessageResponse) {
        val reply = item.draftText.takeIf { it.isNotBlank() } ?: return
        val customId = item.messageCustomId ?: item.customId
        if (customId.isNullOrBlank()) {
            AgentLogger.log(AgentLogger.LogType.INFO, "Review Queue: Draft ${item.draftId} freigegeben, Versand wartet auf Sync")
            return
        }
        val intent = Intent(WhatsAppListener.ACTION_SEND_REPLY).apply {
            putExtra(WhatsAppListener.EXTRA_SENDER, item.contactName)
            putExtra(WhatsAppListener.EXTRA_REPLY, reply)
            putExtra(WhatsAppListener.EXTRA_CUSTOM_ID, customId)
            putExtra(WhatsAppListener.EXTRA_DRAFT_ID, item.draftId ?: -1L)
            putExtra("is_event", item.eventRecipientId != null)
        }
        LocalBroadcastManager.getInstance(application).sendBroadcast(intent)
        AgentLogger.log(AgentLogger.LogType.INFO, "Review Queue: Draft ${item.draftId} freigegeben")
    }
}
