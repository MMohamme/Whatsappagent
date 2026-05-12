package com.example.whatsappagent

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

object AgentLogger {

    const val ACTION_NEW_LOG = "com.example.whatsappagent.NEW_LOG"
    const val EXTRA_LOG_ENTRY = "log_entry"
    private const val LOG_FILE_NAME = "agent_logs.txt"
    private const val MAX_FILE_SIZE = 1 * 1024 * 1024 // 1 MB

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
        loadLogsFromFile()
    }

    private fun loadLogsFromFile() {
        val fileLogs = readLogFile(MAX_LOGS)
        logs.clear()
        fileLogs.forEach { line ->
            try {
                val parts = line.split("|")
                if (parts.size >= 3) {
                    val fullTime = parts[0]
                    val shortTime = if (fullTime.length > 11) fullTime.substring(11) else fullTime
                    val type = LogType.valueOf(parts[1])
                    val message = parts.subList(2, parts.size).joinToString("|")
                    logs.addLast(LogEntry(shortTime, type, message))
                }
            } catch (_: Exception) {}
        }
    }

    fun log(type: LogType, message: String) {
        val fullTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val shortTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val entry = LogEntry(shortTime, type, message)

        logs.addLast(entry)
        if (logs.size > MAX_LOGS) logs.removeFirst()

        // Android Logcat
        Log.d("WA_AGENT", "[$type] $message")

        val logLine = "$fullTime|${type.name}|$message"

        // File Log
        appendToFile(logLine)

        // Broadcast an UI
        appContext?.let { ctx ->
            val intent = Intent(ACTION_NEW_LOG).apply {
                putExtra(EXTRA_LOG_ENTRY, "$shortTime|${type.name}|$message")
            }
            LocalBroadcastManager.getInstance(ctx).sendBroadcast(intent)
        }
    }

    private fun appendToFile(line: String) {
        val context = appContext ?: return
        try {
            val file = File(context.filesDir, LOG_FILE_NAME)
            
            // Rotation
            if (file.exists() && file.length() > MAX_FILE_SIZE) {
                val backup = File(context.filesDir, "${LOG_FILE_NAME}.old")
                if (backup.exists()) backup.delete()
                file.renameTo(backup)
            }
            
            file.appendText("$line\n")
        } catch (e: IOException) {
            // Can't really log this without infinite recursion if we use log()
            Log.e("AgentLogger", "Failed to write to log file", e)
        }
    }

    fun readLogFile(maxLines: Int = 200): List<String> {
        val context = appContext ?: return emptyList()
        return try {
            val file = File(context.filesDir, LOG_FILE_NAME)
            if (file.exists()) {
                file.readLines().takeLast(maxLines)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getLogs(): List<LogEntry> = logs.toList()

    fun clear() = logs.clear()
}
