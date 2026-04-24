package com.example.whatsappagent

import android.app.Notification
import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.whatsappagent.data.AppDatabase
import com.example.whatsappagent.data.MessageEntity
import com.example.whatsappagent.worker.WorkManagerHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.security.MessageDigest
import kotlin.random.Random

class WhatsAppListener : NotificationListenerService() {

    companion object {
        const val ACTION_SEND_REPLY = "com.example.whatsappagent.SEND_REPLY"
        const val EXTRA_SENDER = "sender"
        const val EXTRA_REPLY = "reply"
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.IO)

    // Filter-Einstellungen
    private val BLACKLIST = setOf(
        "Bank", "Sparkasse", "Telekom", "Amazon",
        "Amt", "Behörde", "Polizei", "Arzt", "ابو زاهر"
    )

    // In-Memory Cache für die aktiven Benachrichtigungen (für RemoteInput)
    private val lastNotification = mutableMapOf<String, StatusBarNotification>()

    // Schutzmechanismen gegen Spam & Loops
    private val processedKeys = mutableSetOf<String>()
    private val sentReplies = mutableSetOf<String>()
    private val lastReplySentAt = mutableMapOf<String, Long>()
    private val MIN_REPLY_INTERVAL_MS = 60_000L

    // =========================
    // BROADCAST RECEIVER FÜR DEN WORKER
    // =========================
    private val replyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val sender = intent?.getStringExtra(EXTRA_SENDER) ?: return
            val reply = intent.getStringExtra(EXTRA_REPLY) ?: return

            val delaySeconds = Random.nextInt(5, 30)
            AgentLogger.log(AgentLogger.LogType.WAIT, "⏱ Warte ${delaySeconds}s vor Antwort an $sender...")

            mainHandler.postDelayed({
                sendReplyViaRemoteInput(sender, reply)
            }, delaySeconds * 1000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        AgentLogger.init(applicationContext)
        // Receiver registrieren, um Antworten vom SyncWorker zu empfangen
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(replyReceiver, IntentFilter(ACTION_SEND_REPLY))
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(replyReceiver)
    }

    // =========================
    // NOTIFICATION INTERCEPTION
    // =========================
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != "com.whatsapp.w4b") return

        val extras = sbn.notification.extras
        val sender = extras.getString("android.title") ?: return
        val textRaw = extras.getCharSequence("android.text")?.toString() ?: return
        val subText = extras.getCharSequence("android.subText")?.toString()

        if (isSummaryNotification(textRaw, sbn)) return
        if (isBlacklisted(sender)) return
        if (isGroupMessage(sender, textRaw, subText)) return
        if (isNonTextMessage(textRaw)) return
        if (isReaction(textRaw)) return
        if (textRaw.isBlank() || textRaw.contains("Nachricht gelöscht")) return

        // Self-Loop Schutz (Ignoriert Nachrichten, die wir selbst gerade gesendet haben)
        if (sentReplies.contains(textRaw.trim().lowercase())) {
            sentReplies.remove(textRaw.trim().lowercase())
            return
        }

        // Doppel-Antwort Schutz
        val lastReply = lastReplySentAt[sender] ?: 0L
        if (System.currentTimeMillis() - lastReply < MIN_REPLY_INTERVAL_MS) {
            AgentLogger.log(AgentLogger.LogType.INFO, "⏭ Zu schnell für $sender, ignoriert")
            return
        }

        // Duplikat-Schutz (In-Memory)
        val shortTermKey = "$sender:$textRaw"
        if (processedKeys.contains(shortTermKey)) return
        processedKeys.add(shortTermKey)
        if (processedKeys.size > 50) processedKeys.clear()

        // Cache für spätere Antworten aktualisieren
        lastNotification[sender] = sbn

        // Deterministische ID generieren (für DB und Backend)
        val timestamp = System.currentTimeMillis()
        val customId = generateHash("$sender$textRaw$timestamp")

        AgentLogger.log(AgentLogger.LogType.MESSAGE, "📨 $sender: $textRaw")

        // Offline-Safe Persistenz: Asynchron in DB speichern & Worker triggern
        scope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val messageEntry = MessageEntity(
                customId = customId,
                sender = sender,
                text = textRaw,
                timestamp = timestamp,
                isSynced = false
            )

            val resultId = db.messageDao().insertMessage(messageEntry)

            if (resultId != -1L) {
                AgentLogger.log(AgentLogger.LogType.INFO, "📥 In Queue gespeichert: $customId")
                WorkManagerHelper.triggerSync(applicationContext)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName != "com.whatsapp.w4b") return
        val sender = sbn.notification.extras.getString("android.title") ?: return
        if (lastNotification[sender]?.id == sbn.id) {
            lastNotification.remove(sender)
        }
    }

    // =========================
    // REPLY VIA REMOTEINPUT
    // =========================
    private fun sendReplyViaRemoteInput(sender: String, reply: String) {
        val sbn = lastNotification[sender] ?: run {
            AgentLogger.log(AgentLogger.LogType.ERROR, "❌ Keine Notification für $sender -> Fallback")
            fallbackOpenChat(null, sender, reply) // Wir versuchen das Fallback auch ohne SBN
            return
        }

        val replyAction = findReplyAction(sbn.notification) ?: run {
            AgentLogger.log(AgentLogger.LogType.ERROR, "❌ Kein Reply-Button gefunden -> Fallback")
            fallbackOpenChat(sbn, sender, reply)
            return
        }

        val remoteInput = replyAction.remoteInputs?.firstOrNull() ?: run {
            AgentLogger.log(AgentLogger.LogType.ERROR, "❌ Kein RemoteInput -> Fallback")
            fallbackOpenChat(sbn, sender, reply)
            return
        }

        val bundle = Bundle().apply {
            putCharSequence(remoteInput.resultKey, reply)
        }

        val intent = Intent().apply {
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            RemoteInput.addResultsToIntent(replyAction.remoteInputs, this, bundle)
        }

        try {
            // Self-Loop Schutz Cache
            sentReplies.add(reply.trim().lowercase())
            if (sentReplies.size > 20) sentReplies.clear()

            replyAction.actionIntent.send(applicationContext, 0, intent)
            lastReplySentAt[sender] = System.currentTimeMillis()
            AgentLogger.log(AgentLogger.LogType.REPLY, "✅ Reply gesendet an $sender: $reply")

            lastNotification.remove(sender)
        } catch (e: Exception) {
            AgentLogger.log(AgentLogger.LogType.ERROR, "❌ RemoteInput Fehler: ${e.message} -> Fallback")
            fallbackOpenChat(sbn, sender, reply)
        }
    }

    private fun findReplyAction(notification: Notification): Notification.Action? {
        val actions = notification.actions ?: return null
        actions.firstOrNull { it.remoteInputs?.isNotEmpty() == true }?.let { return it }

        try {
            val wearableExtender = Notification.WearableExtender(notification)
            wearableExtender.actions.firstOrNull { it.remoteInputs?.isNotEmpty() == true }
                ?.let { return it }
        } catch (e: Exception) {
            AgentLogger.log(AgentLogger.LogType.INFO, "Wearable Extender nicht verfügbar")
        }
        return null
    }

    // =========================
    // FALLBACK: ACCESSIBILITY UI AUTOMATION
    // =========================
    private fun fallbackOpenChat(sbn: StatusBarNotification?, sender: String, reply: String) {
        AgentLogger.log(AgentLogger.LogType.INFO, "🔄 Fallback Chat-Öffnen für $sender...")

        try {
            if (sbn != null && sbn.notification.contentIntent != null) {
                // Versuche Chat über die Benachrichtigung zu öffnen
                sbn.notification.contentIntent.send(
                    applicationContext,
                    0,
                    Intent().apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP) }
                )
            } else {
                AgentLogger.log(AgentLogger.LogType.ERROR, "❌ SBN null, kann Chat nicht öffnen.")
                return
            }
        } catch (e: Exception) {
            AgentLogger.log(AgentLogger.LogType.ERROR, "❌ Fallback fehlgeschlagen: ${e.message}")
            return
        }

        mainHandler.postDelayed({
            attemptAccessibilityReply(sender, reply, attempt = 1, maxAttempts = 8)
        }, 3000L)
    }

    private fun attemptAccessibilityReply(sender: String, reply: String, attempt: Int, maxAttempts: Int) {
        mainHandler.postDelayed({
            val autoReply = AutoReplyService.instance
            if (autoReply == null) {
                AgentLogger.log(AgentLogger.LogType.ERROR, "❌ AutoReplyService nicht aktiv!")
                return@postDelayed
            }

            val success = autoReply.tryReply(reply)

            if (success) {
                lastReplySentAt[sender] = System.currentTimeMillis()
                AgentLogger.log(AgentLogger.LogType.REPLY, "✅ Accessibility Reply an $sender")
            } else if (attempt < maxAttempts) {
                AgentLogger.log(AgentLogger.LogType.INFO, "🔄 Versuch $attempt fehlgeschlagen, retry...")
                attemptAccessibilityReply(sender, reply, attempt + 1, maxAttempts)
            } else {
                AgentLogger.log(AgentLogger.LogType.ERROR, "❌ Nach $maxAttempts Versuchen fehlgeschlagen")
            }
        }, 2000L)
    }

    // =========================
    // HILFSFUNKTIONEN
    // =========================
    private fun generateHash(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun isNonTextMessage(text: String): Boolean {
        val patterns = listOf(
            "🎤", "sprachnachricht", "voice message",
            "📷", "📸", "foto", "photo", "image", "bild",
            "📹", "video", "📄", "dokument", "document",
            "sticker", "gif", "📞", "verpasster anruf",
            "missed call", "eingehender anruf", "kontakt", "contact",
            "📍", "standort", "location"
        )
        val lower = text.trim().lowercase()
        return patterns.any { it in lower }
    }

    private fun isReaction(text: String): Boolean {
        val lower = text.trim().lowercase()
        return lower.contains("auf deine nachricht") ||
                lower.contains("reacted to") ||
                lower.contains("reagierte auf") ||
                (text.trim().length <= 2 && text.trim().isNotEmpty())
    }

    private fun isSummaryNotification(text: String, sbn: StatusBarNotification): Boolean {
        if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return true
        val patterns = listOf(
            Regex("^\\d+\\s+neue\\s+Nachrichten?$", RegexOption.IGNORE_CASE),
            Regex("^\\d+\\s+new\\s+messages?$", RegexOption.IGNORE_CASE),
            Regex("^\\d+\\s+Nachrichten?$", RegexOption.IGNORE_CASE),
            Regex("^\\d+\\s+messages?$", RegexOption.IGNORE_CASE),
            Regex(".*aus \\d+ Chats.*", RegexOption.IGNORE_CASE),
            Regex(".*from \\d+ chats.*", RegexOption.IGNORE_CASE)
        )
        return patterns.any { it.containsMatchIn(text.trim()) }
    }

    private fun isBlacklisted(sender: String) = BLACKLIST.any { it.lowercase() in sender.lowercase() }

    private fun isGroupMessage(sender: String, text: String, subText: String?): Boolean {
        if (!subText.isNullOrEmpty()) return true
        if (sender.contains(Regex("\\(\\d+.*\\)"))) return true
        val senderWords = sender.trim().split(Regex("\\s+"))
        if (senderWords.size >= 3) return true
        val groupKeywords = listOf(
            "gruppe", "group", "team", "chat", "klasse", "kurs",
            "hka", "uni", "schule", "restaurant", "küche", "kitchen",
            "work", "office", "firma", "betrieb", "service", "staff",
            "gmbh", "ag ", " kg", "gesellschaft", "مجموعة", "فريق",
            "مطبخ", "مدرسة", "جامعة", "عمل", "شركة", "طاقم", "صف", "دورة"
        )
        if (groupKeywords.any { it in sender.lowercase() }) return true
        val colonIndex = text.indexOf(":")
        if (colonIndex in 2..40) {
            val beforeColon = text.substring(0, colonIndex).trim()
            if (!beforeColon.contains("+") && !beforeColon.contains("http")) return true
        }
        return false
    }
}