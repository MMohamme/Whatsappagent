package com.example.whatsappagent

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import kotlin.random.Random

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
        AgentLogger.log(AgentLogger.LogType.INFO, "AutoReplyService aktiv ✅")
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

            AgentLogger.log(AgentLogger.LogType.MESSAGE, "🔍 Scraped von $sender: $text")

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
    // ÖFFENTLICHE METHODE — gibt true zurück wenn erfolgreich
    // Wird von WhatsAppListener mit Retry aufgerufen
    // =========================
    fun tryReply(text: String): Boolean {
        return tryReply(null, text)
    }

    /**
     * Tries to reply to a specific contact. If targetContact is provided, 
     * it will try to find and open the chat first.
     */
    fun tryReply(targetContact: String?, text: String): Boolean {
        val root = rootInActiveWindow ?: run {
            AgentLogger.log(AgentLogger.LogType.INFO, "Kein aktives Fenster")
            return false
        }

        try {
            // If targetContact is provided, check if we are already in the right chat
            if (targetContact != null) {
                val currentChat = findChatName(root)
                if (currentChat != targetContact) {
                    AgentLogger.log(AgentLogger.LogType.INFO, "Nicht im Chat mit $targetContact. Suche...")
                    openChatByName(root, targetContact)
                    return false // Caller should retry
                }
            }

            // Prüfen ob wir wirklich in WhatsApp sind
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

            // Kurz warten dann Text einfügen
            Handler(Looper.getMainLooper()).postDelayed({
                insertTextAndSend(maybeAddTypo(text))
            }, Random.nextLong(1500, 3000))

            return true
        } finally {
            root.recycle()
        }
    }

    private fun openChatByName(root: AccessibilityNodeInfo, name: String) {
        // 1. Find and click search button
        val searchIds = arrayOf(
            "com.whatsapp:id/menuitem_search",
            "com.whatsapp.w4b:id/menuitem_search"
        )
        var searchBtn: AccessibilityNodeInfo? = null
        for (id in searchIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (!nodes.isNullOrEmpty()) {
                searchBtn = nodes[0]
                break
            }
        }
        
        if (searchBtn == null) {
            searchBtn = findNodeByDescription(root, listOf("Suche", "Search"))
        }

        if (searchBtn != null) {
            searchBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            
            // 2. Wait and type name
            Handler(Looper.getMainLooper()).postDelayed({
                val rootSearch = rootInActiveWindow ?: return@postDelayed
                val searchInput = findSearchInput(rootSearch)
                if (searchInput != null) {
                    val args = Bundle().apply {
                        putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, name)
                    }
                    searchInput.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                    
                    // 3. Wait and click first result
                    Handler(Looper.getMainLooper()).postDelayed({
                        val rootResults = rootInActiveWindow ?: return@postDelayed
                        val firstResult = findFirstSearchResult(rootResults, name)
                        firstResult?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        rootResults.recycle()
                    }, 1000)
                }
                rootSearch.recycle()
            }, 1000)
        } else {
            AgentLogger.log(AgentLogger.LogType.ERROR, "❌ Suche-Button nicht gefunden")
        }
    }

    private fun findSearchInput(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val ids = arrayOf(
            "com.whatsapp:id/search_input",
            "com.whatsapp.w4b:id/search_input",
            "com.whatsapp:id/search_src_text",
            "com.whatsapp.w4b:id/search_src_text"
        )
        for (id in ids) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (!nodes.isNullOrEmpty()) return nodes[0]
        }
        return findEditableNode(root)
    }

    private fun findFirstSearchResult(root: AccessibilityNodeInfo, name: String): AccessibilityNodeInfo? {
        // Try to find a node with the contact name text
        val nodes = root.findAccessibilityNodeInfosByText(name)
        if (!nodes.isNullOrEmpty()) {
            // Find the first clickable parent or the node itself
            var current: AccessibilityNodeInfo? = nodes[0]
            while (current != null) {
                if (current.isClickable) return current
                current = current.parent
            }
        }
        return null
    }

    // =========================
    // TEXT EINFÜGEN + SENDEN
    // =========================
    private fun insertTextAndSend(text: String) {
        val root = rootInActiveWindow ?: return
        val inputField = findInputField(root) ?: run { root.recycle(); return }

        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val inserted = inputField.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        root.recycle()

        if (!inserted) {
            AgentLogger.log(AgentLogger.LogType.ERROR, "❌ Text einfügen fehlgeschlagen")
            return
        }

        // Senden Button klicken
        Handler(Looper.getMainLooper()).postDelayed({
            val rootNew = rootInActiveWindow ?: return@postDelayed
            val sendBtn = findSendButton(rootNew)
            if (sendBtn != null) {
                sendBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            } else {
                AgentLogger.log(AgentLogger.LogType.ERROR, "❌ Senden-Button nicht gefunden")
            }
            rootNew.recycle()
        }, 500)
    }

    // =========================
    // TIPPFEHLER SIMULATION (20% Chance)
    // =========================
    private fun maybeAddTypo(text: String): String {
        if (Random.nextInt(100) >= 20 || text.length < 5) return text
        val words = text.split(" ").toMutableList()
        if (words.isEmpty()) return text
        val wordIndex = Random.nextInt(words.size)
        val word = words[wordIndex]
        if (word.length < 3) return text
        val charIndex = Random.nextInt(word.length - 1)
        val typo = word.toCharArray().also {
            val tmp = it[charIndex]; it[charIndex] = it[charIndex + 1]; it[charIndex + 1] = tmp
        }.concatToString()
        words[wordIndex] = typo
        return "${words.joinToString(" ")} $text"
    }

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