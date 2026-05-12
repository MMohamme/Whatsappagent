# Database Documentation v3

Dieses Dokument beschreibt den aktuellen Datenstand nach dem Pfad-A-Umbau. Clean Reset ist erlaubt; alte Live-Datenbanken sind nicht die Migrationsquelle fuer das Produktmodell.

## Backend Database

Datei: `whatsapp_agent.db` im Projekt-Root.

Definition: `backend/database.py`

Seed: `backend/migrate.py`

Standardverhalten:

- `RESET_DB_ON_START=1` erzeugt die Struktur beim Backend-Start neu.
- `python -m backend.migrate` seedet Kontakte, Regeln, Notizen und ein Beispiel-Event.
- `DATABASE_URL` kann die SQLite-URL ueberschreiben.

## Backend Tables

### `contacts`

Stabile Kontaktidentitaet.

- `id`
- `display_name`
- `phone_number`
- `relation_type`
- `specific_relation`
- `preferred_lang`
- `is_active`
- `auto_mode`

### `contact_categories`

Mehrere Kategorien pro Kontakt, z.B. `CORE_FAMILY`, `EXTENDED_FAMILY`, `FRIEND`, `WORK`, `CUSTOM`.

### `contact_rules`

Persona- und Policy-Kontext pro Kontakt.

- Stil und Sprache
- Delay-Min/Max
- erlaubte Themen
- blockierte Themen
- freie Regeltexte

### `messages`

Inbound/outbound Verlauf.

- `id`
- `contact_id`
- `channel_message_id`
- `notification_key`
- `package_name`
- `role`
- `content`
- `timestamp`
- `status`

### `drafts`

Generierte Antworten, getrennt von echtem Versand.

- `message_id`
- `reply_text`
- `decision`
- `risk_level`
- `reason`
- `recommended_delay_ms`

### `send_attempts`

Jeder echte Sendeversuch.

- `draft_id`
- `status`
- `channel`
- `error`
- `attempt_count`
- `sent_at`

### `notes`

Kontakt-, Kategorie- und globale Notizen.

- `scope`: `GLOBAL`, `CATEGORY`, `CONTACT`
- `contact_id` optional
- `category` optional
- `content`
- `pinned`
- `priority`
- `valid_from`
- `expires_at`

### `event_tickets`

Fachlicher Auftrag, z.B. Feiertagsgruss an `EXTENDED_FAMILY`.

- Anlass
- Zieltyp: `CONTACT`, `CATEGORY`, `GLOBAL`
- Zielwert
- geplante Zeit
- Basisprompt/Text
- Versandmodus
- Status

### `event_recipients`

Aufgeloeste Empfaenger je Ticket.

- eigener Draft
- eigener Status
- eigene Fehlermeldung
- geplante Sendzeit

### `events`

Konkrete geplante Sendungen. Bleibt channel-neutral, damit spaeter Pfad B/Cloud API denselben Flow nutzen kann.

### `audit_logs`

Entscheidungen und Systemereignisse ohne private Inhalte per Default.

## Android Room Database

Definition: `app/src/main/java/com/example/whatsappagent/data/AppDatabase.kt`

Aktuelle Version: Room v8 mit `fallbackToDestructiveMigration()`.

Wichtige lokale Tabellen:

- `messages_queue`
- `notes`
- `event_tickets`
- `event_recipients`
- `send_attempts`
- bestehende Kontakt-/Cache-Tabellen fuer lokale UI und WhatsApp-Kontaktauflosung

Die lokale DB ist Queue, Cache und UI-Zustand. Die Backend-DB ist die fachliche Quelle fuer Policy, Drafts, Events und Audit.

## Status Model

Gemeinsame Statusnamen fuer Backend, Android und UI:

- `RECEIVED`
- `DEDUPED`
- `CLASSIFIED`
- `DRAFTED`
- `NEEDS_REVIEW`
- `SEND_PENDING`
- `SENDING`
- `SENT`
- `FAILED`
- `SKIPPED`
- `BLOCKED`

Legacy-Status duerfen nur noch in altem UI-Code oder historischen Daten vorkommen:

- `CAPTURED`
- `SYNCING`
- `SYNCED`
- `REPLY_PENDING`
- `REPLY_SENT`
- `REPLY_FAILED`
- `SYNC_FAILED`

## Note Time Logic

Prompt-aktive Notizen:

- Dauerhafte Notiz: `expires_at` ist leer.
- Zeitliche Notiz: `valid_from <= now` und `expires_at > now`.
- Abgelaufene Notizen bleiben sichtbar, werden aber nicht an den Reply-Generator gegeben.

Prioritaet im Prompt:

1. globale pinned Notizen
2. aktive Kategorie-Notizen
3. aktive Kontakt-Notizen
4. normale globale Notizen
5. normale Kontakt-Notizen

## Event Ticket Lifecycle

1. `DRAFT`: Ticket angelegt.
2. `PREPARED`: Empfaenger und Drafts erzeugt.
3. `APPROVED`: Nutzer hat Review freigegeben.
4. `SENDING`: mindestens ein Empfaenger wird gesendet.
5. `PARTIAL_FAILED`: manche Empfaenger fehlgeschlagen.
6. `SENT`: alle Empfaenger erfolgreich.
7. `CANCELLED`: abgebrochen.

Massen-/Kategorie-Events bleiben standardmaessig Review-before-send.
