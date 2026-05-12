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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.security.MessageDigest
import kotlin.random.Random

class WhatsAppListener : NotificationListenerService() {

    companion object {
        const val ACTION_SEND_REPLY = "com.example.whatsappagent.SEND_REPLY"
        const val EXTRA_SENDER = "sender"
        const val EXTRA_REPLY = "reply"
        const val EXTRA_CUSTOM_ID = "custom_id"
        
        var isRunning = false // Static tracking
        var instance: WhatsAppListener? = null
        var lastRebindRequestAt = 0L
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
    
    // NEU: Sofortige Extraktion der Notification-Daten
    data class NotificationData(
        val sbn: StatusBarNotification,
        val replyAction: Notification.Action?,
        val contentIntent: android.app.PendingIntent?,
        val capturedAt: Long = System.currentTimeMillis() // NEU
    )
    private val extractedNotifications = mutableMapOf<String, NotificationData>()

    // Schutzmechanismen gegen Spam & Loops
    private val processedPrefs by lazy {
        getSharedPreferences("processed_keys", Context.MODE_PRIVATE)
    }
    private val sentReplies = mutableSetOf<String>()
    private val lastReplySentAt = mutableMapOf<String, Long>()
    private val MIN_REPLY_INTERVAL_MS = 60_000L

    private fun isDuplicate(key: String): Boolean {
        val timestamp = processedPrefs.getLong(key, 0L)
        if (timestamp == 0L) return false
        // 5 minutes TTL
        return System.currentTimeMillis() - timestamp < 5 * 60 * 1000L
    }

    private fun markAsProcessed(key: String) {
        val allKeys = processedPrefs.all.keys
        if (allKeys.size > 100) {
            // Cleanup: remove older half
            val editor = processedPrefs.edit()
            allKeys.toList().sortedBy { processedPrefs.getLong(it, 0L) }
                .take(50)
                .forEach { editor.remove(it) }
            editor.apply()
        }
        processedPrefs.edit().putLong(key, System.currentTimeMillis()).apply()
    }

    // =========================
    // BROADCAST RECEIVER FÜR DEN WORKER
    // =========================
    private val replyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val sender = intent?.getStringExtra(EXTRA_SENDER) ?: return
            val reply = intent.getStringExtra(EXTRA_REPLY) ?: return
            val customId = intent.getStringExtra(EXTRA_CUSTOM_ID) ?: ""
            val isEvent = intent.getBooleanExtra("is_event", false)

            scope.launch {
                val db = AppDatabase.getDatabase(applicationContext)
                val cached = db.contactCacheDao().getByName(sender)
                val phone = cached?.phoneNumber ?: sender
                
                val settings = db.contactSettingsDao().getContactSettingsByPhone(phone)
                val delayMin = settings?.delayMin?.toLong() ?: 5000L
                val delayMax = settings?.delayMax?.toLong() ?: 30000L
                
                val delayMs = if (delayMin < delayMax) {
                    Random.nextLong(delayMin, delayMax)
                } else {
                    delayMin
                }

                val logType = if (isEvent) "EVENT_REPLY" else "SYNC_REPLY"
                AgentLogger.log(AgentLogger.LogType.WAIT, "⏱ [$logType] Warte ${delayMs/1000}s vor Antwort an $sender...")

                mainHandler.postDelayed({
                    sendReplyViaRemoteInput(sender, reply, customId)
                }, delayMs)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        AgentLogger.init(applicationContext)
        // Receiver registrieren, um Antworten vom SyncWorker zu empfangen
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(replyReceiver, IntentFilter(ACTION_SEND_REPLY))
        
        // Retry Worker einplanen
        WorkManagerHelper.scheduleRetryCheck(applicationContext)
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(replyReceiver)
        scope.cancel()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        isRunning = true
        AgentLogger.log(AgentLogger.LogType.INFO, "WhatsApp Listener verbunden ✅")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        instance = null
        isRunning = false
        AgentLogger.log(AgentLogger.LogType.INFO, "WhatsApp Listener getrennt ❌")
    }

    // =========================
    // NOTIFICATION INTERCEPTION
    // =========================
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != "com.whatsapp.w4b" && sbn.packageName != "com.whatsapp") return

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

        // Duplikat-Schutz (Persistent via SharedPreferences)
        val shortTermKey = "$sender:$textRaw"
        if (isDuplicate(shortTermKey)) return
        markAsProcessed(shortTermKey)

        // Cache für spätere Antworten aktualisieren
        lastNotification[sender] = sbn
        extractedNotifications[sender] = NotificationData(
            sbn = sbn,
            replyAction = findReplyAction(sbn.notification),
            contentIntent = sbn.notification.contentIntent
        )

        // Wake screen if off
        DeviceControl.wakeScreen(applicationContext)

        // Deterministische ID generieren (für DB und Backend)
        val timestamp = System.currentTimeMillis()
        val customId = generateHash("$sender$textRaw$timestamp")

        AgentLogger.log(AgentLogger.LogType.MESSAGE, "📨 $sender: $textRaw")

        // Offline-Safe Persistenz: Asynchron in DB speichern & Worker triggern
        scope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            
            // Resolve phone number if missing
            var resolvedPhone: String? = null
            val cachedContact = db.contactCacheDao().getByName(sender)
            if (cachedContact != null) {
                resolvedPhone = cachedContact.phoneNumber
            }

            // Check if agent is active for this sender
            val settings = if (resolvedPhone != null) {
                db.contactSettingsDao().getContactSettingsByPhone(resolvedPhone)
            } else {
                db.contactSettingsDao().getContactSettingsByName(sender)
            }

            if (settings != null && !settings.isActive) {
                AgentLogger.log(AgentLogger.LogType.INFO, "⏭ Ignoriert: Agent für $sender deaktiviert")
                return@launch
            }

            // Create default settings if new contact
            if (settings == null) {
                db.contactSettingsDao().insertOrUpdateContactSettings(
                    com.example.whatsappagent.data.ContactSettingsEntity(
                        phoneNumber = resolvedPhone ?: sender,
                        contactName = sender,
                        isActive = true,
                        category = "UNKNOWN",
                        preferredLang = "de",
                        delayMin = 5000,
                        delayMax = 15000
                    )
                )
                AgentLogger.log(AgentLogger.LogType.INFO, "👤 Neuer Kontakt angelegt: $sender")
            }

            val messageEntry = MessageEntity(
                customId = customId,
                sender = sender,
                text = textRaw,
                timestamp = timestamp,
                isSynced = false,
                phoneNumber = resolvedPhone
            )

            val resultId = db.messageDao().insertMessage(messageEntry)

            if (resultId != -1L) {
                AgentLogger.log(AgentLogger.LogType.INFO, "📥 In Queue gespeichert: $customId")
                WorkManagerHelper.triggerSync(applicationContext)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName != "com.whatsapp.w4b" && sbn.packageName != "com.whatsapp") return
        val sender = sbn.notification.extras.getString("android.title") ?: return
        if (lastNotification[sender]?.id == sbn.id) {
            lastNotification.remove(sender)
            // Wir entfernen extrahierter Daten NICHT sofort, damit delayed replies noch funktionieren
            mainHandler.postDelayed({
                extractedNotifications.remove(sender)
            }, 60_000L) // 60s Puffer
        }
    }

    // =========================
    // REPLY VIA REMOTEINPUT
    // =========================
    private fun sendReplyViaRemoteInput(sender: String, reply: String, customId: String) {
        val data = extractedNotifications[sender] ?: run {
            AgentLogger.log(AgentLogger.LogType.ERROR, "❌ Keine extrahierte Notification für $sender -> Fallback")
            fallbackOpenChat(null, sender, reply, customId)
            return
        }

        // Check age of notification (MAX 10 minutes)
        if (System.currentTimeMillis() - data.capturedAt > 10 * 60 * 1000L) {
            AgentLogger.log(AgentLogger.LogType.INFO, "🕒 Notification zu alt (>10min) -> Fallback")
            fallbackOpenChat(data.sbn, sender, reply, customId)
            return
        }

        val replyAction = data.replyAction ?: run {
            AgentLogger.log(AgentLogger.LogType.ERROR, "❌ Kein Reply-Button für $sender -> Fallback")
            fallbackOpenChat(data.sbn, sender, reply, customId)
            return
        }

        val remoteInput = replyAction.remoteInputs?.firstOrNull() ?: run {
            AgentLogger.log(AgentLogger.LogType.ERROR, "❌ Kein RemoteInput für $sender -> Fallback")
            fallbackOpenChat(data.sbn, sender, reply, customId)
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

            if (customId.isNotEmpty()) {
                scope.launch {
                    val db = AppDatabase.getDatabase(applicationContext)
                    db.messageDao().updateStatus(customId, com.example.whatsappagent.data.MessageStatus.REPLY_SENT)
                    
                    // Sync backend status to fix Dashboard/Queue inconsistency
                    val repository = (application as AgentApplication).repository
                    repository.updateMessageStatus(customId, "REPLY_SENT")
                }
            }

            extractedNotifications.remove(sender)
        } catch (e: android.os.DeadObjectException) {
            AgentLogger.log(AgentLogger.LogType.ERROR, "❌ RemoteInput DeadObjectException -> Fallback")
            fallbackOpenChat(data.sbn, sender, reply, customId)
        } catch (e: Exception) {
            AgentLogger.log(AgentLogger.LogType.ERROR, "❌ RemoteInput Fehler: ${e.message} -> Fallback")
            fallbackOpenChat(data.sbn, sender, reply, customId)
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
    private fun fallbackOpenChat(sbn: StatusBarNotification?, sender: String, reply: String, customId: String) {
        AgentLogger.log(AgentLogger.LogType.INFO, "🔄 Fallback Chat-Öffnen für $sender...")

        try {
            // Stage 1: Try extracted contentIntent
            val data = extractedNotifications[sender]
            val intentToUse = data?.contentIntent ?: sbn?.notification?.contentIntent

            if (intentToUse != null) {
                intentToUse.send(
                    applicationContext,
                    0,
                    Intent().apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP) }
                )
            } else {
                // Stage 2: Try wa.me intent (Fallback if SBN is missing)
                AgentLogger.log(AgentLogger.LogType.INFO, "🌐 SBN null, versuche URI-Fallback...")
                
                scope.launch {
                    val db = AppDatabase.getDatabase(applicationContext)
                    val cached = db.contactCacheDao().getByName(sender)
                    val phone = cached?.phoneNumber
                    
                    val pkg = if (sbn?.packageName == "com.whatsapp.w4b") "com.whatsapp.w4b" else "com.whatsapp"
                    val uriString = if (!phone.isNullOrBlank()) {
                        "https://wa.me/$phone?text=${android.net.Uri.encode(reply)}"
                    } else {
                        "https://wa.me/?text=${android.net.Uri.encode(reply)}"
                    }

                    val whatsappIntent = Intent(Intent.ACTION_VIEW).apply {
                        this.setData(android.net.Uri.parse(uriString))
                        setPackage(pkg)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(whatsappIntent)
                }
            }
        } catch (e: Exception) {
            AgentLogger.log(AgentLogger.LogType.ERROR, "❌ Fallback fehlgeschlagen: ${e.message}")
            if (customId.isNotEmpty()) {
                scope.launch {
                    AppDatabase.getDatabase(applicationContext).messageDao().updateStatus(customId, com.example.whatsappagent.data.MessageStatus.REPLY_FAILED)
                }
            }
            return
        }

        mainHandler.postDelayed({
            attemptAccessibilityReply(sender, reply, customId, attempt = 1, maxAttempts = 8)
        }, 3000L)
    }

    private fun attemptAccessibilityReply(sender: String, reply: String, customId: String, attempt: Int, maxAttempts: Int) {
        mainHandler.postDelayed({
            val autoReply = AutoReplyService.instance
            if (autoReply == null) {
                AgentLogger.log(AgentLogger.LogType.ERROR, "❌ AutoReplyService nicht aktiv!")
                if (customId.isNotEmpty()) {
                    scope.launch {
                        AppDatabase.getDatabase(applicationContext).messageDao().updateStatus(customId, com.example.whatsappagent.data.MessageStatus.REPLY_FAILED)
                    }
                }
                return@postDelayed
            }

            val success = autoReply.tryReply(sender, reply)

            if (success) {
                lastReplySentAt[sender] = System.currentTimeMillis()
                AgentLogger.log(AgentLogger.LogType.REPLY, "✅ Accessibility Reply an $sender")
                if (customId.isNotEmpty()) {
                    scope.launch {
                        val db = AppDatabase.getDatabase(applicationContext)
                        db.messageDao().updateStatus(customId, com.example.whatsappagent.data.MessageStatus.REPLY_SENT)
                        
                        // Sync backend status
                        val repository = (application as AgentApplication).repository
                        repository.updateMessageStatus(customId, "REPLY_SENT")
                    }
                }
            } else if (attempt < maxAttempts) {
                AgentLogger.log(AgentLogger.LogType.INFO, "🔄 Versuch $attempt fehlgeschlagen, retry...")
                attemptAccessibilityReply(sender, reply, customId, attempt + 1, maxAttempts)
            } else {
                AgentLogger.log(AgentLogger.LogType.ERROR, "❌ Nach $maxAttempts Versuchen fehlgeschlagen")
                if (customId.isNotEmpty()) {
                    scope.launch {
                        AppDatabase.getDatabase(applicationContext).messageDao().updateStatus(customId, com.example.whatsappagent.data.MessageStatus.REPLY_FAILED)
                    }
                }
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
        
        // Nur "(3)", "(12)" etc. am Ende → Gruppe
        val endsWithCount = sender.trimEnd().matches(Regex(".*\\(\\d+\\)$"))
        if (endsWithCount) return true

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
            if (!beforeColon.contains("+") && !beforeColon.contains("http") && !beforeColon.contains(" ")) return true
        }
        return false
    }
}