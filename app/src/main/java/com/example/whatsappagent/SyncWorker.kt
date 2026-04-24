package com.example.whatsappagent.worker

import android.content.Context
import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.whatsappagent.AgentLogger
import com.example.whatsappagent.WhatsAppListener
import com.example.whatsappagent.data.AppDatabase
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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

        // 1. Alle ausstehenden Nachrichten holen (FIFO)
        val pendingMessages = dao.getPendingMessages()

        if (pendingMessages.isEmpty()) {
            return Result.success()
        }

        AgentLogger.log(AgentLogger.LogType.INFO, "🔄 SyncWorker gestartet: ${pendingMessages.size} Nachrichten in der Queue.")

        var allSuccessful = true

        for (msg in pendingMessages) {
            try {
                // 2. JSON Payload für FastAPI bauen
                val json = JSONObject().apply {
                    put("custom_id", msg.customId)
                    put("sender", msg.sender)
                    put("text", msg.text)
                }

                val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
                val request = Request.Builder()
                    .url("$BASE_URL/generate")
                    .post(body)
                    .addHeader("ngrok-skip-browser-warning", "1")
                    .build()

                // 3. Synchroner Call an das Backend
                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    val rawBody = response.body?.string()
                    val reply = rawBody?.let { JSONObject(it).optString("reply") }

                    // --- DEINE LOGIK HIER ---
                    if (!reply.isNullOrBlank() && reply != "Bereits verarbeitet") {
                        // 1. In DB als synchronisiert markieren
                        dao.markAsSynced(msg.customId)
                        AgentLogger.log(AgentLogger.LogType.BACKEND, "✅ Gesendet & Gespeichert: ${msg.customId}")

                        // 2. Broadcast an den WhatsAppListener senden!
                        val intent = Intent(WhatsAppListener.ACTION_SEND_REPLY).apply {
                            putExtra(WhatsAppListener.EXTRA_SENDER, msg.sender)
                            putExtra(WhatsAppListener.EXTRA_REPLY, reply)
                        }
                        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)

                    } else if (reply == "Bereits verarbeitet") {
                        // Backend hat sie schon, also aus lokaler Queue entfernen
                        dao.markAsSynced(msg.customId)
                        AgentLogger.log(AgentLogger.LogType.INFO, "⏭ Duplikat vom Backend bestätigt: ${msg.customId}")
                    }
                    // -------------------------

                } else {
                    AgentLogger.log(AgentLogger.LogType.ERROR, "❌ HTTP Error ${response.code} für ${msg.customId}")
                    allSuccessful = false
                }

            } catch (e: IOException) {
                AgentLogger.log(AgentLogger.LogType.ERROR, "❌ Netzwerkfehler im Worker: ${e.message}")
                allSuccessful = false
                break // Schleife abbrechen, wir haben kein Internet, Rest bleibt in der Queue
            } catch (e: Exception) {
                AgentLogger.log(AgentLogger.LogType.ERROR, "❌ Unerwarteter Fehler im Worker: ${e.message}")
                allSuccessful = false
            }
        }

        // 5. Wenn auch nur eine Nachricht fehlschlägt, den Worker später erneut versuchen lassen
        return if (allSuccessful) Result.success() else Result.retry()
    }
}