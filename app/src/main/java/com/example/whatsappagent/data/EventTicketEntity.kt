package com.example.whatsappagent.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "event_tickets")
data class EventTicketEntity(
    @PrimaryKey
    val backendId: Long,
    val title: String,
    val targetType: String,
    val targetCategory: String? = null,
    val targetContactId: Long? = null,
    val scheduledAtMillis: Long,
    val baseText: String? = null,
    val prompt: String? = null,
    val status: String = "DRAFT",
    val recipientsCount: Int = 0,
    val updatedAt: Long = System.currentTimeMillis()
)
