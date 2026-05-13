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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import kotlin.random.Random

class WhatsAppListener : NotificationListenerService() {

    companion object {
        const val ACTION_SEND_REPLY = "com.example.whatsappagent.SEND_REPLY"
        const val EXTRA_SENDER = "sender"
        const val EXTRA_REPLY = "reply"
        const val EXTRA_CUSTOM_ID = "custom_id"
        const val EXTRA_DRAFT_ID = "draft_id"
        
        var isRunning = false // Static tracking
        var instance: WhatsAppListener? = null
        var lastRebindRequestAt = 0L
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.IO)

    // Filter-Einstellungen
    private val BLACKLIST = setOf(
        "Bank", "Sparkasse", "Telekom", "Amazon",
        "Amt", "BehÃ¶rde", "Polizei", "Arzt", "Ø§Ø¨Ùˆ Ø²Ø§Ù‡Ø±"
    )

    // In-Memory Cache fÃ¼r die aktiven Benachrichtigungen (fÃ¼r RemoteInput)
    private val lastNotification = mutableMapOf<String, StatusBarNotification>()
    
    // NEU: Sofortige Extraktion der Notification-Daten
    data class NotificationData(
        val sbn: StatusBarNotification,
        val replyAction: Notification.Action?,
        val contentIntent: android.app.PendingIntent?,
        val contactName: String,
        val notificationKey: String,
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
    // BROADCAST RECEIVER FÃœR DEN WORKER
    // =========================
    private val replyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val sender = intent?.getStringExtra(EXTRA_SENDER) ?: return
            val reply = intent.getStringExtra(EXTRA_REPLY) ?: return
            val customId = intent.getStringExtra(EXTRA_CUSTOM_ID) ?: ""
            val draftId = intent.getLongExtra(EXTRA_DRAFT_ID, -1L).takeIf { it > 0 }
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
                AgentLogger.log(AgentLogger.LogType.WAIT, "â± [$logType] Warte ${delayMs/1000}s vor Antwort an $sender...")

                mainHandler.postDelayed({
                    sendReplyViaRemoteInput(sender, reply, customId, draftId)
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
        AgentLogger.log(AgentLogger.LogType.INFO, "WhatsApp Listener verbunden âœ…")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        instance = null
        isRunning = false
        AgentLogger.log(AgentLogger.LogType.INFO, "WhatsApp Listener getrennt âŒ")
    }

    // =========================
    // NOTIFICATION INTERCEPTION
    // =========================
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != "com.whatsapp.w4b" && sbn.packageName != "com.whatsapp") return

        val parsedMessages = NotificationParser.parse(sbn)
        if (parsedMessages.isEmpty()) {
            AgentLogger.log(AgentLogger.LogType.INFO, "WhatsApp Notification ignoriert: kein eindeutiger 1:1 Sender")
            return
        }

        if (parsedMessages.size > 1) {
            AgentLogger.log(AgentLogger.LogType.INFO, "Kompakte WhatsApp Notification: ${parsedMessages.size} Nachrichten erkannt")
        }

        parsedMessages.forEach { parsed ->
            handleParsedMessage(sbn, parsed)
        }
    }

    private fun handleParsedMessage(sbn: StatusBarNotification, parsed: NotificationParser.ParsedMessage) {
        val sender = parsed.sender
        val textRaw = parsed.text

        if (isBlacklisted(sender)) return
        if (parsed.isGroupConversation || isGroupMessage(sender, textRaw, null)) return
        if (NotificationParser.isUnsafeReplyText(textRaw)) return

        // Self-Loop Schutz (Ignoriert Nachrichten, die wir selbst gerade gesendet haben)
        if (sentReplies.contains(textRaw.trim().lowercase())) {
            sentReplies.remove(textRaw.trim().lowercase())
            return
        }

        // Doppel-Antwort Schutz
        val lastReply = lastReplySentAt[sender] ?: 0L
        if (System.currentTimeMillis() - lastReply < MIN_REPLY_INTERVAL_MS) {
            AgentLogger.log(AgentLogger.LogType.INFO, "â­ Zu schnell fÃ¼r $sender, ignoriert")
            return
        }

        // Duplikat-Schutz (Persistent via SharedPreferences)
        val shortTermKey = "$sender:$textRaw"
        if (isDuplicate(shortTermKey)) return
        markAsProcessed(shortTermKey)

        val timestamp = parsed.timestamp.takeIf { it > 0 } ?: System.currentTimeMillis()
        val customId = generateHash("$sender$textRaw$timestamp")

        // Cache fÃ¼r spÃ¤tere Antworten aktualisieren
        lastNotification[sender] = sbn
        val notificationData = NotificationData(
            sbn = sbn,
            replyAction = findReplyAction(sbn.notification),
            contentIntent = sbn.notification.contentIntent,
            contactName = sender,
            notificationKey = parsed.notificationKey
        )
        extractedNotifications[sender] = notificationData
        extractedNotifications[customId] = notificationData
        extractedNotifications[parsed.notificationKey] = notificationData
        pruneExtractedNotifications()

        // Wake screen if off
        DeviceControl.wakeScreen(applicationContext)

        AgentLogger.log(AgentLogger.LogType.MESSAGE, "ðŸ“¨ $sender: $textRaw")

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
                AgentLogger.log(AgentLogger.LogType.INFO, "â­ Ignoriert: Agent fÃ¼r $sender deaktiviert")
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
                AgentLogger.log(AgentLogger.LogType.INFO, "ðŸ‘¤ Neuer Kontakt angelegt: $sender")
            }

            val messageEntry = MessageEntity(
                customId = customId,
                sender = sender,
                text = textRaw,
                timestamp = timestamp,
                isSynced = false,
                phoneNumber = resolvedPhone,
                packageName = parsed.packageName,
                notificationKey = parsed.notificationKey
            )

            val resultId = db.messageDao().insertMessage(messageEntry)

            if (resultId != -1L) {
                AgentLogger.log(AgentLogger.LogType.INFO, "ðŸ“¥ In Queue gespeichert: $customId")
                WorkManagerHelper.triggerSync(applicationContext)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName != "com.whatsapp.w4b" && sbn.packageName != "com.whatsapp") return
        val sender = NotificationParser.parse(sbn).lastOrNull()?.sender
            ?: extractedNotifications[sbn.key]?.contactName
            ?: return
        if (lastNotification[sender]?.id == sbn.id) {
            lastNotification.remove(sender)
            // Wir entfernen extrahierter Daten NICHT sofort, damit delayed replies noch funktionieren
            mainHandler.postDelayed({
                extractedNotifications.remove(sender)
                extractedNotifications.remove(sbn.key)
            }, 60_000L) // 60s Puffer
        }
    }

    // =========================
    // REPLY VIA REMOTEINPUT
    // =========================
    private fun sendReplyViaRemoteInput(sender: String, reply: String, customId: String, draftId: Long?) {
        val data = extractedNotifications[customId].takeUnless { customId.isBlank() }
            ?: extractedNotifications[sender]
            ?: run {
            AgentLogger.log(AgentLogger.LogType.ERROR, "âŒ Keine extrahierte Notification fÃ¼r $sender -> Fallback")
            fallbackOpenChat(null, sender, reply, customId, draftId)
            return
        }

        // Check age of notification (MAX 10 minutes)
        if (System.currentTimeMillis() - data.capturedAt > 10 * 60 * 1000L) {
            AgentLogger.log(AgentLogger.LogType.INFO, "ðŸ•’ Notification zu alt (>10min) -> Fallback")
            fallbackOpenChat(data.sbn, sender, reply, customId, draftId)
            return
        }

        val replyAction = data.replyAction ?: run {
            AgentLogger.log(AgentLogger.LogType.ERROR, "âŒ Kein Reply-Button fÃ¼r $sender -> Fallback")
            fallbackOpenChat(data.sbn, sender, reply, customId, draftId)
            return
        }

        val remoteInput = replyAction.remoteInputs?.firstOrNull() ?: run {
            AgentLogger.log(AgentLogger.LogType.ERROR, "âŒ Kein RemoteInput fÃ¼r $sender -> Fallback")
            fallbackOpenChat(data.sbn, sender, reply, customId, draftId)
            return
        }

        val bundle = Bundle().apply {
            putCharSequence(remoteInput.resultKey, reply)
        }

        val intent = Intent().apply {
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            RemoteInput.addResultsToIntent(replyAction.remoteInputs, this, bundle)
        }

        var attemptId: Long? = null
        try {
            attemptId = runBlocking(Dispatchers.IO) {
                startSendAttempt(draftId, "ANDROID_REMOTE_INPUT", customId)
            }
            // Self-Loop Schutz Cache
            sentReplies.add(reply.trim().lowercase())
            if (sentReplies.size > 20) sentReplies.clear()

            replyAction.actionIntent.send(applicationContext, 0, intent)
            lastReplySentAt[sender] = System.currentTimeMillis()
            AgentLogger.log(AgentLogger.LogType.REPLY, "âœ… Reply gesendet an $sender: $reply")

            runBlocking(Dispatchers.IO) {
                finishSendAttempt(attemptId, customId, "SENT")
            }

            if (customId.isNotEmpty()) {
                scope.launch {
                    val db = AppDatabase.getDatabase(applicationContext)
                    db.messageDao().updateStatus(customId, com.example.whatsappagent.data.MessageStatus.SENT)
                    
                    // Sync backend status to fix Dashboard/Queue inconsistency
                    val repository = (application as AgentApplication).repository
                    repository.updateMessageStatus(customId, "SENT")
                }
            }

            extractedNotifications.remove(sender)
            if (customId.isNotEmpty()) extractedNotifications.remove(customId)
        } catch (e: android.os.DeadObjectException) {
            runBlocking(Dispatchers.IO) {
                finishSendAttempt(attemptId, customId, "FAILED", e.message)
            }
            AgentLogger.log(AgentLogger.LogType.ERROR, "âŒ RemoteInput DeadObjectException -> Fallback")
            fallbackOpenChat(data.sbn, sender, reply, customId, draftId)
        } catch (e: Exception) {
            runBlocking(Dispatchers.IO) {
                finishSendAttempt(attemptId, customId, "FAILED", e.message)
            }
            AgentLogger.log(AgentLogger.LogType.ERROR, "âŒ RemoteInput Fehler: ${e.message} -> Fallback")
            fallbackOpenChat(data.sbn, sender, reply, customId, draftId)
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
            AgentLogger.log(AgentLogger.LogType.INFO, "Wearable Extender nicht verfÃ¼gbar")
        }
        return null
    }

    // =========================
    // FALLBACK: ACCESSIBILITY UI AUTOMATION
    // =========================
    private fun fallbackOpenChat(sbn: StatusBarNotification?, sender: String, reply: String, customId: String, draftId: Long?) {
        AgentLogger.log(AgentLogger.LogType.INFO, "ðŸ”„ Fallback Chat-Ã–ffnen fÃ¼r $sender...")

        if (!isAccessibilityFallbackAllowed()) {
            AgentLogger.log(AgentLogger.LogType.ERROR, "Accessibility-Fallback nicht freigegeben")
            scope.launch {
                AppDatabase.getDatabase(applicationContext).messageDao().updateStatus(customId, com.example.whatsappagent.data.MessageStatus.FAILED)
                reportFailedNoChannel(draftId, customId, "Accessibility fallback not enabled")
            }
            return
        }

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
                AgentLogger.log(AgentLogger.LogType.INFO, "ðŸŒ SBN null, versuche URI-Fallback...")
                
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
            AgentLogger.log(AgentLogger.LogType.ERROR, "âŒ Fallback fehlgeschlagen: ${e.message}")
            if (customId.isNotEmpty()) {
                scope.launch {
                    AppDatabase.getDatabase(applicationContext).messageDao().updateStatus(customId, com.example.whatsappagent.data.MessageStatus.FAILED)
                    reportFailedNoChannel(draftId, customId, e.message)
                }
            }
            return
        }

        mainHandler.postDelayed({
            attemptAccessibilityReply(sender, reply, customId, draftId, attemptId = null, attempt = 1, maxAttempts = 8)
        }, 3000L)
    }

    private fun attemptAccessibilityReply(
        sender: String,
        reply: String,
        customId: String,
        draftId: Long?,
        attemptId: Long?,
        attempt: Int,
        maxAttempts: Int
    ) {
        mainHandler.postDelayed({
            val autoReply = AutoReplyService.instance
            if (autoReply == null) {
                AgentLogger.log(AgentLogger.LogType.ERROR, "âŒ AutoReplyService nicht aktiv!")
                if (customId.isNotEmpty()) {
                    scope.launch {
                        AppDatabase.getDatabase(applicationContext).messageDao().updateStatus(customId, com.example.whatsappagent.data.MessageStatus.FAILED)
                        if (attemptId != null) {
                            finishSendAttempt(attemptId, customId, "FAILED_NO_CHANNEL", "AutoReplyService not active")
                        } else {
                            reportFailedNoChannel(draftId, customId, "AutoReplyService not active")
                        }
                    }
                }
                return@postDelayed
            }

            if (attemptId == null && draftId != null) {
                scope.launch {
                    val newAttemptId = startSendAttempt(draftId, "ANDROID_ACCESSIBILITY", customId)
                    withContext(Dispatchers.Main) {
                        attemptAccessibilityReply(sender, reply, customId, draftId, newAttemptId, attempt, maxAttempts)
                    }
                }
                return@postDelayed
            }

            val success = autoReply.tryReply(sender, reply)

            if (success) {
                lastReplySentAt[sender] = System.currentTimeMillis()
                AgentLogger.log(AgentLogger.LogType.REPLY, "âœ… Accessibility Reply an $sender")
                if (customId.isNotEmpty()) {
                    scope.launch {
                        finishSendAttempt(attemptId, customId, "SENT")
                    }
                }
            } else if (attempt < maxAttempts) {
                AgentLogger.log(AgentLogger.LogType.INFO, "ðŸ”„ Versuch $attempt fehlgeschlagen, retry...")
                attemptAccessibilityReply(sender, reply, customId, draftId, attemptId, attempt + 1, maxAttempts)
            } else {
                AgentLogger.log(AgentLogger.LogType.ERROR, "âŒ Nach $maxAttempts Versuchen fehlgeschlagen")
                if (customId.isNotEmpty()) {
                    scope.launch {
                        finishSendAttempt(attemptId, customId, "FAILED_ACCESSIBILITY", "Accessibility reply failed after $maxAttempts attempts")
                    }
                }
            }
        }, 2000L)
    }

    // =========================
    // HILFSFUNKTIONEN
    // =========================
    private suspend fun startSendAttempt(draftId: Long?, channel: String, customId: String): Long? {
        if (draftId == null) return null
        val repository = (application as AgentApplication).repository
        val db = AppDatabase.getDatabase(applicationContext)
        val created = repository.createSendAttempt(draftId, channel).getOrElse { error ->
            AgentLogger.log(AgentLogger.LogType.ERROR, "SendAttempt konnte nicht erstellt werden: ${error.message}")
            return null
        }
        val attemptId = created.id
        db.sendAttemptDao().upsertAttempt(
            com.example.whatsappagent.data.SendAttemptEntity(
                backendId = attemptId,
                draftId = draftId,
                channel = channel,
                status = com.example.whatsappagent.data.MessageStatus.SEND_PENDING,
                attemptCount = 1
            )
        )
        repository.updateSendAttempt(attemptId, "SENDING").onFailure {
            AgentLogger.log(AgentLogger.LogType.ERROR, "SendAttempt SENDING Sync fehlgeschlagen: ${it.message}")
        }
        db.sendAttemptDao().updateStatus(attemptId, com.example.whatsappagent.data.MessageStatus.SENDING, null, null)
        if (customId.isNotEmpty()) {
            db.messageDao().updateStatus(customId, com.example.whatsappagent.data.MessageStatus.SENDING)
        }
        return attemptId
    }

    private suspend fun finishSendAttempt(attemptId: Long?, customId: String, backendStatus: String, error: String? = null) {
        val localStatus = if (backendStatus == "SENT") {
            com.example.whatsappagent.data.MessageStatus.SENT
        } else {
            com.example.whatsappagent.data.MessageStatus.FAILED
        }
        val sentAtMillis = if (backendStatus == "SENT") System.currentTimeMillis() else null
        val db = AppDatabase.getDatabase(applicationContext)

        if (attemptId != null) {
            val repository = (application as AgentApplication).repository
            repository.updateSendAttempt(attemptId, backendStatus, error).onFailure {
                AgentLogger.log(AgentLogger.LogType.ERROR, "SendAttempt $backendStatus Sync fehlgeschlagen: ${it.message}")
            }
            db.sendAttemptDao().updateStatus(attemptId, localStatus, error, sentAtMillis)
        }

        if (customId.isNotEmpty()) {
            db.messageDao().updateStatus(customId, localStatus)
        }
    }

    private suspend fun reportFailedNoChannel(draftId: Long?, customId: String, error: String?) {
        val attemptId = startSendAttempt(draftId, "ANDROID_ACCESSIBILITY", customId)
        finishSendAttempt(attemptId, customId, "FAILED_NO_CHANNEL", error)
    }

    private fun isAccessibilityFallbackAllowed(): Boolean =
        getSharedPreferences(AgentSafetySettings.PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(AgentSafetySettings.PREF_ACCESSIBILITY_FALLBACK_ENABLED, false)

    private fun pruneExtractedNotifications() {
        if (extractedNotifications.size <= 100) return
        val now = System.currentTimeMillis()
        val staleKeys = extractedNotifications
            .filterValues { now - it.capturedAt > 10 * 60 * 1000L }
            .keys
        staleKeys.forEach { extractedNotifications.remove(it) }
    }

    private fun generateHash(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
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
        
        // Nur "(3)", "(12)" etc. am Ende â†’ Gruppe
        val endsWithCount = sender.trimEnd().matches(Regex(".*\\(\\d+\\)$"))
        if (endsWithCount) return true

        val groupKeywords = listOf(
            "gruppe", "group", "team", "chat", "klasse", "kurs",
            "hka", "uni", "schule", "restaurant", "kÃ¼che", "kitchen",
            "work", "office", "firma", "betrieb", "service", "staff",
            "gmbh", "ag ", " kg", "gesellschaft", "Ù…Ø¬Ù…ÙˆØ¹Ø©", "ÙØ±ÙŠÙ‚",
            "Ù…Ø·Ø¨Ø®", "Ù…Ø¯Ø±Ø³Ø©", "Ø¬Ø§Ù…Ø¹Ø©", "Ø¹Ù…Ù„", "Ø´Ø±ÙƒØ©", "Ø·Ø§Ù‚Ù…", "ØµÙ", "Ø¯ÙˆØ±Ø©"
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
