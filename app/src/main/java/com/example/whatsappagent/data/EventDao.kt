package com.example.whatsappagent.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for events operations
 */
@Dao
interface EventDao {
    
    @Query("SELECT * FROM events ORDER BY createdAt DESC")
    fun getAllEvents(): Flow<List<EventEntity>>
    
    @Query("SELECT * FROM events WHERE active = 1 ORDER BY createdAt DESC")
    fun getActiveEvents(): Flow<List<EventEntity>>
    
    @Query("SELECT * FROM events WHERE id = :eventId")
    suspend fun getEventById(eventId: Long): EventEntity?
    
    @Query("SELECT * FROM events WHERE expiresAtMillis IS NOT NULL AND expiresAtMillis < :currentTime")
    suspend fun getExpiredEvents(currentTime: Long): List<EventEntity>
    
    @Insert
    suspend fun insertEvent(event: EventEntity): Long
    
    @Update
    suspend fun updateEvent(event: EventEntity)
    
    @Delete
    suspend fun deleteEvent(event: EventEntity)
    
    @Query("DELETE FROM events WHERE id = :eventId")
    suspend fun deleteEventById(eventId: Long)
    
    @Query("UPDATE events SET active = NOT active WHERE id = :eventId")
    suspend fun toggleEventActive(eventId: Long)
    
    @Query("UPDATE events SET sentCount = sentCount + 1 WHERE id = :eventId")
    suspend fun incrementSentCount(eventId: Long)
    
    @Query("DELETE FROM events WHERE expiresAtMillis < :currentTime")
    suspend fun deleteExpiredEvents(currentTime: Long)
}
