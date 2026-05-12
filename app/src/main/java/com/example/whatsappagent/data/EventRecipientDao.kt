package com.example.whatsappagent.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EventRecipientDao {
    @Query("SELECT * FROM event_recipients WHERE ticketId = :ticketId ORDER BY scheduledAtMillis ASC")
    fun getRecipientsForTicketFlow(ticketId: Long): Flow<List<EventRecipientEntity>>

    @Query("SELECT * FROM event_recipients WHERE status = :status ORDER BY scheduledAtMillis ASC")
    suspend fun getRecipientsByStatus(status: MessageStatus): List<EventRecipientEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecipients(recipients: List<EventRecipientEntity>)

    @Query("UPDATE event_recipients SET status = :status, error = :error, updatedAt = :updatedAt WHERE backendId = :backendId")
    suspend fun updateStatus(backendId: Long, status: MessageStatus, error: String?, updatedAt: Long = System.currentTimeMillis())
}
