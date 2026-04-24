package com.example.whatsappagent.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MessageDao {

    /**
     * Fügt eine neue Nachricht in die Warteschlange ein.
     * Konflikt-Strategie IGNORE: Wenn die customId bereits existiert (Self-Loop oder Duplikat),
     * wird der Insert-Vorgang ignoriert.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMessage(message: MessageEntity): Long

    /**
     * Ruft alle Nachrichten ab, die noch nicht an das Backend gesendet wurden.
     * Sortiert nach Zeitstempel (älteste zuerst, FIFO-Prinzip).
     */
    @Query("SELECT * FROM messages_queue WHERE isSynced = 0 ORDER BY timestamp ASC")
    suspend fun getPendingMessages(): List<MessageEntity>

    /**
     * Markiert eine spezifische Nachricht als erfolgreich synchronisiert.
     */
    @Query("UPDATE messages_queue SET isSynced = 1 WHERE customId = :id")
    suspend fun markAsSynced(id: String): Int

    /**
     * (Optional) Bereinigt die Datenbank, um unendliches Wachstum zu verhindern.
     * Löscht alle synchronisierten Nachrichten, die älter als ein bestimmter Timestamp sind.
     */
    @Query("DELETE FROM messages_queue WHERE isSynced = 1 AND timestamp < :thresholdMillis")
    suspend fun deleteOldSyncedMessages(thresholdMillis: Long): Int
}