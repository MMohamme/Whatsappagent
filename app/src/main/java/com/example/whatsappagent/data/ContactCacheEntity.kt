package com.example.whatsappagent.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stores the mapping between WhatsApp display names and phone numbers.
 * This is used as a fallback when the NotificationListener cannot determine
 * the phone number directly.
 */
@Entity(tableName = "contact_cache")
data class ContactCacheEntity(
    @PrimaryKey
    val phoneNumber: String,    // ← PK ist eindeutig
    val name: String,
    val lastUpdated: Long = System.currentTimeMillis()
)
