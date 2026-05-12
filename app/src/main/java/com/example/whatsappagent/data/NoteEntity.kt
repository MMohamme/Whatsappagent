package com.example.whatsappagent.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room Entity for storing notes about contacts
 * Local storage only - no backend sync needed
 */
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val contactName: String,
    val type: NoteType,
    val text: String,
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAtMillis: Long? = null,
    val scope: NoteScope = NoteScope.CONTACT,
    val category: String? = null,
    val pinned: Boolean = false,
    val priority: Int = 0,
    val validFromMillis: Long? = null,
    val backendId: Long? = null
)

enum class NoteType {
    PERSONAL,
    REMINDER,
    INFO,
    CUSTOM
}

enum class NoteScope {
    GLOBAL,
    CATEGORY,
    CONTACT
}
