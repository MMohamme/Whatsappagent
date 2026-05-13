package com.example.whatsappagent

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.whatsappagent.data.AppDatabase
import com.example.whatsappagent.data.MessageEntity
import com.example.whatsappagent.worker.WorkManagerHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.security.MessageDigest

class AutoReplyService : AccessibilityService() {

    companion object {
        var instance: AutoReplyService? = null
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private var lastScrapedText: String? = null
    private var lastScrapedSender: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this

        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            packageNames = arrayOf("com.whatsapp", "com.whatsapp.w4b")
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100
        }
        serviceInfo = info
        AgentLogger.log(AgentLogger.LogType.INFO, "AutoReplyService aktiv âœ…")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        scope.cancel()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        
        // Only process events from WhatsApp
        val packageName = event.packageName?.toString()
        if (packageName != "com.whatsapp" && packageName != "com.whatsapp.w4b") return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED || 
            event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            scrapeMessages()
        }
    }

    private fun scrapeMessages() {
        val root = rootInActiveWindow ?: return
        
        // Try to find the chat header to identify the sender
        val sender = findChatName(root) ?: return
        
        // Find all message bubbles
        val messages = mutableListOf<String>()
        findMessageTexts(root, messages)
        
        if (messages.isNotEmpty()) {
            val latestMessage = messages.last()
            
            // Basic deduplication
            if (latestMessage != lastScrapedText || sender != lastScrapedSender) {
                lastScrapedText = latestMessage
                lastScrapedSender = sender
                
                processScrapedMessage(sender, latestMessage)
            }
        }
        root.recycle()
    }

    private fun findChatName(root: AccessibilityNodeInfo?): String? {
        if (root == null) return null
        // WhatsApp Chat Header IDs
        val ids = arrayOf(
            "com.whatsapp:id/conversation_contact_name",
            "com.whatsapp.w4b:id/conversation_contact_name"
        )
        for (id in ids) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (!nodes.isNullOrEmpty()) {
                val text = nodes[0].text?.toString()
                nodes.forEach { it.recycle() }
                return text
            }
        }
        return null
    }

    private fun findMessageTexts(node: AccessibilityNodeInfo?, list: MutableList<String>) {
        if (node == null) return
        // Message text IDs
        val ids = arrayOf(
            "com.whatsapp:id/message_text",
            "com.whatsapp.w4b:id/message_text"
        )
        
        val nodeText = node.text?.toString()
        val viewId = node.viewIdResourceName
        
        if (!nodeText.isNullOrBlank() && ids.any { it == viewId }) {
            list.add(nodeText)
        }
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findMessageTexts(child, list)
        }
    }

    private fun processScrapedMessage(sender: String, text: String) {
        scope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            
            // Check if agent is active for this sender
            val settings = db.contactSettingsDao().getContactSettingsByName(sender)
            if (settings != null && !settings.isActive) return@launch

            // Check if this message was already processed (very rough check)
            // In a real app, we'd need better logic to avoid re-processing existing messages
            val recent = db.messageDao().getLastMessages(sender, 1)
            if (recent.isNotEmpty() && recent[0].text == text) return@launch

            AgentLogger.log(AgentLogger.LogType.MESSAGE, "ðŸ” Scraped von $sender: $text")

            val timestamp = System.currentTimeMillis()
            val customId = generateHash("$sender$text$timestamp")

            val messageEntry = MessageEntity(
                customId = customId,
                sender = sender,
                text = text,
                timestamp = timestamp,
                isSynced = false
            )

            db.messageDao().insertMessage(messageEntry)
            WorkManagerHelper.triggerSync(applicationContext)
        }
    }

    private fun generateHash(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    override fun onInterrupt() {}

    // =========================
    // Ã–FFENTLICHE METHODE â€” gibt true zurÃ¼ck wenn erfolgreich
    // Wird von WhatsAppListener mit Retry aufgerufen
    // =========================
    fun tryReply(text: String): Boolean {
        return tryReply(null, text)
    }

    /**
     * Tries to reply only when the active WhatsApp chat already matches the target contact.
     */
    fun tryReply(targetContact: String?, text: String): Boolean {
        if (!isAccessibilityFallbackAllowed()) {
            AgentLogger.log(AgentLogger.LogType.INFO, "Accessibility-Fallback nicht freigegeben")
            return false
        }

        val root = rootInActiveWindow ?: run {
            AgentLogger.log(AgentLogger.LogType.INFO, "Kein aktives Fenster")
            return false
        }

        try {
            // If targetContact is provided, check if we are already in the right chat
            if (targetContact != null) {
                val currentChat = findChatName(root)
                if (!isSameChatName(currentChat, targetContact)) {
                    AgentLogger.log(AgentLogger.LogType.ERROR, "Accessibility blockiert: aktiver Chat ist '$currentChat', erwartet '$targetContact'")
                    return false
                }
            }

            // PrÃ¼fen ob wir wirklich in WhatsApp sind
            val packageName = root.packageName?.toString()
            if (packageName != "com.whatsapp.w4b" && packageName != "com.whatsapp") {
                AgentLogger.log(AgentLogger.LogType.INFO, "Falsches Fenster: $packageName")
                return false
            }

            val inputField = findInputField(root)
            if (inputField == null) {
                AgentLogger.log(AgentLogger.LogType.INFO, "Eingabefeld nicht gefunden")
                return false
            }

            // Tipp-Indikator: Feld fokussieren
            inputField.performAction(AccessibilityNodeInfo.ACTION_CLICK)

            // Release wake lock as we are now interacting
            DeviceControl.releaseWakeLock()

            // Kurz warten dann Text einfÃ¼gen
            return insertTextAndSend(text, targetContact)
        } finally {
            root.recycle()
        }
    }

    // =========================
    // TEXT EINFÃœGEN + SENDEN
    // =========================
    private fun insertTextAndSend(text: String, targetContact: String?): Boolean {
        val root = rootInActiveWindow ?: return false
        if (targetContact != null && !isSameChatName(findChatName(root), targetContact)) {
            AgentLogger.log(AgentLogger.LogType.ERROR, "Accessibility blockiert: Chat wechselte vor dem Senden")
            root.recycle()
            return false
        }

        val inputField = findInputField(root) ?: run { root.recycle(); return false }

        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val inserted = inputField.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)

        if (!inserted) {
            AgentLogger.log(AgentLogger.LogType.ERROR, "âŒ Text einfÃ¼gen fehlgeschlagen")
            root.recycle()
            return false
        }

        // Senden Button klicken
        val sendBtn = findSendButton(root)
        if (sendBtn == null) {
            AgentLogger.log(AgentLogger.LogType.ERROR, "Senden-Button nicht gefunden")
            root.recycle()
            return false
        }

        val sent = sendBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        root.recycle()
        return sent
    }

    private fun isAccessibilityFallbackAllowed(): Boolean =
        getSharedPreferences(AgentSafetySettings.PREFS_NAME, MODE_PRIVATE)
            .getBoolean(AgentSafetySettings.PREF_ACCESSIBILITY_FALLBACK_ENABLED, false)

    private fun isSameChatName(actual: String?, expected: String): Boolean =
        actual?.trim()?.equals(expected.trim(), ignoreCase = true) == true

    // =========================
    // NODE FINDER
    // =========================
    private fun findInputField(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // WhatsApp View IDs
        val ids = arrayOf(
            "com.whatsapp:id/entry",
            "com.whatsapp.w4b:id/entry"
        )
        for (id in ids) {
            root.findAccessibilityNodeInfosByViewId(id)
                ?.takeIf { it.isNotEmpty() }?.let { return it[0] }
        }
        // Fallback: erstes editierbares Feld
        return findEditableNode(root)
    }

    private fun findEditableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            val result = findEditableNode(node.getChild(i) ?: continue)
            if (result != null) return result
        }
        return null
    }

    private fun findSendButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val ids = arrayOf(
            "com.whatsapp:id/send",
            "com.whatsapp.w4b:id/send"
        )
        for (id in ids) {
            root.findAccessibilityNodeInfosByViewId(id)
                ?.takeIf { it.isNotEmpty() }?.let { return it[0] }
        }
        return findNodeByDescription(root, listOf("Senden", "Send"))
    }

    private fun findNodeByDescription(node: AccessibilityNodeInfo, labels: List<String>): AccessibilityNodeInfo? {
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val text = node.text?.toString()?.lowercase() ?: ""
        if (labels.any { it.lowercase() in desc || it.lowercase() in text }) return node
        for (i in 0 until node.childCount) {
            val result = findNodeByDescription(node.getChild(i) ?: continue, labels)
            if (result != null) return result
        }
        return null
    }
}
