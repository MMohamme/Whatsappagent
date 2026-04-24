package com.example.whatsappagent.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Repräsentiert eine Nachricht in der lokalen SQLite-Warteschlange.
 *
 * role:
 *   "user"      → eingehende Nachricht vom Kontakt
 *   "assistant" → vom Agenten generierte und gesendete Antwort
 */
@Entity(tableName = "messages_queue")
data class MessageEntity(
    @PrimaryKey
    val customId: String,
    val sender: String,
    val text: String,
    val role: String = "user",          // NEU: "user" oder "assistant"
    val timestamp: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false
)