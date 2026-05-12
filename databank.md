# Database Documentation (WA Agent)

## Schema History

### Version 7 (Aktuell)
*   **Notes Extension**: `expiresAtMillis` (Long) in lokaler `NoteEntity` für automatische Filterung.
*   **Contact Settings**: Primary Key auf `phoneNumber` umgestellt für eindeutige Identifizierung.
*   **Message Status**: Erweiterte Enums (`REPLY_FAILED`, `SYNC_FAILED`) für bessere Fehlerdiagnose in der Queue.

> [!NOTE]
> **v2.1 Fixes**: Backend database `whatsapp_agent.db` migrated manually (added `is_active`, `created_at` to `contacts`, `expiry_date` to `notes`, `status` to `messages`).

### Migration Paths
*   **5 → 6**: Umstellung auf `phoneNumber` als PK in `contact_settings`.
*   **6 → 7**: Hinzufügen von `expiresAtMillis` zur `notes` Tabelle (Local only).

---

## Entity Details

### MessageEntity (`messages_queue`)
*   `customId`: String (PK)
*   `sender`: String
*   `text`: String
*   `role`: String ("user"/"assistant")
*   `timestamp`: Long
*   `isSynced`: Boolean
*   `phoneNumber`: String?
*   `status`: MessageStatus (Enum)

### NoteEntity (`notes`)
*   `id`: Long (PK, Auto-gen)
*   `contactName`: String
*   `type`: NoteType
*   `text`: String
*   `createdAt`: Long
*   `expiresAtMillis`: Long? (NEU v2.1)
