package com.example.whatsappagent.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EventTicketDao {
    @Query("SELECT * FROM event_tickets ORDER BY scheduledAtMillis DESC")
    fun getTicketsFlow(): Flow<List<EventTicketEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTickets(tickets: List<EventTicketEntity>)

    @Query("DELETE FROM event_tickets")
    suspend fun clearTickets()
}
