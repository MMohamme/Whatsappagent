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
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val BASE_URL = "https://humming-opposite-deforest.ngrok-free.dev"
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override suspend fun doWork(): Result {
        val db = AppDatabase.getDatabase(applicationContext)
        val dao = db.messageDao()

        val pendingMessages = dao.getPendingMessages()

        if (pendingMessages.isEmpty()) {
            return Result.success()
        }

        AgentLogger.log(AgentLogger.LogType.INFO, "🔄 SyncWorker: ${pendingMessages.size} Nachrichten in Queue.")

        var allSuccessful = true

        for (msg in pendingMessages) {
            try {
                // 1. Gesprächsverlauf aus Room holen (user + assistant, älteste zuerst)
                //    Die aktuelle Nachricht ist noch nicht committed, daher schließen
                //    wir sie über customId aus — sie kommt als "text" im Payload.
                val historyRaw = dao.getLastMessages(msg.sender, limit = 12).reversed()
                val history = historyRaw.filter { it.customId != msg.customId }

                val historyArray = JSONArray()
                for (h in history) {
                    historyArray.put(JSONObject().apply {
                        put("role", h.role)   // "user" oder "assistant"
                        put("text", h.text)
                    })
                }

                // 2. Payload bauen
                val json = JSONObject().apply {
                    put("custom_id", msg.customId)
                    put("sender", msg.sender)
                    put("text", msg.text)
                    put("history", historyArray)
                }

                val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
                val request = Request.Builder()
                    .url("$BASE_URL/generate")
                    .post(body)
                    .addHeader("ngrok-skip-browser-warning", "1")
                    .build()

                // 3. API-Call
                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    val rawBody = response.body?.string()
                    val reply = rawBody?.let { JSONObject(it).optString("reply") }

                    when {
                        !reply.isNullOrBlank() && reply != "Bereits verarbeitet" -> {
                            // User-Nachricht als synced markieren
                            dao.markAsSynced(msg.customId)

                            // Agent-Antwort als "assistant"-Eintrag in Room speichern
                            // (sender = gleicher Kontakt, damit getLastMessages() sie mitliefert)
                            val replyEntity = MessageEntity(
                                customId = "reply_${msg.customId}",
                                sender = msg.sender,
                                text = reply,
                                role = "assistant",
                                isSynced = true   // ist bereits "gesendet", muss nicht nochmal in Queue
                            )
                            dao.insertMessage(replyEntity)

                            AgentLogger.log(AgentLogger.LogType.BACKEND, "✅ Synced & Reply gespeichert: ${msg.sender}")

                            // Broadcast → WhatsAppListener sendet via RemoteInput
                            val intent = Intent(WhatsAppListener.ACTION_SEND_REPLY).apply {
                                putExtra(WhatsAppListener.EXTRA_SENDER, msg.sender)
                                putExtra(WhatsAppListener.EXTRA_REPLY, reply)
                            }
                            LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)
                        }
                        reply == "Bereits verarbeitet" -> {
                            dao.markAsSynced(msg.customId)
                            AgentLogger.log(AgentLogger.LogType.INFO, "⏭ Duplikat: ${msg.customId}")
                        }
                        else -> {
                            AgentLogger.log(AgentLogger.LogType.ERROR, "⚠️ Leere Antwort vom Backend für ${msg.customId}")
                            allSuccessful = false
                        }
                    }

                } else {
                    AgentLogger.log(AgentLogger.LogType.ERROR, "❌ HTTP ${response.code} für ${msg.customId}")
                    allSuccessful = false
                }

            } catch (e: IOException) {
                AgentLogger.log(AgentLogger.LogType.ERROR, "❌ Netzwerkfehler: ${e.message}")
                allSuccessful = false
                break
            } catch (e: Exception) {
                AgentLogger.log(AgentLogger.LogType.ERROR, "❌ Fehler im Worker: ${e.message}")
                allSuccessful = false
            }
        }

        return if (allSuccessful) Result.success() else Result.retry()
    }
}