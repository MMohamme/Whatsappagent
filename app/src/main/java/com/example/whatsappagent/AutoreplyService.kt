package com.example.whatsappagent

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.random.Random

class AutoReplyService : AccessibilityService() {

    companion object {
        var instance: AutoReplyService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this

        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            packageNames = arrayOf("com.whatsapp.w4b")
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
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    // =========================
    // ÖFFENTLICHE METHODE — gibt true zurück wenn erfolgreich
    // Wird von WhatsAppListener mit Retry aufgerufen
    // =========================
    fun tryReply(text: String): Boolean {
        val root = rootInActiveWindow ?: run {
            AgentLogger.log(AgentLogger.LogType.INFO, "Kein aktives Fenster")
            return false
        }

        // Prüfen ob wir wirklich in WhatsApp Business sind
        val packageName = root.packageName?.toString()
        if (packageName != "com.whatsapp.w4b") {
            AgentLogger.log(AgentLogger.LogType.INFO, "Falsches Fenster: $packageName")
            root.recycle()
            return false
        }

        val inputField = findInputField(root)
        if (inputField == null) {
            AgentLogger.log(AgentLogger.LogType.INFO, "Eingabefeld nicht gefunden")
            root.recycle()
            return false
        }

        // Tipp-Indikator: Feld fokussieren
        inputField.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        root.recycle()

        // Kurz warten dann Text einfügen
        Handler(Looper.getMainLooper()).postDelayed({
            insertTextAndSend(maybeAddTypo(text))
        }, Random.nextLong(1500, 3000))

        return true
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
        // Primär: WhatsApp Business View ID
        root.findAccessibilityNodeInfosByViewId("com.whatsapp.w4b:id/entry")
            ?.takeIf { it.isNotEmpty() }?.let { return it[0] }
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
        root.findAccessibilityNodeInfosByViewId("com.whatsapp.w4b:id/send")
            ?.takeIf { it.isNotEmpty() }?.let { return it[0] }
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