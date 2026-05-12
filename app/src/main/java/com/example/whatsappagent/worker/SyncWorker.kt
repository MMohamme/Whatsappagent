package com.example.whatsappagent.worker

import android.content.Context
import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.whatsappagent.AgentLogger
import com.example.whatsappagent.WhatsAppListener
import com.example.whatsappagent.data.AppDatabase
import com.example.whatsappagent.data.MessageEntity
import com.example.whatsappagent.data.MessageStatus
import com.example.whatsappagent.data.remote.AgentApiService
import com.example.whatsappagent.data.remote.MessageSchema
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val BASE_URL = "https://humming-opposite-deforest.ngrok-free.dev/"
    
    private val apiService: AgentApiService by lazy {
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.HEADERS
            })
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        retrofit.create(AgentApiService::class.java)
    }

    override suspend fun doWork(): Result {
        val db = AppDatabase.getDatabase(applicationContext)
        val dao = db.messageDao()
        val cacheDao = db.contactCacheDao()

        val pendingMessages = dao.getPendingMessages()

        if (pendingMessages.isEmpty()) {
            return Result.success()
        }

        AgentLogger.log(AgentLogger.LogType.INFO, "🔄 SyncWorker: ${pendingMessages.size} Nachrichten in Queue.")

        var allSuccessful = true

        for (msg in pendingMessages) {
            try {
                // Resolve phone number if missing
                var currentPhone = msg.phoneNumber
                if (currentPhone.isNullOrBlank()) {
                    val cached = cacheDao.getByName(msg.sender)
                    if (cached != null) {
                        currentPhone = cached.phoneNumber
                    }
                }

                // 0. Check if agent is active for this contact
                val settings = if (!currentPhone.isNullOrBlank()) {
                    db.contactSettingsDao().getContactSettingsByPhone(currentPhone)
                } else {
                    db.contactSettingsDao().getContactSettingsByName(msg.sender)
                }
                
                if (settings != null && !settings.isActive) {
                    AgentLogger.log(AgentLogger.LogType.INFO, "⏭ Überspringe ${msg.sender} (Agent deaktiviert)")
                    dao.markAsSynced(msg.customId) // Mark as "processed" so it doesn't stay in queue
                    continue
                }

                // 1. Gesprächsverlauf aus Room holen (user + assistant, älteste zuerst)
                val historyRaw = dao.getLastMessages(msg.sender, limit = 12).reversed()
                val historyEntities = historyRaw.filter { it.customId != msg.customId }
                val builtHistory = buildConversationHistory(historyEntities)

                val historyItems = builtHistory.map { (role, text) ->
                    mapOf("role" to role, "text" to text)
                }

                // Update status to SYNCING
                dao.updateStatus(msg.customId, MessageStatus.SYNCING)

                // 2. Payload bauen
                val schema = MessageSchema(
                    customId = msg.customId,
                    sender = msg.sender,
                    text = msg.text,
                    history = historyItems
                )

                // 3. API-Call via Retrofit
                val response = apiService.generateMessage(schema)

                if (response.isSuccessful) {
                    val reply = response.body()?.get("reply")

                    when {
                        !reply.isNullOrBlank() && reply != "Bereits verarbeitet" -> {
                            // User-Nachricht als synced markieren
                            dao.markAsSynced(msg.customId, MessageStatus.REPLY_PENDING)

                            // Agent-Antwort als "assistant"-Eintrag in Room speichern
                            val replyEntity = MessageEntity(
                                customId = "reply_${msg.customId}",
                                sender = msg.sender,
                                text = reply,
                                role = "assistant",
                                isSynced = true,
                                status = MessageStatus.REPLY_SENT,
                                phoneNumber = currentPhone
                            )
                            dao.insertMessage(replyEntity)

                            AgentLogger.log(AgentLogger.LogType.BACKEND, "✅ Synced & Reply gespeichert: ${msg.sender}")

                            // Broadcast → WhatsAppListener sendet via RemoteInput
                            val intent = Intent(WhatsAppListener.ACTION_SEND_REPLY).apply {
                                putExtra(WhatsAppListener.EXTRA_SENDER, msg.sender)
                                putExtra(WhatsAppListener.EXTRA_REPLY, reply)
                                putExtra(WhatsAppListener.EXTRA_CUSTOM_ID, msg.customId)
                            }
                            LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)
                        }
                        reply == "Bereits verarbeitet" -> {
                            dao.markAsSynced(msg.customId, MessageStatus.DONE)
                            AgentLogger.log(AgentLogger.LogType.INFO, "⏭ Duplikat: ${msg.customId}")
                        }
                        else -> {
                            AgentLogger.log(AgentLogger.LogType.ERROR, "⚠️ Leere Antwort vom Backend für ${msg.customId}")
                            dao.updateStatus(msg.customId, MessageStatus.REPLY_FAILED)
                            allSuccessful = false
                        }
                    }

                } else {
                    AgentLogger.log(AgentLogger.LogType.ERROR, "❌ HTTP ${response.code()} für ${msg.customId}")
                    dao.updateStatus(msg.customId, MessageStatus.REPLY_FAILED)
                    allSuccessful = false
                }

            } catch (e: Exception) {
                AgentLogger.log(AgentLogger.LogType.ERROR, "❌ Fehler im Worker (${msg.sender}): ${e.message}")
                allSuccessful = false
                if (e is IOException) break // Stop loop on network issues
            }
        }

        // --- Task 9: Event-Trigger-Polling ---
        pollAndTriggerEvents()

        return if (allSuccessful) {
            Result.success()
        } else {
            if (runAttemptCount > 3) {
                AgentLogger.log(AgentLogger.LogType.ERROR, "❌ SyncWorker: Zu viele Fehlversuche ($runAttemptCount). Breche ab.")
                Result.failure()
            } else {
                Result.retry()
            }
        }
    }

    private suspend fun pollAndTriggerEvents() {
        try {
            val response = apiService.getAllEvents(status = "TRIGGERED")
            if (response.isSuccessful) {
                val triggeredEvents = response.body() ?: emptyList()
                if (triggeredEvents.isNotEmpty()) {
                    AgentLogger.log(AgentLogger.LogType.INFO, "🔔 ${triggeredEvents.size} getriggerte Events gefunden.")
                }

                triggeredEvents.forEach { event ->
                    // Broadcast an WhatsAppListener
                    val intent = Intent(WhatsAppListener.ACTION_SEND_REPLY).apply {
                        putExtra(WhatsAppListener.EXTRA_SENDER, event.contactName)
                        putExtra(WhatsAppListener.EXTRA_REPLY, event.generatedText ?: "Hallo!")
                        putExtra("is_event", true)
                        putExtra("event_id", event.id)
                    }
                    LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)

                    // Mark as SENT im Backend
                    apiService.updateEvent(event.id, com.example.whatsappagent.data.remote.EventUpdate(status = "SENT"))
                    AgentLogger.log(AgentLogger.LogType.INFO, "✅ Event ${event.id} als SENT markiert.")
                }
            }
        } catch (e: Exception) {
            AgentLogger.log(AgentLogger.LogType.ERROR, "❌ Fehler beim Event-Polling: ${e.message}")
        }
    }

    private fun buildConversationHistory(messages: List<MessageEntity>): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        var lastRole = ""

        for (msg in messages.sortedBy { it.timestamp }) {
            if (msg.role == lastRole) {
                val lastEntry = result.lastOrNull()
                if (lastEntry != null) {
                    result[result.lastIndex] = Pair(lastRole, "${lastEntry.second}\n${msg.text}")
                }
            } else {
                result.add(Pair(msg.role, msg.text))
                lastRole = msg.role
            }
        }
        return result
    }
}
