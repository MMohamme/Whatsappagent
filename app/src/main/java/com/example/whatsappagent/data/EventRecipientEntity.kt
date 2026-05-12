package com.example.whatsappagent.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "event_recipients")
data class EventRecipientEntity(
    @PrimaryKey
    val backendId: Long,
    val ticketId: Long,
    val contactId: Long,
    val contactName: String,
    val draftId: Long? = null,
    val reply: String? = null,
    val status: MessageStatus = MessageStatus.NEEDS_REVIEW,
    val scheduledAtMillis: Long,
    val error: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)
