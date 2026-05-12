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

    @TypeConverter
    fun fromNoteType(type: NoteType): String = type.name

    @TypeConverter
    fun toNoteType(type: String): NoteType {
        return try {
            NoteType.valueOf(type)
        } catch (e: Exception) {
            NoteType.CUSTOM
        }
    }

    @TypeConverter
    fun fromNoteScope(scope: NoteScope): String = scope.name

    @TypeConverter
    fun toNoteScope(scope: String): NoteScope {
        return try {
            NoteScope.valueOf(scope)
        } catch (e: Exception) {
            NoteScope.CONTACT
        }
    }
}
