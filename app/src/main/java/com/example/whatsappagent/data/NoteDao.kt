package com.example.whatsappagent.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for notes operations
 */
@Dao
interface NoteDao {
    
    @Query("SELECT * FROM notes WHERE contactName = :contactName ORDER BY createdAt DESC")
    fun getNotesForContact(contactName: String): Flow<List<NoteEntity>>
    
    @Query("SELECT * FROM notes ORDER BY createdAt DESC")
    fun getAllNotes(): Flow<List<NoteEntity>>
    
    @Query("SELECT * FROM notes WHERE expiresAtMillis IS NOT NULL AND expiresAtMillis < :currentTime")
    suspend fun getExpiredNotes(currentTime: Long): List<NoteEntity>
    
    @Insert
    suspend fun insertNote(note: NoteEntity)
    
    @Update
    suspend fun updateNote(note: NoteEntity)
    
    @Delete
    suspend fun deleteNote(note: NoteEntity)
    
    @Query("DELETE FROM notes WHERE id = :noteId")
    suspend fun deleteNoteById(noteId: Long)
    
    @Query("DELETE FROM notes WHERE contactName = :contactName")
    suspend fun deleteNotesForContact(contactName: String)
    
    @Query("DELETE FROM notes WHERE expiresAtMillis < :currentTime")
    suspend fun deleteExpiredNotes(currentTime: Long)
}
