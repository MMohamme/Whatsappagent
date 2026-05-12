package com.example.whatsappagent.ui.model

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList

// ─────────────────────────────────────────────────────────────────────────────
// Domain Models für die UI-Schicht
// Diese sind von den Room-Entities bewusst getrennt — die UI hält eigene
// Zustände (z.B. active-Toggle, Notes, Events) die nicht in Room liegen.
// ─────────────────────────────────────────────────────────────────────────────

enum class ContactCategory { CORE_FAMILY, EXTENDED_FAMILY, FRIEND, WORK, UNKNOWN }

data class UiContact(
    val name: String,
    val phoneNumber: String, // NEU
    val lang: String,
    val relation: String,
    val category: ContactCategory,
    val style: String,
    val active: Boolean,
    val replies: Int,
    val lastSeen: String,
)

enum class NoteType { PERSONAL, TIMED }

data class UiNote(
    val id: Long,
    val contact: String,
    val type: NoteType,
    val text: String,
    val createdAt: String,
    val expiresAt: String = "",   // ISO date "2026-05-10" oder leer
    val expiresAtMillis: Long? = null // For logic and comparisons
)

data class UiEvent(
    val id: Long,
    val title: String,
    val emoji: String,
    val message: String,
    val targetCategory: ContactCategory,
    val expiresAt: String,        // ISO date oder leer
    val active: Boolean,
    val sent: Int,
    val createdAt: String,
)

// ─────────────────────────────────────────────────────────────────────────────
// Seed-Daten (entsprechen dem migrate.py Stand)
// ─────────────────────────────────────────────────────────────────────────────
fun seedContacts(): SnapshotStateList<UiContact> = mutableStateListOf(
    UiContact("Meine Mutter", "phone_1", "Arabisch (Syrisch)", "Mutter",      ContactCategory.CORE_FAMILY, "Antworte sehr warm und liebevoll. Nenn sie 'amy'. Benutze syrischen Dialekt.", true,  34, "vor 2h"),
    UiContact("Mohammed",     "phone_2", "Marokkanisch",        "Freund",      ContactCategory.FRIEND, "Locker und humorvoll. Anrede: 'muealim'.",                                    true,  12, "vor 5h"),
    UiContact("Tatjana Wydlok","phone_3", "Deutsch",            "Chefin (Groß)",ContactCategory.WORK, "Professionell und sachlich. Kein Small Talk.",                                false, 8,  "gestern"),
    UiContact("Mazen",        "phone_4", "Arabisch (Syrisch)", "Freund",      ContactCategory.FRIEND, "Locker, humorvoll. Anrede: 'muealim'.",                                       true,  21, "vor 1h"),
    UiContact("Alaa",         "phone_5", "Arabisch (Syrisch)", "Schwester",   ContactCategory.CORE_FAMILY, "Spielerisch, liebevoll, beschützend.",                                        true,  15, "vor 30min"),
    UiContact("scheni",       "phone_6", "Deutsch",            "Vorgesetzte", ContactCategory.WORK,  "Locker aber sachlich.",                                                       false, 5,  "vor 3 Tagen"),
    UiContact("Mouaz Türkei", "phone_7", "Arabisch (Syrisch)", "Bruder",      ContactCategory.CORE_FAMILY, "Locker, humorvoll. Anrede: 'muealim'.",                                       true,  9,  "vor 4h"),
    UiContact("Azhar",        "phone_8", "Englisch",           "Freund",      ContactCategory.FRIEND, "Casual and funny. Address as 'Bro'.",                                         true,  7,  "vor 2 Tagen"),
)

fun seedNotes(): SnapshotStateList<UiNote> = mutableStateListOf(
    UiNote(1L, "Mazen",        NoteType.PERSONAL, "Hat eine Tochter bekommen 🎉",                              "28.04.2026", ""),
    UiNote(2L, "Meine Mutter", NoteType.TIMED,    "Ist zurzeit bei Verwandten — nicht nach Besuch fragen",     "25.04.2026", "2026-05-10"),
)

fun seedEvents(): SnapshotStateList<UiEvent> = mutableStateListOf(
    UiEvent(
        id             = 1L,
        title          = "Zuckerfest",
        emoji          = "🌙",
        message        = "كل عام وانتَ بخير! Zuckerfest Mubarak — wünsche dir und deiner Familie alles Gute! 🌙",
        targetCategory = ContactCategory.CORE_FAMILY,
        expiresAt      = "2026-04-30",
        active         = true,
        sent           = 0,
        createdAt      = "28.04.2026",
    )
)
