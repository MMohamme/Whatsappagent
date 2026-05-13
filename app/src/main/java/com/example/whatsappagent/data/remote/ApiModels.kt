package com.example.whatsappagent.data.remote

import com.google.gson.annotations.SerializedName

/**
 * API models for backend communication
 * These models match the backend Pydantic schemas
 */

// --- STATS ---
data class StatsResponse(
    @SerializedName("contacts") val contacts: ContactStats,
    @SerializedName("messages") val messages: MessageStats,
    @SerializedName("events") val events: EventStats,
    @SerializedName("chart_daily") val chartDaily: List<DailyCount>,
    @SerializedName("chart_categories") val chartCategories: List<CategoryCount>
)

data class ContactStats(
    @SerializedName("total") val total: Int,
    @SerializedName("active") val active: Int
)

data class MessageStats(
    @SerializedName("total") val total: Int,
    @SerializedName("total_replies") val totalReplies: Int,
    @SerializedName("pending_replies") val pendingReplies: Int,
    @SerializedName("failed_replies") val failedReplies: Int
)

data class EventStats(
    @SerializedName("pending") val pending: Int
)

data class DailyCount(
    @SerializedName("day") val day: String,
    @SerializedName("count") val count: Int
)

data class CategoryCount(
    @SerializedName("category") val category: String,
    @SerializedName("count") val count: Int
)

// --- CONTACT ---
data class ContactResponse(
    @SerializedName("id") val id: Long? = null,
    @SerializedName("contact_name") val contactName: String,
    @SerializedName("display_name") val displayName: String? = null,
    @SerializedName("phone_number") val phoneNumber: String? = null,
    @SerializedName("relation_type") val relationType: String,
    @SerializedName("specific_relation") val specificRelation: String? = null,
    @SerializedName("preferred_lang") val preferredLang: String? = null,
    @SerializedName("behavior_rules") val behaviorRules: String? = null,
    @SerializedName("replies_count") val repliesCount: Int = 0,
    @SerializedName("last_seen") val lastSeen: String? = null,
    @SerializedName("active") val active: Boolean = true,
    @SerializedName("auto_mode") val autoMode: String? = null,
    @SerializedName("categories") val categories: List<String> = emptyList()
)

data class ContactCreate(
    @SerializedName("contact_name") val contactName: String,
    @SerializedName("display_name") val displayName: String? = null,
    @SerializedName("phone_number") val phoneNumber: String? = null,
    @SerializedName("relation_type") val relationType: String,
    @SerializedName("specific_relation") val specificRelation: String? = null,
    @SerializedName("preferred_lang") val preferredLang: String? = null,
    @SerializedName("behavior_rules") val behaviorRules: String? = null,
    @SerializedName("auto_mode") val autoMode: String? = null,
    @SerializedName("categories") val categories: List<String>? = null
)

data class ContactUpdate(
    @SerializedName("display_name") val displayName: String? = null,
    @SerializedName("contact_name") val contactName: String? = null,
    @SerializedName("phone_number") val phoneNumber: String? = null,
    @SerializedName("relation_type") val relationType: String? = null,
    @SerializedName("specific_relation") val specificRelation: String? = null,
    @SerializedName("preferred_lang") val preferredLang: String? = null,
    @SerializedName("behavior_rules") val behaviorRules: String? = null,
    @SerializedName("auto_mode") val autoMode: String? = null,
    @SerializedName("categories") val categories: List<String>? = null,
    @SerializedName("active") val active: Boolean? = null
)

// --- QUEUE ---
data class QueueMessageResponse(
    @SerializedName("id") val id: Long? = null,
    @SerializedName("message_id") val messageId: Long? = null,
    @SerializedName("event_recipient_id") val eventRecipientId: Long? = null,
    @SerializedName("custom_id") val customId: String? = null,
    @SerializedName("message_custom_id") val messageCustomId: String? = null,
    @SerializedName("notification_key") val notificationKey: String? = null,
    @SerializedName("msg_id") val msgId: String,
    @SerializedName("contact_name") val contactName: String,
    @SerializedName("content") val content: String,
    @SerializedName("reply") val reply: String? = null,
    @SerializedName("timestamp") val timestamp: String?,
    @SerializedName("status") val status: String,
    @SerializedName("decision") val decision: String? = null,
    @SerializedName("risk_level") val riskLevel: String? = null,
    @SerializedName("reason") val reason: String? = null,
    @SerializedName("category") val category: String?,
    @SerializedName("recommended_delay_ms") val recommendedDelayMs: Long = 0
) {
    val draftId: Long?
        get() = id ?: msgId.toLongOrNull()

    val draftText: String
        get() = reply ?: content

    val displayStatus: String
        get() = status.ifBlank { decision ?: "NEEDS_REVIEW" }

    val isReviewable: Boolean
        get() = decision == "NEEDS_REVIEW" || status == "NEEDS_REVIEW"
}

// --- NOTES ---
data class NoteResponse(
    @SerializedName("id") val id: Int,
    @SerializedName("contact_name") val contactName: String,
    @SerializedName("content") val content: String,
    @SerializedName("pinned") val pinned: Boolean,
    @SerializedName("expiry_date") val expiryDate: String?, // ISO 8601
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("updated_at") val updatedAt: String?
)

data class NoteCreate(
    @SerializedName("content") val content: String,
    @SerializedName("pinned") val pinned: Boolean = false,
    @SerializedName("expiry_date") val expiryDate: String? = null
)

data class NoteUpdate(
    @SerializedName("content") val content: String? = null,
    @SerializedName("pinned") val pinned: Boolean? = null,
    @SerializedName("expiry_date") val expiryDate: String? = null
)

// --- EVENTS ---
data class EventResponse(
    @SerializedName("id") val id: Int,
    @SerializedName("contact_name") val contactName: String,
    @SerializedName("event_type") val eventType: String,
    @SerializedName("title") val title: String,
    @SerializedName("description") val description: String?,
    @SerializedName("status") val status: String,
    @SerializedName("scheduled_at") val scheduledAt: String?,
    @SerializedName("generated_text") val generatedText: String?,
    @SerializedName("sent_at") val sentAt: String?,
    @SerializedName("created_at") val createdAt: String?
)

data class EventCreate(
    @SerializedName("title") val title: String,
    @SerializedName("event_type") val eventType: String = "CUSTOM",
    @SerializedName("description") val description: String? = null,
    @SerializedName("scheduled_at") val scheduledAt: String, // ISO 8601
    @SerializedName("generated_text") val generatedText: String? = null
)

data class EventUpdate(
    @SerializedName("title") val title: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("scheduled_at") val scheduledAt: String? = null,
    @SerializedName("generated_text") val generatedText: String? = null,
    @SerializedName("status") val status: String? = null
)

data class EventTriggerResponse(
    @SerializedName("event_id") val eventId: Int,
    @SerializedName("contact_name") val contactName: String,
    @SerializedName("reply") val reply: String,
    @SerializedName("title") val title: String
)

// --- MESSAGE GENERATION ---
data class MessageHistory(
    @SerializedName("msg_id") val msgId: String,
    @SerializedName("role") val role: String,
    @SerializedName("content") val content: String,
    @SerializedName("timestamp") val timestamp: String? = null,
    @SerializedName("category") val category: String? = null
)

data class ApiError(
    @SerializedName("error") val error: String
)

/**
 * Payload for message generation endpoint
 */
data class MessageSchema(
    @SerializedName("custom_id") val customId: String,
    @SerializedName("sender") val sender: String,
    @SerializedName("text") val text: String,
    @SerializedName("history") val history: List<Map<String, String>> = emptyList(),
    @SerializedName("sender_display_name") val senderDisplayName: String = sender,
    @SerializedName("phone_number") val phoneNumber: String? = null,
    @SerializedName("package_name") val packageName: String? = null,
    @SerializedName("notification_key") val notificationKey: String? = null,
    @SerializedName("timestamp") val timestamp: String? = null
)

data class InboundMessageRequest(
    @SerializedName("custom_id") val customId: String,
    @SerializedName("sender_display_name") val senderDisplayName: String,
    @SerializedName("text") val text: String,
    @SerializedName("phone_number") val phoneNumber: String? = null,
    @SerializedName("package_name") val packageName: String? = null,
    @SerializedName("notification_key") val notificationKey: String? = null,
    @SerializedName("timestamp") val timestamp: String? = null,
    @SerializedName("history") val history: List<Map<String, String>> = emptyList()
)

data class AgentDecisionResponse(
    @SerializedName("decision") val decision: String,
    @SerializedName("message_id") val messageId: Long,
    @SerializedName("draft_id") val draftId: Long? = null,
    @SerializedName("reply") val reply: String? = null,
    @SerializedName("risk_level") val riskLevel: String,
    @SerializedName("reason") val reason: String,
    @SerializedName("recommended_delay_ms") val recommendedDelayMs: Long = 0
)

data class NoteRequest(
    @SerializedName("scope") val scope: String,
    @SerializedName("content") val content: String,
    @SerializedName("contact_id") val contactId: Long? = null,
    @SerializedName("category") val category: String? = null,
    @SerializedName("pinned") val pinned: Boolean = false,
    @SerializedName("priority") val priority: Int = 0,
    @SerializedName("valid_from") val validFrom: String? = null,
    @SerializedName("expires_at") val expiresAt: String? = null
)

data class EventTicketRequest(
    @SerializedName("title") val title: String,
    @SerializedName("target_type") val targetType: String,
    @SerializedName("scheduled_at") val scheduledAt: String,
    @SerializedName("target_contact_id") val targetContactId: Long? = null,
    @SerializedName("target_category") val targetCategory: String? = null,
    @SerializedName("base_text") val baseText: String? = null,
    @SerializedName("prompt") val prompt: String? = null,
    @SerializedName("stagger_min_seconds") val staggerMinSeconds: Int = 20,
    @SerializedName("stagger_max_seconds") val staggerMaxSeconds: Int = 90
)

data class EventTicketResponse(
    @SerializedName("id") val id: Long,
    @SerializedName("title") val title: String,
    @SerializedName("target_type") val targetType: String,
    @SerializedName("target_contact_id") val targetContactId: Long? = null,
    @SerializedName("target_category") val targetCategory: String? = null,
    @SerializedName("scheduled_at") val scheduledAt: String,
    @SerializedName("base_text") val baseText: String? = null,
    @SerializedName("prompt") val prompt: String? = null,
    @SerializedName("status") val status: String,
    @SerializedName("recipients_count") val recipientsCount: Int = 0,
    @SerializedName("recipients") val recipients: List<EventRecipientResponse> = emptyList()
)

data class EventRecipientResponse(
    @SerializedName("id") val id: Long,
    @SerializedName("ticket_id") val ticketId: Long,
    @SerializedName("contact_id") val contactId: Long,
    @SerializedName("contact_name") val contactName: String?,
    @SerializedName("draft_id") val draftId: Long? = null,
    @SerializedName("status") val status: String,
    @SerializedName("scheduled_at") val scheduledAt: String,
    @SerializedName("error") val error: String? = null,
    @SerializedName("draft") val draft: DraftResponse? = null
)

data class DraftResponse(
    @SerializedName("id") val id: Long,
    @SerializedName("message_id") val messageId: Long? = null,
    @SerializedName("event_recipient_id") val eventRecipientId: Long? = null,
    @SerializedName("reply") val reply: String,
    @SerializedName("decision") val decision: String,
    @SerializedName("risk_level") val riskLevel: String,
    @SerializedName("reason") val reason: String? = null,
    @SerializedName("recommended_delay_ms") val recommendedDelayMs: Long = 0
)

data class SendAttemptRequest(
    @SerializedName("channel") val channel: String = "ANDROID_REMOTE_INPUT"
)

data class SendAttemptUpdate(
    @SerializedName("status") val status: String,
    @SerializedName("error") val error: String? = null
)

data class SendAttemptResponse(
    @SerializedName("id") val id: Long,
    @SerializedName("draft_id") val draftId: Long? = null,
    @SerializedName("status") val status: String,
    @SerializedName("channel") val channel: String? = null,
    @SerializedName("sent_at") val sentAt: String? = null
)

data class DraftDecisionUpdate(
    @SerializedName("decision") val decision: String,
    @SerializedName("reply_text") val replyText: String? = null,
    @SerializedName("reason") val reason: String? = null
)
