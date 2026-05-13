package com.example.whatsappagent

import android.app.Notification
import android.os.Bundle
import android.service.notification.StatusBarNotification

object NotificationParser {
    data class ParsedMessage(
        val sender: String,
        val text: String,
        val timestamp: Long,
        val notificationKey: String,
        val packageName: String,
        val isGroupConversation: Boolean = false,
        val source: Source = Source.FALLBACK
    )

    data class RawMessage(
        val sender: String,
        val text: String,
        val timestamp: Long
    )

    enum class Source {
        MESSAGING_STYLE,
        FALLBACK
    }

    private val appLabels = setOf(
        "whatsapp",
        "whatsapp business",
        "wa business"
    )

    private val summaryPatterns = listOf(
        Regex("^\\d+\\s+neue\\s+Nachrichten?$", RegexOption.IGNORE_CASE),
        Regex("^\\d+\\s+new\\s+messages?$", RegexOption.IGNORE_CASE),
        Regex("^\\d+\\s+Nachrichten?$", RegexOption.IGNORE_CASE),
        Regex("^\\d+\\s+messages?$", RegexOption.IGNORE_CASE),
        Regex(".*aus \\d+ Chats.*", RegexOption.IGNORE_CASE),
        Regex(".*from \\d+ chats.*", RegexOption.IGNORE_CASE)
    )

    fun parse(sbn: StatusBarNotification): List<ParsedMessage> {
        val extras = sbn.notification.extras ?: return emptyList()
        if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return emptyList()

        val messagingMessages = parseMessagingStyle(sbn, extras)
        if (messagingMessages.isNotEmpty()) return messagingMessages

        val fallback = parseFallback(sbn, extras)
        return fallback?.let(::listOf) ?: emptyList()
    }

    private fun parseMessagingStyle(
        sbn: StatusBarNotification,
        extras: Bundle
    ): List<ParsedMessage> {
        val isGroup = extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false)
        if (isGroup) return emptyList()

        val bundles = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
            ?: extras.getParcelableArray("android.messages")
            ?: return emptyList()

        val rawMessages = bundles.mapNotNull { raw ->
            val bundle = raw as? Bundle ?: return@mapNotNull null
            val text = bundle.getCharSequence("text")?.toString()?.trim()
                ?: bundle.getCharSequence("android.text")?.toString()?.trim()
                ?: return@mapNotNull null
            val sender = extractSender(bundle)?.trim().orEmpty()
            val timestamp = bundle.getLong("time", sbn.postTime)

            RawMessage(sender = sender, text = text, timestamp = timestamp)
        }

        return normalizeMessagingMessages(rawMessages).mapNotNull { raw ->
            buildMessage(
                sbn = sbn,
                sender = raw.sender,
                text = raw.text,
                timestamp = raw.timestamp,
                isGroup = false,
                source = Source.MESSAGING_STYLE
            )
        }
    }

    fun normalizeMessagingMessages(rawMessages: List<RawMessage>): List<RawMessage> {
        val parsed = rawMessages
            .filter { it.sender.isNotBlank() && it.text.isNotBlank() }
            .filterNot { isAppLabel(it.sender) || isSummary(it.text) || isUnsafeReplyText(it.text) }
        val senders = parsed.map { it.sender }.distinct()
        return when {
            parsed.isEmpty() -> emptyList()
            senders.any(::isAppLabel) -> emptyList()
            senders.size > 1 -> parsed
            else -> parsed.takeLast(1)
        }
    }

    private fun extractSender(bundle: Bundle): String? {
        bundle.getCharSequence("sender")?.toString()?.let { if (it.isNotBlank()) return it }
        bundle.getCharSequence("android.sender")?.toString()?.let { if (it.isNotBlank()) return it }

        val person = bundle.getParcelable<android.app.Person>("sender_person")
            ?: bundle.getParcelable("android.sender_person")
        return person?.name?.toString()
    }

    private fun parseFallback(sbn: StatusBarNotification, extras: Bundle): ParsedMessage? {
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
            ?: extras.getCharSequence("android.title")?.toString()?.trim()
            ?: return null
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()
            ?: extras.getCharSequence("android.text")?.toString()?.trim()
            ?: return null
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim()
            ?: extras.getCharSequence("android.subText")?.toString()?.trim()

        if (subText?.isNotBlank() == true) return null
        if (isAppLabel(title)) return null
        if (isSummary(text)) return null

        return buildMessage(
            sbn = sbn,
            sender = title,
            text = text,
            timestamp = sbn.postTime,
            isGroup = false,
            source = Source.FALLBACK
        )
    }

    private fun buildMessage(
        sbn: StatusBarNotification,
        sender: String,
        text: String,
        timestamp: Long,
        isGroup: Boolean,
        source: Source
    ): ParsedMessage? {
        if (sender.isBlank() || text.isBlank()) return null
        if (isAppLabel(sender)) return null
        if (isSummary(text)) return null
        if (isUnsafeReplyText(text)) return null
        return ParsedMessage(
            sender = sender,
            text = text,
            timestamp = timestamp,
            notificationKey = sbn.key,
            packageName = sbn.packageName,
            isGroupConversation = isGroup,
            source = source
        )
    }

    fun isAppLabel(value: String): Boolean =
        appLabels.contains(value.trim().lowercase())

    fun isSummary(value: String): Boolean =
        summaryPatterns.any { it.containsMatchIn(value.trim()) }

    fun isUnsafeReplyText(value: String): Boolean {
        val text = value.trim()
        if (text.isBlank()) return true
        val lower = text.lowercase()
        if (lower.contains("nachricht gel\u00f6scht") || lower.contains("message deleted")) return true
        if (isReaction(text)) return true
        return isNonTextMessage(text)
    }

    fun isReaction(value: String): Boolean {
        val text = value.trim()
        val lower = text.lowercase()
        return lower.contains("auf deine nachricht") ||
            lower.contains("reacted to") ||
            lower.contains("reagierte auf") ||
            (text.length <= 2 && text.isNotEmpty())
    }

    fun isNonTextMessage(value: String): Boolean {
        val lower = value.trim().lowercase()
        val patterns = listOf(
            "sprachnachricht",
            "voice message",
            "foto",
            "photo",
            "image",
            "bild",
            "video",
            "dokument",
            "document",
            "sticker",
            "gif",
            "verpasster anruf",
            "missed call",
            "eingehender anruf",
            "kontakt",
            "contact",
            "standort",
            "location"
        )
        return patterns.any { it in lower }
    }
}
