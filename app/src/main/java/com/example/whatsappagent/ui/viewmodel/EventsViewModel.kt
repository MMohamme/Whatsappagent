package com.example.whatsappagent.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.whatsappagent.data.AppDatabase
import com.example.whatsappagent.data.remote.AgentRepository
import com.example.whatsappagent.data.remote.EventResponse
import com.example.whatsappagent.data.remote.EventTicketRequest
import com.example.whatsappagent.data.remote.EventTicketResponse
import com.example.whatsappagent.data.remote.EventUpdate
import com.example.whatsappagent.ui.model.ContactCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the Events screen.
 * Category and single-contact planning now use v3 EventTickets.
 */
class EventsViewModel(
    private val database: AppDatabase,
    private val repository: AgentRepository,
    private val application: Application
) : AndroidViewModel(application) {

    private val _events = MutableStateFlow<List<EventResponse>>(emptyList())
    val events: StateFlow<List<EventResponse>> = _events.asStateFlow()

    private val _tickets = MutableStateFlow<List<EventTicketResponse>>(emptyList())
    val tickets: StateFlow<List<EventTicketResponse>> = _tickets.asStateFlow()

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
            repository.getEventTickets(status).onSuccess {
                _tickets.value = it
            }.onFailure {
                _successMessage.value = "Event-Tickets konnten nicht geladen werden."
            }
            _events.value = emptyList()
            _isLoading.value = false
        }
    }

    fun addEvent(
        contactName: String,
        title: String,
        scheduledAt: String,
        description: String? = null,
        eventType: String = "CUSTOM"
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            repository.getBackendContacts()
                .mapCatching { contacts ->
                    contacts.firstOrNull {
                        it.contactName.equals(contactName, ignoreCase = true) ||
                            it.displayName?.equals(contactName, ignoreCase = true) == true
                    }?.id ?: throw IllegalStateException("Contact not found")
                }
                .mapCatching { contactId ->
                    repository.createEventTicket(
                        EventTicketRequest(
                            title = title,
                            targetType = "CONTACT",
                            targetContactId = contactId,
                            scheduledAt = scheduledAt,
                            baseText = description
                        )
                    ).getOrThrow()
                }
                .mapCatching { ticket ->
                    repository.prepareEventTicket(ticket.id).getOrThrow()
                }
                .onSuccess {
                    _successMessage.value = "Event-Ticket '$title' fuer $contactName vorbereitet."
                    loadAll()
                    _showAddEventDialog.value = false
                }
                .onFailure {
                    _successMessage.value = "Event-Ticket konnte nicht erstellt werden."
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
            repository.createEventTicket(
                EventTicketRequest(
                    title = title,
                    targetType = "CATEGORY",
                    targetCategory = category.name,
                    scheduledAt = scheduledAt,
                    baseText = description
                )
            )
                .mapCatching { ticket ->
                    repository.prepareEventTicket(ticket.id).getOrThrow()
                }
                .onSuccess { ticket ->
                    _successMessage.value = "Event-Ticket '${ticket.title}' vorbereitet (${ticket.recipientsCount} Empfaenger)."
                    loadAll()
                    _showAddEventDialog.value = false
                }
                .onFailure {
                    _successMessage.value = "Event-Ticket konnte nicht erstellt werden."
                }
            _isLoading.value = false
        }
    }

    fun prepareTicket(ticketId: Long) {
        viewModelScope.launch {
            _isLoading.value = true
            repository.prepareEventTicket(ticketId).onSuccess {
                _successMessage.value = "Event-Ticket vorbereitet (${it.recipientsCount} Empfaenger)."
                loadAll()
            }.onFailure {
                _successMessage.value = "Event-Ticket konnte nicht vorbereitet werden."
            }
            _isLoading.value = false
        }
    }

    fun approveTicket(ticketId: Long) {
        viewModelScope.launch {
            _isLoading.value = true
            repository.approveEventTicket(ticketId).onSuccess {
                _successMessage.value = "Event-Ticket freigegeben."
                loadAll()
            }.onFailure {
                _successMessage.value = "Event-Ticket konnte nicht freigegeben werden."
            }
            _isLoading.value = false
        }
    }

    fun cancelTicket(ticketId: Long) {
        viewModelScope.launch {
            _isLoading.value = true
            repository.cancelEventTicket(ticketId).onSuccess {
                _successMessage.value = "Event-Ticket storniert."
                loadAll()
            }.onFailure {
                _successMessage.value = "Event-Ticket konnte nicht storniert werden."
            }
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
