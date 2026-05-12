package com.example.whatsappagent.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "send_attempts")
data class SendAttemptEntity(
    @PrimaryKey
    val backendId: Long,
    val draftId: Long,
    val channel: String,
    val status: MessageStatus = MessageStatus.SEND_PENDING,
    val error: String? = null,
    val attemptCount: Int = 0,
    val sentAtMillis: Long? = null,
    val updatedAt: Long = System.currentTimeMillis()
)
