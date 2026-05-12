package com.example.whatsappagent.data

enum class MessageStatus {
    RECEIVED,
    DEDUPED,
    CLASSIFIED,
    DRAFTED,
    NEEDS_REVIEW,
    SEND_PENDING,
    SENDING,
    SENT,
    FAILED,
    SKIPPED,
    BLOCKED,

    // Legacy statuses kept during the v3 transition.
    CAPTURED,
    SYNCING,
    DONE,
    REPLY_PENDING,
    REPLY_SENT,
    REPLY_FAILED,
    SYNC_FAILED
}
