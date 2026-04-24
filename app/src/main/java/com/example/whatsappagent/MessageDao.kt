package com.example.whatsappagent.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MessageDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMessage(message: MessageEntity): Long

    @Query("SELECT * FROM messages_queue WHERE isSynced = 0 ORDER BY timestamp ASC")
    suspend fun getPendingMessages(): List<MessageEntity>

    @Query("UPDATE messages_queue SET isSynced = 1 WHERE customId = :id")
    suspend fun markAsSynced(id: String): Int

    /**
     * Holt die letzten N Nachrichten eines Kontakts — user UND assistant —
     * sortiert neueste zuerst. Im SyncWorker wird die Liste danach umgekehrt
     * (älteste zuerst), damit Gemini den Dialog chronologisch liest.
     */
    @Query("SELECT * FROM messages_queue WHERE sender = :sender ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getLastMessages(sender: String, limit: Int): List<MessageEntity>

    @Query("DELETE FROM messages_queue WHERE isSynced = 1 AND timestamp < :thresholdMillis")
    suspend fun deleteOldSyncedMessages(thresholdMillis: Long): Int
}