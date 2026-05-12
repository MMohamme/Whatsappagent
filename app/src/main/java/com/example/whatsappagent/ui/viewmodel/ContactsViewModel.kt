package com.example.whatsappagent.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.whatsappagent.data.*
import com.example.whatsappagent.data.remote.AgentRepository
import com.example.whatsappagent.data.remote.NoteResponse
import com.example.whatsappagent.ui.model.UiContact
import com.example.whatsappagent.ui.model.UiNote
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel for Contacts screen
 * Manages contacts, notes, and contact settings with backend sync
 */
class ContactsViewModel(
    private val database: AppDatabase,
    private val repository: AgentRepository
) : ViewModel() {

    private val _selectedContact = MutableStateFlow<String?>(null)
    val selectedContact: StateFlow<String?> = _selectedContact.asStateFlow()

    private val _showAddContactDialog = MutableStateFlow(false)
    val showAddContactDialog: StateFlow<Boolean> = _showAddContactDialog.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()

    private val _notesMap = MutableStateFlow<Map<String, List<NoteResponse>>>(emptyMap())

    val uiContacts: StateFlow<List<UiContact>> = 
        repository.getLocalContacts()
            .onEach { android.util.Log.d("ContactsVM", "UI Contacts updated: ${it.size} entries") }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    // Exposed notes for the currently selected contact
    val uiNotes: StateFlow<List<UiNote>> = 
        combine(_notesMap, _selectedContact) { map, selectedName ->
            if (selectedName == null) emptyList()
            else map[selectedName]?.sortedByDescending { it.pinned }?.map { note ->
                UiNote(
                    id = note.id.toLong(),
                    contact = note.contactName,
                    type = com.example.whatsappagent.ui.model.NoteType.PERSONAL,
                    text = if (note.pinned) "📌 ${note.content}" else note.content,
                    createdAt = formatBackendTimestamp(note.createdAt),
                    expiresAt = formatBackendTimestamp(note.expiryDate),
                    expiresAtMillis = note.expiryDate?.let { parseIsoTimestamp(it) }
                )
            } ?: emptyList()
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        // Initial sync
        viewModelScope.launch {
            repository.syncContacts()
        }
    }

    fun refreshContacts() {
        viewModelScope.launch {
            _isLoading.value = true
            repository.syncContacts()
            _isLoading.value = false
        }
    }

    fun toggleContactActive(phoneNumber: String, currentActive: Boolean) {
        viewModelScope.launch {
            repository.toggleContactActive(phoneNumber, !currentActive)
        }
    }

    fun addContact(contact: UiContact) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = repository.createContact(contact)
            if (result.isSuccess) {
                _successMessage.value = "Kontakt '${contact.name}' erfolgreich angelegt."
            } else {
                _error.value = "Failed to create contact: ${result.exceptionOrNull()?.message}"
            }
            _isLoading.value = false
        }
    }

    fun deleteContact(contactName: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = repository.deleteContact(contactName)
            if (result.isFailure) {
                _error.value = "Failed to delete contact: ${result.exceptionOrNull()?.message}"
            }
            _isLoading.value = false
        }
    }

    fun updateContact(contact: UiContact) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = repository.updateContact(contact)
            if (result.isSuccess) {
                _successMessage.value = "Kontakt '${contact.name}' aktualisiert."
            } else {
                _error.value = "Failed to update contact: ${result.exceptionOrNull()?.message}"
            }
            _isLoading.value = false
        }
    }

    // --- Notes Backend Logic ---
    fun loadNotes(phoneOrName: String) {
        viewModelScope.launch {
            repository.getNotes(phoneOrName).onSuccess { list ->
                _notesMap.value = _notesMap.value + (phoneOrName to list)
            }
        }
    }

    fun addNote(phoneOrName: String, content: String, pinned: Boolean = false, expiryMillis: Long? = null) {
        viewModelScope.launch {
            repository.createNote(phoneOrName, content, pinned, expiryMillis).onSuccess {
                loadNotes(phoneOrName)
            }
        }
    }

    fun deleteNote(phoneOrName: String, noteId: Int) {
        viewModelScope.launch {
            repository.deleteNote(phoneOrName, noteId).onSuccess {
                loadNotes(phoneOrName)
            }
        }
    }

    fun togglePin(phoneOrName: String, noteId: Int, currentPinned: Boolean) {
        viewModelScope.launch {
            repository.updateNote(phoneOrName, noteId, null, !currentPinned).onSuccess {
                loadNotes(phoneOrName)
            }
        }
    }

    fun showAddContactDialog() {
        _showAddContactDialog.value = true
    }

    fun hideAddContactDialog() {
        _showAddContactDialog.value = false
    }

    fun selectContact(contactName: String) {
        _selectedContact.value = contactName
        loadNotes(contactName)
    }

    fun clearSelectedContact() {
        _selectedContact.value = null
    }

    fun clearError() {
        _error.value = null
    }

    fun clearSuccessMessage() {
        _successMessage.value = null
    }

    private fun formatBackendTimestamp(timestamp: String?): String {
        if (timestamp == null) return ""
        return try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
            val date = sdf.parse(timestamp)
            java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(date!!)
        } catch (e: Exception) {
            timestamp.take(10)
        }
    }

    private fun parseIsoTimestamp(timestamp: String?): Long? {
        if (timestamp == null) return null
        return try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
            sdf.parse(timestamp)?.time
        } catch (e: Exception) {
            null
        }
    }
}
