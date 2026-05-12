package com.example.whatsappagent.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room Entity for storing contact-specific settings
 * Used to persist toggle state and other preferences
 */
@Entity(tableName = "contact_settings")
data class ContactSettingsEntity(
    @PrimaryKey
    val phoneNumber: String,               // ← PK ist jetzt Telefonnummer
    val contactName: String,               // ← Name bleibt als normales Feld
    val isActive: Boolean = true,
    val relation: String = "Kontakt",
    val category: String = "FREUNDE",
    val style: String = "Standard",
    val delayMin: Int = 1500, // milliseconds
    val delayMax: Int = 3000, // milliseconds
    val preferredLang: String = "de",
    val updatedAt: Long = System.currentTimeMillis()
)
