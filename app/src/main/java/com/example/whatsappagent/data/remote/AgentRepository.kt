package com.example.whatsappagent.data.remote

import com.example.whatsappagent.data.AppDatabase
import com.example.whatsappagent.data.ContactSettingsEntity
import com.example.whatsappagent.data.MessageEntity
import com.example.whatsappagent.ui.model.ContactCategory
import com.example.whatsappagent.ui.model.UiContact
import kotlinx.coroutines.flow.*
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.*

/**
 * Repository that combines Room database and API service
 * Handles contact synchronization between local and backend storage
 */
class AgentRepository(
    private val apiService: AgentApiService,
    private val database: AppDatabase
) {
    
    companion object {
        private const val TAG = "AgentRepository"
    }

    // --- Conflict-Fix: isActive-Toggle mit Room-first + Backend-Sync ---
    suspend fun toggleContactActive(phoneNumber: String, isActive: Boolean) {
        // 1. Zuerst lokal in Room speichern (sofort wirksam für NLS)
        database.contactSettingsDao().updateIsActive(phoneNumber, isActive)
        
        // 2. Dann ans Backend synchen (Name via ContactCache auflösen)
        val name = database.contactCacheDao().getByPhone(phoneNumber)?.name ?: return
        runCatching { 
            apiService.updateContact(name, ContactUpdate(active = isActive)) 
        }
        // Fehler loggen aber nicht werfen — Room-State gilt immer
    }

    // --- Conflict-Fix: phoneNumber bei Kontakt-Sync mitschreiben ---
    suspend fun syncContactPhoneNumber(contactName: String, phoneNumber: String): Result<ContactResponse> =
        runCatching { 
            // Backend doesn't support phoneNumber yet, just updating local cache for now
            database.contactCacheDao().insertContact(com.example.whatsappagent.data.ContactCacheEntity(phoneNumber, contactName))
            val response = apiService.updateContact(contactName, ContactUpdate(active = true)) // Dummy update to verify connection
            if (response.isSuccessful) response.body()!! else throw Exception("Failed to sync: ${response.code()}")
        }

    // --- Stats ---
    suspend fun getStats(): Result<StatsResponse> = runCatching { 
        val response = apiService.getStats()
        if (response.isSuccessful) response.body()!! else throw Exception("Failed to get stats: ${response.code()}")
    }

    // --- Queue ---
    suspend fun getQueue(status: String? = null): Result<List<QueueMessageResponse>> = runCatching { 
        val response = apiService.getQueue(status = status)
        if (response.isSuccessful) response.body()!! else throw Exception("Failed to get queue: ${response.code()}")
    }

    // --- Notes ---
    suspend fun getNotes(contactName: String): Result<List<NoteResponse>> = runCatching { 
        val response = apiService.getNotes(contactName)
        if (response.isSuccessful) response.body()!! else throw Exception("Failed to get notes: ${response.code()}")
    }

    suspend fun createNote(contactName: String, content: String, pinned: Boolean = false, expiryMillis: Long? = null): Result<NoteResponse> = runCatching { 
        val expiryIso = expiryMillis?.let { ms ->
            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(ms))
        }
        val response = apiService.createNote(contactName, NoteCreate(content, pinned, expiryIso))
        if (response.isSuccessful) response.body()!! else throw Exception("Failed to create note: ${response.code()}")
    }

    suspend fun updateNote(contactName: String, noteId: Int, content: String?, pinned: Boolean?, expiryMillis: Long? = null): Result<NoteResponse> = runCatching { 
        val expiryIso = expiryMillis?.let { ms ->
            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(ms))
        }
        val response = apiService.updateNote(contactName, noteId, NoteUpdate(content, pinned, expiryIso))
        if (response.isSuccessful) response.body()!! else throw Exception("Failed to update note: ${response.code()}")
    }

    suspend fun deleteNote(contactName: String, noteId: Int): Result<Unit> = runCatching { 
        val response = apiService.deleteNote(contactName, noteId)
        if (response.isSuccessful) Unit else throw Exception("Failed to delete note: ${response.code()}")
    }

    // --- Events ---
    suspend fun getContactEvents(contactName: String): Result<List<EventResponse>> = runCatching { 
        val response = apiService.getContactEvents(contactName)
        if (response.isSuccessful) response.body()!! else throw Exception("Failed to get events: ${response.code()}")
    }

    suspend fun getAllEvents(status: String? = null): Result<List<EventResponse>> = runCatching { 
        val response = apiService.getAllEvents(status)
        if (response.isSuccessful) response.body()!! else throw Exception("Failed to get all events: ${response.code()}")
    }

    suspend fun createEvent(contactName: String, event: EventCreate): Result<EventResponse> = runCatching { 
        val response = apiService.createEvent(contactName, event)
        if (response.isSuccessful) response.body()!! else throw Exception("Failed to create event: ${response.code()}")
    }

    suspend fun updateEvent(eventId: Int, update: EventUpdate): Result<EventResponse> = runCatching { 
        val response = apiService.updateEvent(eventId, update)
        if (response.isSuccessful) response.body()!! else throw Exception("Failed to update event: ${response.code()}")
    }

    suspend fun deleteEvent(eventId: Int): Result<Unit> = runCatching { 
        val response = apiService.deleteEvent(eventId)
        if (response.isSuccessful) Unit else throw Exception("Failed to delete event: ${response.code()}")
    }

    suspend fun triggerEvent(eventId: Int): Result<EventTriggerResponse> = runCatching { 
        val response = apiService.triggerEvent(eventId)
        if (response.isSuccessful) response.body()!! else throw Exception("Failed to trigger event: ${response.code()}")
    }

    suspend fun updateMessageStatus(msgId: String, status: String): Result<Unit> = runCatching {
        val response = apiService.updateMessageStatus(msgId, status)
        if (response.isSuccessful) Unit else throw Exception("Failed to update status: ${response.code()}")
    }
    
    // --- Existing Sync Logic ---
    suspend fun syncContacts(): Result<List<UiContact>> {
        return try {
            val response = apiService.getContacts()
            if (response.isSuccessful) {
                val backendContacts = response.body() ?: emptyList()
                android.util.Log.d(TAG, "Sync: Received ${backendContacts.size} contacts from backend")
                val uiContacts = backendContacts.map { backendContact ->
                    // Backend has no phoneNumber, check local cache
                    val phone = database.contactCacheDao().getByName(backendContact.contactName)?.phoneNumber ?: 
                               backendContact.contactName

                    // Convert backend response to UiContact
                    UiContact(
                        name = backendContact.contactName,
                        phoneNumber = phone,
                        lang = backendContact.preferredLang ?: "de",
                        relation = backendContact.specificRelation ?: "Bekannt",
                        category = mapToCategory(backendContact.relationType),
                        style = backendContact.behaviorRules ?: "Freundlich",
                        active = backendContact.active,
                        replies = backendContact.repliesCount,
                        lastSeen = backendContact.lastSeen?.let { formatTimestamp(it) } ?: "Nie"
                    )
                }
                
                // Update local contact settings (Android = Master for isActive)
                updateLocalContactSettings(backendContacts)
                
                Result.success(uiContacts)
            } else {
                Result.failure(Exception("Failed to sync contacts: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // Get contacts by combining settings and message history
    fun getLocalContacts(): Flow<List<UiContact>> {
        return combine(
            database.contactSettingsDao().getAllContactSettings(),
            database.messageDao().getSendersFlow()
        ) { settingsList, senders ->
            val settingsMap = settingsList.associateBy { it.contactName }
            val allNames = (settingsMap.keys + senders).distinct()
            
            allNames.map { name ->
                val cached = database.contactCacheDao().getByName(name)
                val phone = cached?.phoneNumber ?: name

                val settings = settingsMap[name]
                UiContact(
                    name = name,
                    phoneNumber = phone,
                    lang = settings?.preferredLang ?: "de",
                    relation = settings?.relation ?: "Unbekannt",
                    category = mapToCategory(settings?.category ?: "FREUNDE"), 
                    style = settings?.style ?: "Standard",
                    active = settings?.isActive ?: true,
                    replies = 0,
                    lastSeen = "Lokal"
                )
            }
        }
    }
    
    // Create new contact
    suspend fun createContact(contact: UiContact): Result<UiContact> {
        return try {
            val createRequest = ContactCreate(
                contactName = contact.name,
                relationType = contact.category.name,
                specificRelation = contact.relation,
                preferredLang = contact.lang,
                behaviorRules = contact.style
            )
            
            val response = apiService.createContact(createRequest)
            if (response.isSuccessful) {
                val backendContact = response.body()
                if (backendContact != null) {
                    val uiContact = UiContact(
                        name = backendContact.contactName,
                        phoneNumber = contact.phoneNumber ?: contact.name,
                        lang = backendContact.preferredLang ?: "de",
                        relation = backendContact.specificRelation ?: "Bekannt",
                        category = mapToCategory(backendContact.relationType),
                        style = backendContact.behaviorRules ?: "Freundlich",
                        active = backendContact.active,
                        replies = backendContact.repliesCount,
                        lastSeen = backendContact.lastSeen?.let { formatTimestamp(it) } ?: "Jetzt"
                    )
                    
                    // Update local settings
                    updateLocalContactSettings(listOf(backendContact))
                    
                    Result.success(uiContact)
                } else {
                    Result.failure(Exception("Empty response from backend"))
                }
            } else {
                Result.failure(Exception("Failed to create contact: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // Update contact
    suspend fun updateContact(contact: UiContact): Result<UiContact> {
        return try {
            val updateRequest = ContactUpdate(
                specificRelation = contact.relation,
                preferredLang = contact.lang,
                behaviorRules = contact.style,
                active = contact.active
            )
            
            val response = apiService.updateContact(contact.name, updateRequest)
            if (response.isSuccessful) {
                val backendContact = response.body()
                if (backendContact != null) {
                    val uiContact = UiContact(
                        name = backendContact.contactName,
                        phoneNumber = contact.phoneNumber,
                        lang = backendContact.preferredLang ?: "de",
                        relation = backendContact.specificRelation ?: "Bekannt",
                        category = mapToCategory(backendContact.relationType),
                        style = backendContact.behaviorRules ?: "Freundlich",
                        active = backendContact.active,
                        replies = backendContact.repliesCount,
                        lastSeen = backendContact.lastSeen?.let { formatTimestamp(it) } ?: "Jetzt"
                    )
                    
                    // Update local settings
                    updateLocalContactSettings(listOf(backendContact))
                    
                    Result.success(uiContact)
                } else {
                    Result.failure(Exception("Empty response from backend"))
                }
            } else {
                Result.failure(Exception("Failed to update contact: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // Delete contact
    suspend fun deleteContact(contactName: String): Result<Unit> {
        return try {
            val response = apiService.deleteContact(contactName)
            if (response.isSuccessful) {
                // Remove from local settings
                database.contactSettingsDao().deleteContactSettingsByName(contactName)
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to delete contact: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // Get contact history
    suspend fun getContactHistory(contactName: String, limit: Int = 30): Result<List<MessageEntity>> {
        return try {
            val response = apiService.getContactHistory(contactName, limit)
            if (response.isSuccessful) {
                val history = response.body() ?: emptyList()
                val messageEntities = history.map { historyItem ->
                    MessageEntity(
                        customId = historyItem.msgId,
                        sender = contactName,
                        text = historyItem.content,
                        role = historyItem.role,
                        timestamp = historyItem.timestamp?.let { parseTimestamp(it) } ?: System.currentTimeMillis(),
                        isSynced = true
                    )
                }
                Result.success(messageEntities)
            } else {
                Result.failure(Exception("Failed to get contact history: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // Health check
    suspend fun healthCheck(): Result<Boolean> {
        return try {
            val response = apiService.healthCheck()
            Result.success(response.isSuccessful)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // Private helper methods
    private fun mapToCategory(type: String): ContactCategory {
        return try {
            val upper = type.uppercase()
            when {
                upper.contains("CORE_FAMILY") || upper == "FAMILY" -> ContactCategory.CORE_FAMILY
                upper.contains("EXTENDED_FAMILY") -> ContactCategory.EXTENDED_FAMILY
                upper.contains("WORK") || upper == "ARBEIT" -> ContactCategory.WORK
                upper.contains("FRIEND") || upper == "FREUNDE" -> ContactCategory.FRIEND
                else -> ContactCategory.valueOf(upper)
            }
        } catch (e: Exception) {
            ContactCategory.UNKNOWN
        }
    }

    private suspend fun updateLocalContactSettings(backendContacts: List<ContactResponse>) {
        try {
            val settingsToUpdate = backendContacts.map { backendContact ->
                // Backend has no phoneNumber, check local cache
                val phone = database.contactCacheDao().getByName(backendContact.contactName)?.phoneNumber ?:
                           backendContact.contactName

                // Android = Master for isActive: check local first
                val existing = database.contactSettingsDao().getContactSettingsByPhone(phone)
                
                ContactSettingsEntity(
                    phoneNumber = phone,
                    contactName = backendContact.contactName,
                    isActive = existing?.isActive ?: backendContact.active, // Android = Master
                    relation = backendContact.specificRelation ?: "Bekannt",
                    category = backendContact.relationType.uppercase(),
                    style = backendContact.behaviorRules ?: "Standard",
                    preferredLang = backendContact.preferredLang ?: "de",
                    delayMin = existing?.delayMin ?: 1500,
                    delayMax = existing?.delayMax ?: 3000
                )
            }
            database.contactSettingsDao().insertAll(settingsToUpdate)
            android.util.Log.d(TAG, "Successfully updated local database with ${settingsToUpdate.size} contacts")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error updating local contact settings: ${e.message}", e)
        }
    }
    
    private fun formatTimestamp(timestamp: String): String {
        return try {
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            val date = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).parse(timestamp)
            date?.let { sdf.format(it) } ?: timestamp
        } catch (e: Exception) {
            timestamp
        }
    }
    
    private fun parseTimestamp(timestamp: String): Long {
        return try {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).parse(timestamp)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }
}
