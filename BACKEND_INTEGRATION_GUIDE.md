# Backend Integration Guide (WA Agent v2.0.0)

This document serves as the source of truth for the API contract between the FastAPI backend and the Android application.

## 1. Data Models (SQLAlchemy & Pydantic)

### Contact
*   **Database Table**: `contacts`
*   **Fields**:
    *   `contact_name`: String (Primary Key)
    *   `relation_type`: Enum (`CORE_FAMILY`, `EXTENDED_FAMILY`, `FRIEND`, `WORK`, `UNKNOWN`)
    *   `specific_relation`: String (Nullable)
    *   `preferred_lang`: String (Default: "Deutsch")
    *   `behavior_rules`: Text (Nullable)
    *   `is_active`: Boolean (Default: True)
    *   `created_at`: DateTime

### Message
*   **Database Table**: `messages`
*   **Fields**:
    *   `msg_id`: String (Primary Key)
    *   `contact_name`: String (Foreign Key)
    *   `role`: String ("user", "assistant")
    *   `content`: Text
    *   `timestamp`: DateTime
    *   `category`: String (Thematic category like "Arbeit", "Familie")
    *   `status`: String ("CAPTURED", "REPLY_PENDING", "REPLY_SENT", "REPLY_FAILED")

### Note
*   **Database Table**: `notes`
*   **Fields**:
    *   `id`: Integer (Auto-increment)
    *   `contact_name`: String (Foreign Key)
    *   `content`: Text
    *   `pinned`: Boolean (Default: False)
    *   `created_at`/`updated_at`: DateTime

### Event
*   **Database Table**: `events`
*   **Fields**:
    *   `id`: Integer
    *   `contact_name`: String
    *   `event_type`: Enum (`GREETING`, `REMINDER`, `PROACTIVE`, `CUSTOM`)
    *   `status`: Enum (`PENDING`, `TRIGGERED`, `SENT`, `FAILED`, `CANCELLED`)
    *   `scheduled_at`: DateTime
    *   `generated_text`: Text (The reply content to be sent)

---

## 2. API Endpoints

### 2.1 Core
*   `POST /generate`: Send a user message to generate an AI reply.
    *   **Body**: `{ "custom_id": str, "sender": str, "text": str, "history": [] }`
*   `GET /stats`: Dashboard data (counts, daily chart, categories).
*   `GET /health`: Basic health check.

### 2.2 Contacts
*   `GET /contacts`: List all contacts.
*   `POST /contacts`: Create a new contact.
*   `PATCH /contacts/{contact_name}`: Update settings or active state.
*   `DELETE /contacts/{contact_name}`: Wipe contact and all data.
*   `GET /contacts/{contact_name}/history`: Get conversation history.

### 2.3 Notes
*   `GET /contacts/{contact_name}/notes`: List notes (pinned first).
*   `POST /contacts/{contact_name}/notes`: Add a note.
*   `PATCH /contacts/{contact_name}/notes/{note_id}`: Edit or toggle pin.
*   `DELETE /contacts/{contact_name}/notes/{note_id}`: Delete note.

### 2.4 Events (Proactive Replies)
*   `GET /events`: List all events (optional `?status=` filter).
*   `GET /contacts/{contact_name}/events`: Events for specific contact.
*   `POST /contacts/{contact_name}/events`: Schedule new event.
*   `PATCH /events/{event_id}`: Update event text/status.
*   `POST /events/{event_id}/trigger`: Polled by Android to get reply text to send.

---

## 3. Business Logic Details
1.  **Rate Limiting**: Backend has a 30s limit per sender. Android should handle `429 Too Many Requests`.
2.  **Persona Rules**: The `behavior_rules` from the contact are combined with a system prompt and `notes` to generate the AI response.
3.  **Event Flow**:
    *   Android `SyncWorker` polls `GET /events?status=PENDING`.
    *   If current time > `scheduled_at`, Android calls `POST /events/{event_id}/trigger`.
    *   Android receives `reply` and `contact_name` and sends it via WhatsApp.
    *   Android updates event status to `SENT`.
