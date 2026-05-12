package com.example.whatsappagent.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.whatsappagent.data.*
import com.example.whatsappagent.data.remote.AgentRepository
import com.example.whatsappagent.data.remote.EventCreate
import com.example.whatsappagent.data.remote.EventResponse
import com.example.whatsappagent.data.remote.EventUpdate
import com.example.whatsappagent.ui.model.ContactCategory
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel for Events screen
 * Manages events via the backend API
 */
class EventsViewModel(
    private val database: AppDatabase,
    private val repository: AgentRepository,
    private val application: Application
) : AndroidViewModel(application) {

    private val _events = MutableStateFlow<List<EventResponse>>(emptyList())
    val events: StateFlow<List<EventResponse>> = _events.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()

    private val _showAddEventDialog = MutableStateFlow(false)
    val showAddEventDialog: StateFlow<Boolean> = _showAddEventDialog.asStateFlow()

    init {
        loadAll()
    }

    fun loadAll(status: String? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            repository.getAllEvents(status).onSuccess {
                _events.value = it
            }.onFailure {
                // Log error
            }
            _isLoading.value = false
        }
    }

    fun addEvent(
        contactName: String,
        title: String,
        scheduledAt: String, // ISO 8601
        description: String? = null,
        eventType: String = "CUSTOM"
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            val eventCreate = EventCreate(
                title = title,
                eventType = eventType,
                description = description,
                scheduledAt = scheduledAt
            )
            repository.createEvent(contactName, eventCreate).onSuccess {
                _successMessage.value = "Event '$title' für $contactName erstellt."
                loadAll()
                _showAddEventDialog.value = false
            }.onFailure {
                // Handle error
            }
            _isLoading.value = false
        }
    }

    fun updateEvent(eventId: Int, title: String? = null, status: String? = null) {
        viewModelScope.launch {
            val update = EventUpdate(title = title, status = status)
            repository.updateEvent(eventId, update).onSuccess {
                loadAll()
            }
        }
    }

    fun triggerNow(eventId: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            repository.triggerEvent(eventId).onSuccess {
                loadAll()
            }.onFailure {
                // Handle error
            }
            _isLoading.value = false
        }
    }

    fun deleteEvent(eventId: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            repository.deleteEvent(eventId).onSuccess {
                loadAll()
            }
            _isLoading.value = false
        }
    }

    fun broadcastEvent(
        category: ContactCategory,
        title: String,
        scheduledAt: String,
        description: String? = null
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            // 1. Get all active contacts in this category
            val contacts = database.contactSettingsDao().getSettingsByCategory(category.name)
                .filter { it.isActive }
            
            var successCount = 0
            var errorCount = 0

            // 2. Create an event for each contact with rate limiting handling
            contacts.forEach { contact ->
                var attempt = 0
                var success = false
                
                while (attempt < 3 && !success) {
                    val result = repository.createEvent(contact.contactName, EventCreate(
                        title = title,
                        eventType = "PROACTIVE",
                        description = description,
                        scheduledAt = scheduledAt
                    ))

                    if (result.isSuccess) {
                        success = true
                        successCount++
                    } else {
                        val error = result.exceptionOrNull()?.message ?: ""
                        if (error.contains("429")) {
                            attempt++
                            kotlinx.coroutines.delay(1000L * attempt) // Backoff
                        } else {
                            attempt = 3 // Give up on other errors
                        }
                    }
                }
                if (!success) errorCount++
                
                // Small delay between contacts to avoid hammering
                kotlinx.coroutines.delay(300L)
            }
            
            _successMessage.value = "$successCount Events geplant" + (if (errorCount > 0) ", $errorCount Fehler" else "")
            loadAll()
            _showAddEventDialog.value = false
            _isLoading.value = false
        }
    }

    fun showAddEventDialog() {
        _showAddEventDialog.value = true
    }

    fun hideAddEventDialog() {
        _showAddEventDialog.value = false
    }

    fun clearSuccessMessage() {
        _successMessage.value = null
    }
}
