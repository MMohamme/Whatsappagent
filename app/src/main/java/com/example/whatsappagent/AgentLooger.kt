package com.example.whatsappagent

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.text.SimpleDateFormat
import java.util.*

object AgentLogger {

    const val ACTION_NEW_LOG = "com.example.whatsappagent.NEW_LOG"
    const val EXTRA_LOG_ENTRY = "log_entry"

    private val logs = ArrayDeque<LogEntry>()
    private const val MAX_LOGS = 50

    private var appContext: Context? = null

    data class LogEntry(
        val time: String,
        val type: LogType,
        val message: String
    )

    enum class LogType {
        INFO,       // allgemeine Info
        MESSAGE,    // Nachricht empfangen
        REPLY,      // Antwort gesendet
        WAIT,       // wartet X Sekunden
        ERROR,      // Fehler
        BACKEND     // Backend Status
    }

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun log(type: LogType, message: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val entry = LogEntry(time, type, message)

        logs.addLast(entry)
        if (logs.size > MAX_LOGS) logs.removeFirst()

        // Android Logcat
        Log.d("WA_AGENT", "[$type] $message")

        // Broadcast an UI
        appContext?.let { ctx ->
            val intent = Intent(ACTION_NEW_LOG).apply {
                putExtra(EXTRA_LOG_ENTRY, "$time|${type.name}|$message")
            }
            LocalBroadcastManager.getInstance(ctx).sendBroadcast(intent)
        }
    }

    fun getLogs(): List<LogEntry> = logs.toList()

    fun clear() = logs.clear()
}