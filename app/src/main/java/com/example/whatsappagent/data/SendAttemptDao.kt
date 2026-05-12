package com.example.whatsappagent.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SendAttemptDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAttempt(attempt: SendAttemptEntity)

    @Query("SELECT * FROM send_attempts WHERE draftId = :draftId ORDER BY updatedAt DESC")
    suspend fun getAttemptsForDraft(draftId: Long): List<SendAttemptEntity>

    @Query("UPDATE send_attempts SET status = :status, error = :error, sentAtMillis = :sentAtMillis, updatedAt = :updatedAt WHERE backendId = :backendId")
    suspend fun updateStatus(
        backendId: Long,
        status: MessageStatus,
        error: String?,
        sentAtMillis: Long?,
        updatedAt: Long = System.currentTimeMillis()
    )
}
