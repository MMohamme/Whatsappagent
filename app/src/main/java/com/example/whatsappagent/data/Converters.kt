package com.example.whatsappagent.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromMessageStatus(status: MessageStatus): String {
        return status.name
    }

    @TypeConverter
    fun toMessageStatus(status: String): MessageStatus {
        return try {
            MessageStatus.valueOf(status)
        } catch (e: Exception) {
            MessageStatus.CAPTURED
        }
    }
}
