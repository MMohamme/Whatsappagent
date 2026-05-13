package com.example.whatsappagent.worker

import android.content.Context
import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.whatsappagent.AgentLogger
import com.example.whatsappagent.AgentSafetySettings
import com.example.whatsappagent.BuildConfig
import com.example.whatsappagent.WhatsAppListener
import com.example.whatsappagent.data.AppDatabase
import com.example.whatsappagent.data.EventRecipientEntity
import com.example.whatsappagent.data.MessageEntity
import com.example.whatsappagent.data.MessageStatus
import com.example.whatsappagent.data.remote.AgentApiService
import com.example.whatsappagent.data.remote.InboundMessageRequest
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val apiService: AgentApiService by lazy {
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer ${BuildConfig.APP_API_TOKEN}")
                    .addHeader("ngrok-skip-browser-warning", "1")
                    .build()
                chain.proceed(request)
            }
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.HEADERS
            })
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl(BuildConfig.BACKEND_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AgentApiService::class.java)
    }

    override suspend fun doWork(): Result {
        val db = AppDatabase.getDatabase(applicationContext)
        val dao = db.messageDao()
        val cacheDao = db.contactCacheDao()
        val autoSendPaused = applicationContext
            .getSharedPreferences(AgentSafetySettings.PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(AgentSafetySettings.PREF_AUTO_SEND_PAUSED, false)
        val pendingMessages = dao.getPendingMessages()

        var allSuccessful = true
        if (pendingMessages.isNotEmpty()) {
            AgentLogger.log(AgentLogger.LogType.INFO, "SyncWorker: ${pendingMessages.size} Nachrichten in Queue.")
        }

        for (msg in pendingMessages) {
            try {
                var currentPhone = msg.phoneNumber
                if (currentPhone.isNullOrBlank()) {
                    currentPhone = cacheDao.getByName(msg.sender)?.phoneNumber
                }

                val settings = if (!currentPhone.isNullOrBlank()) {
                    db.contactSettingsDao().getContactSettingsByPhone(currentPhone)
                } else {
                    db.contactSettingsDao().getContactSettingsByName(msg.sender)
                }

                if (settings != null && !settings.isActive) {
                    AgentLogger.log(AgentLogger.LogType.INFO, "Ueberspringe ${msg.sender}: lokal deaktiviert")
                    dao.markAsSynced(msg.customId, MessageStatus.SKIPPED)
                    continue
                }

                val historyRaw = dao.getLastMessages(msg.sender, limit = 12).reversed()
                val historyItems = buildConversationHistory(historyRaw.filter { it.customId != msg.customId })
                    .map { (role, text) -> mapOf("role" to role, "text" to text) }

                dao.updateStatus(msg.customId, MessageStatus.SYNCING)

                val response = apiService.inboundMessage(
                    InboundMessageRequest(
                        customId = msg.customId,
                        senderDisplayName = msg.sender,
                        text = msg.text,
                        phoneNumber = currentPhone,
                        packageName = msg.packageName,
                        notificationKey = msg.notificationKey,
                        history = historyItems
                    )
                )

                if (!response.isSuccessful) {
                    AgentLogger.log(AgentLogger.LogType.ERROR, "HTTP ${response.code()} fuer ${msg.customId}")
                    dao.updateStatus(msg.customId, MessageStatus.FAILED)
                    allSuccessful = false
                    continue
                }

                val decision = response.body()
                val reply = decision?.reply
                when {
                    decision?.decision == "AUTO_SEND_ALLOWED" && !reply.isNullOrBlank() && !autoSendPaused -> {
                        dao.markAsSynced(msg.customId, MessageStatus.SEND_PENDING)
                        dao.insertMessage(
                            MessageEntity(
                                customId = "reply_${msg.customId}",
                                sender = msg.sender,
                                text = reply,
                                role = "assistant",
                                isSynced = true,
                                phoneNumber = currentPhone,
                                status = MessageStatus.SEND_PENDING,
                                backendMessageId = decision.messageId,
                                draftId = decision.draftId
                            )
                        )
                        val intent = Intent(WhatsAppListener.ACTION_SEND_REPLY).apply {
                            putExtra(WhatsAppListener.EXTRA_SENDER, msg.sender)
                            putExtra(WhatsAppListener.EXTRA_REPLY, reply)
                            putExtra(WhatsAppListener.EXTRA_CUSTOM_ID, msg.customId)
                            putExtra(WhatsAppListener.EXTRA_DRAFT_ID, decision.draftId ?: -1L)
                        }
                        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)
                    }

                    decision?.decision == "AUTO_SEND_ALLOWED" && !reply.isNullOrBlank() && autoSendPaused -> {
                        dao.markAsSynced(msg.customId, MessageStatus.NEEDS_REVIEW)
                        dao.insertMessage(
                            MessageEntity(
                                customId = "draft_${msg.customId}",
                                sender = msg.sender,
                                text = reply,
                                role = "assistant",
                                isSynced = true,
                                phoneNumber = currentPhone,
                                status = MessageStatus.NEEDS_REVIEW,
                                backendMessageId = decision.messageId,
                                draftId = decision.draftId
                            )
                        )
                        AgentLogger.log(AgentLogger.LogType.INFO, "Auto-Send pausiert: Draft fuer ${msg.sender} in Review")
                    }

                    decision?.decision == "NEEDS_REVIEW" -> {
                        dao.markAsSynced(msg.customId, MessageStatus.NEEDS_REVIEW)
                        if (!reply.isNullOrBlank()) {
                            dao.insertMessage(
                                MessageEntity(
                                    customId = "draft_${msg.customId}",
                                    sender = msg.sender,
                                    text = reply,
                                    role = "assistant",
                                    isSynced = true,
                                    phoneNumber = currentPhone,
                                    status = MessageStatus.NEEDS_REVIEW,
                                    backendMessageId = decision.messageId,
                                    draftId = decision.draftId
                                )
                            )
                        }
                        AgentLogger.log(AgentLogger.LogType.INFO, "Review fuer ${msg.sender}: ${decision.reason}")
                    }

                    decision?.decision == "BLOCKED" || decision?.decision == "IGNORE" -> {
                        dao.markAsSynced(msg.customId, MessageStatus.BLOCKED)
                        AgentLogger.log(AgentLogger.LogType.INFO, "Blockiert fuer ${msg.sender}: ${decision.reason}")
                    }

                    else -> {
                        dao.updateStatus(msg.customId, MessageStatus.FAILED)
                        allSuccessful = false
                    }
                }
            } catch (e: Exception) {
                AgentLogger.log(AgentLogger.LogType.ERROR, "Fehler im Worker (${msg.sender}): ${e.message}")
                allSuccessful = false
                if (e is IOException) break
            }
        }

        pollDueEventRecipients(db)

        return if (allSuccessful) {
            Result.success()
        } else if (runAttemptCount > 3) {
            Result.failure()
        } else {
            Result.retry()
        }
    }

    private suspend fun pollDueEventRecipients(db: AppDatabase) {
        try {
            val response = apiService.getDueEventRecipients()
            if (!response.isSuccessful) return
            val recipients = response.body().orEmpty()
            db.eventRecipientDao().upsertRecipients(
                recipients.map {
                    EventRecipientEntity(
                        backendId = it.id,
                        ticketId = it.ticketId,
                        contactId = it.contactId,
                        contactName = it.contactName ?: it.contactId.toString(),
                        draftId = it.draftId,
                        reply = it.draft?.reply,
                        status = runCatching { MessageStatus.valueOf(it.status) }.getOrDefault(MessageStatus.SEND_PENDING),
                        scheduledAtMillis = System.currentTimeMillis(),
                        error = it.error
                    )
                }
            )
            val autoSendPaused = applicationContext
                .getSharedPreferences(AgentSafetySettings.PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(AgentSafetySettings.PREF_AUTO_SEND_PAUSED, false)
            if (autoSendPaused) {
                AgentLogger.log(AgentLogger.LogType.INFO, "Auto-Send pausiert: EventRecipients werden nicht gesendet")
                return
            }
            recipients.forEach { recipient ->
                val reply = recipient.draft?.reply ?: return@forEach
                val sender = recipient.contactName ?: return@forEach
                val intent = Intent(WhatsAppListener.ACTION_SEND_REPLY).apply {
                    putExtra(WhatsAppListener.EXTRA_SENDER, sender)
                    putExtra(WhatsAppListener.EXTRA_REPLY, reply)
                    putExtra("is_event", true)
                    putExtra("event_recipient_id", recipient.id)
                    putExtra(WhatsAppListener.EXTRA_DRAFT_ID, recipient.draftId ?: -1L)
                }
                LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)
            }
        } catch (e: Exception) {
            AgentLogger.log(AgentLogger.LogType.ERROR, "Fehler beim EventRecipient-Polling: ${e.message}")
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
