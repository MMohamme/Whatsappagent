package com.example.whatsappagent.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room Entity for storing events that can be sent to multiple contacts
 * These are group messages that can be triggered manually
 */
@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val emoji: String,
    val message: String,
    val targetCategory: String, // "freunde", "familie", "arbeit", etc.
    val expiresAtMillis: Long? = null,
    val active: Boolean = true,
    val sentCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
