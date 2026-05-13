package com.example.whatsappagent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationParserTest {
    @Test
    fun appLabelsAreRejectedAsSenders() {
        assertTrue(NotificationParser.isAppLabel("WhatsApp"))
        assertTrue(NotificationParser.isAppLabel("WhatsApp Business"))
    }

    @Test
    fun singleConversationKeepsOnlyNewestMessage() {
        val messages = NotificationParser.normalizeMessagingMessages(
            listOf(
                NotificationParser.RawMessage("Mazen", "Hallo", 1L),
                NotificationParser.RawMessage("Mazen", "Bist du da?", 2L),
            )
        )

        assertEquals(1, messages.size)
        assertEquals("Bist du da?", messages.single().text)
        assertEquals("Mazen", messages.single().sender)
    }

    @Test
    fun compactMultipleContactsKeepsReliableSenderMessagePairs() {
        val messages = NotificationParser.normalizeMessagingMessages(
            listOf(
                NotificationParser.RawMessage("Mazen", "Hallo", 1L),
                NotificationParser.RawMessage("Alaa", "Salam", 2L),
            )
        )

        assertEquals(2, messages.size)
        assertEquals(listOf("Mazen", "Alaa"), messages.map { it.sender })
    }

    @Test
    fun summariesAndAppTitleMessagesAreIgnored() {
        val messages = NotificationParser.normalizeMessagingMessages(
            listOf(
                NotificationParser.RawMessage("WhatsApp", "2 neue Nachrichten", 1L),
                NotificationParser.RawMessage("WhatsApp Business", "2 messages", 2L),
            )
        )

        assertTrue(messages.isEmpty())
    }

    @Test
    fun mediaDeletedAndReactionMessagesAreUnsafeForAutoReply() {
        assertTrue(NotificationParser.isUnsafeReplyText("Foto"))
        assertTrue(NotificationParser.isUnsafeReplyText("Voice message"))
        assertTrue(NotificationParser.isUnsafeReplyText("Nachricht gel\u00f6scht"))
        assertTrue(NotificationParser.isUnsafeReplyText("Message deleted"))
        assertTrue(NotificationParser.isUnsafeReplyText("Reacted to your message"))
        assertTrue(NotificationParser.isUnsafeReplyText("OK"))
    }

    @Test
    fun unsafeMessagingStyleMessagesAreIgnoredDuringNormalization() {
        val messages = NotificationParser.normalizeMessagingMessages(
            listOf(
                NotificationParser.RawMessage("Mazen", "Foto", 1L),
                NotificationParser.RawMessage("Mazen", "Nachricht gel\u00f6scht", 2L),
            )
        )

        assertTrue(messages.isEmpty())
    }
}
