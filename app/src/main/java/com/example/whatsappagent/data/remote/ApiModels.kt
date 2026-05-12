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
    @SerializedName("contact_name") val contactName: String,
    @SerializedName("relation_type") val relationType: String,
    @SerializedName("specific_relation") val specificRelation: String? = null,
    @SerializedName("preferred_lang") val preferredLang: String? = null,
    @SerializedName("behavior_rules") val behaviorRules: String? = null,
    @SerializedName("replies_count") val repliesCount: Int = 0,
    @SerializedName("last_seen") val lastSeen: String? = null,
    @SerializedName("active") val active: Boolean = true
)

data class ContactCreate(
    @SerializedName("contact_name") val contactName: String,
    @SerializedName("relation_type") val relationType: String,
    @SerializedName("specific_relation") val specificRelation: String? = null,
    @SerializedName("preferred_lang") val preferredLang: String? = null,
    @SerializedName("behavior_rules") val behaviorRules: String? = null
)

data class ContactUpdate(
    @SerializedName("specific_relation") val specificRelation: String? = null,
    @SerializedName("preferred_lang") val preferredLang: String? = null,
    @SerializedName("behavior_rules") val behaviorRules: String? = null,
    @SerializedName("active") val active: Boolean? = null
)

// --- QUEUE ---
data class QueueMessageResponse(
    @SerializedName("msg_id") val msgId: String,
    @SerializedName("contact_name") val contactName: String,
    @SerializedName("content") val content: String,
    @SerializedName("timestamp") val timestamp: String?,
    @SerializedName("status") val status: String,
    @SerializedName("category") val category: String?
)

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
    @SerializedName("history") val history: List<Map<String, String>> = emptyList()
)
