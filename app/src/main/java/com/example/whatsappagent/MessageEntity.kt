package com.example.whatsappagent.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Repräsentiert eine abgefangene WhatsApp-Nachricht in der lokalen SQLite-Warteschlange.
 */
@Entity(tableName = "messages_queue")
data class MessageEntity(
    @PrimaryKey
    val customId: String,       // SHA-256 Hash (Deduplizierung)
    val sender: String,         // Name des Kontakts
    val text: String,           // Inhalt der Nachricht
    val timestamp: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false // false = PENDING, true = SUCCESSFULLY SENT
)