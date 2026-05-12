package com.example.whatsappagent.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Insert(onConflict = OnConflictStrategy.Companion.IGNORE)
    suspend fun insertMessage(message: MessageEntity): Long

    @Query("SELECT * FROM messages_queue WHERE isSynced = 0 ORDER BY timestamp ASC")
    suspend fun getPendingMessages(): List<MessageEntity>

    @Query("UPDATE messages_queue SET isSynced = 1, status = :status WHERE customId = :id")
    suspend fun markAsSynced(id: String, status: MessageStatus = MessageStatus.DONE): Int

    @Query("UPDATE messages_queue SET status = :status WHERE customId = :id")
    suspend fun updateStatus(id: String, status: MessageStatus): Int

    @Query("SELECT * FROM messages_queue WHERE status = :status")
    suspend fun getMessagesByStatus(status: MessageStatus): List<MessageEntity>

    @Query("SELECT * FROM messages_queue WHERE status = 'REPLY_FAILED'")
    fun getFailedRepliesFlow(): Flow<List<MessageEntity>>

    /**
     * Holt die letzten N Nachrichten eines Kontakts — user UND assistant —
     * sortiert neueste zuerst. Im SyncWorker wird die Liste danach umgekehrt
     * (älteste zuerst), damit Gemini den Dialog chronologisch liest.
     */
    @Query("SELECT * FROM messages_queue WHERE sender = :sender ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getLastMessages(sender: String, limit: Int): List<MessageEntity>

    @Query("DELETE FROM messages_queue WHERE isSynced = 1 AND timestamp < :thresholdMillis")
    suspend fun deleteOldSyncedMessages(thresholdMillis: Long): Int

    // Flow-based queries for real-time UI updates
    @Query("SELECT * FROM messages_queue WHERE isSynced = 0 ORDER BY timestamp ASC")
    fun getPendingMessagesFlow(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages_queue ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentMessagesFlow(limit: Int): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages_queue WHERE sender = :sender ORDER BY timestamp DESC LIMIT :limit")
    fun getMessagesForSenderFlow(sender: String, limit: Int): Flow<List<MessageEntity>>

    @Query("SELECT DISTINCT sender FROM messages_queue ORDER BY timestamp DESC")
    fun getSendersFlow(): Flow<List<String>>

    // Statistics queries
    @Query("SELECT COUNT(*) FROM messages_queue WHERE status = 'REPLY_FAILED' AND timestamp > :since")
    fun countErrorsSince(since: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM messages_queue WHERE role = 'assistant' AND timestamp > :since")
    fun countRepliesSince(since: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM messages_queue WHERE isSynced = 0")
    fun countPendingFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM messages_queue WHERE timestamp > :since")
    fun countMessagesSince(since: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM messages_queue WHERE timestamp > :weekStart")
    fun countWeekReplies(weekStart: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM messages_queue WHERE role = 'assistant' AND timestamp > :since")
    fun countMonthReplies(since: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM messages_queue WHERE sender = :sender AND role = 'assistant' AND timestamp > :afterTimestamp")
    suspend fun hasManualReplyAfter(sender: String, afterTimestamp: Long): Int
}