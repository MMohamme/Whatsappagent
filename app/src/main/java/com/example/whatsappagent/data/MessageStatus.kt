package com.example.whatsappagent.data

enum class MessageStatus {
    CAPTURED,       // Nachricht empfangen, noch nicht gesynct
    SYNCING,        // API-Call läuft
    DONE,           // AI-Reply generiert (veraltet, wird durch REPLY_SENT ersetzt)
    REPLY_PENDING,  // Reply wurde an Listener gesendet, Bestätigung steht aus
    REPLY_SENT,     // Reply erfolgreich zugestellt
    REPLY_FAILED,   // Alle Versuche fehlgeschlagen
    SYNC_FAILED     // Backend nicht erreichbar
}
