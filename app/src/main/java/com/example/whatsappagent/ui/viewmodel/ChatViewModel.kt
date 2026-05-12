package com.example.whatsappagent.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.whatsappagent.data.AppDatabase
import com.example.whatsappagent.data.MessageEntity
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * ViewModel for Chat screen
 * Manages conversation history for a specific sender
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    private val database: AppDatabase
) : ViewModel() {

    private val _selectedSender = MutableStateFlow<String?>(null)
    val selectedSender: StateFlow<String?> = _selectedSender.asStateFlow()

    val messages: StateFlow<List<MessageEntity>> = _selectedSender
        .flatMapLatest { sender ->
            if (sender != null) {
                database.messageDao().getMessagesForSenderFlow(sender, 50)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val senders: StateFlow<List<String>> = database.messageDao()
        .getSendersFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun selectSender(sender: String) {
        _selectedSender.value = sender
    }

    fun clearSelectedSender() {
        _selectedSender.value = null
    }

    fun refreshMessages() {
        // Messages are automatically updated via Flow
    }
}
