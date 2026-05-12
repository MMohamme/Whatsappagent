# Backend Integration Guide v3

Dieses Dokument ist die aktuelle Schnittstellen-Quelle zwischen Android-App und FastAPI-Backend.

## Start

Backend vom Projekt-Root starten:

```powershell
cd C:\Users\bassa\AndroidStudioProjects\Whatsappagent
python -m uvicorn backend.main:app --reload
```

Seed/Clean-Reset:

```powershell
python -m backend.migrate
```

Der Backend-Code liegt in `backend/`. Die lokale SQLite-Datei `whatsapp_agent.db` bleibt privat und wird nicht als Produkt-Artefakt behandelt.

## Environment

Siehe `.env.example`.

- `GEMINI_API_KEY`: optional fuer echte LLM-Antworten, aktuell mit Fallback-Logik abgesichert.
- `APP_API_TOKEN`: Bearer Token fuer Android-Requests.
- `DATABASE_URL`: optionaler SQLAlchemy-Override.
- `BACKEND_BASE_URL`: Android/Tooling-Konfiguration.

Alle geschuetzten Endpoints erwarten:

```http
Authorization: Bearer <APP_API_TOKEN>
```

`GET /health` bleibt absichtlich ohne Token, damit Setup und Health Checks leicht pruefbar sind.

## Core Flow

1. Android erfasst eine WhatsApp/WhatsApp-Business Notification.
2. Android speichert die Nachricht lokal in Room.
3. `SyncWorker` sendet `POST /messages/inbound`.
4. Backend resolved Kontakt, sammelt aktive Notizen, bewertet Risiko und erzeugt Draft.
5. Backend antwortet mit `AgentDecisionResponse`.
6. Android speichert Draft/Status lokal.
7. Nur bei `AUTO_SEND_ALLOWED` wird RemoteInput-Senden versucht.
8. Android meldet echte SendAttempt-Status an das Backend.

Wichtig: Backend setzt nie `SENT`, bevor Android einen realen Sendeversuch erfolgreich gemeldet hat.

## Main Endpoint

### `POST /messages/inbound`

Request:

```json
{
  "custom_id": "local-stable-id",
  "sender_display_name": "Mutter",
  "phone_number": "+491234567",
  "package_name": "com.whatsapp",
  "notification_key": "android-notification-key",
  "text": "Bist du zuhause?",
  "timestamp": "2026-05-12T12:00:00",
  "history": [
    {"role": "user", "content": "Hi"}
  ]
}
```

Response:

```json
{
  "decision": "AUTO_SEND_ALLOWED",
  "message_id": 42,
  "draft_id": 7,
  "reply": "Ja, bin da.",
  "risk_level": "LOW",
  "reason": "Trusted low-risk contact",
  "recommended_delay_ms": 12000
}
```

Entscheidungen:

- `IGNORE`: keine Aktion.
- `DRAFT_ONLY`: Draft wird erzeugt, aber nicht automatisch gesendet.
- `NEEDS_REVIEW`: Nutzer muss pruefen.
- `AUTO_SEND_ALLOWED`: Android darf RemoteInput versuchen.
- `BLOCKED`: Safety Gate oder deaktivierter Kontakt.

## Compatibility Endpoint

### `POST /generate`

Legacy-Wrapper fuer alte Android-Pfade. Intern wird derselbe Decision Flow wie `/messages/inbound` benutzt. Neue App-Logik soll `/messages/inbound` verwenden.

## Contacts

- `GET /contacts`
- `POST /contacts`
- `PATCH /contacts/{contact_ref}`
- `DELETE /contacts/{contact_ref}`

Kontakte haben stabile IDs. Telefonnummer wird bevorzugt, Display-Name bleibt editierbar.

Wichtige Felder:

- `display_name`
- `phone_number`
- `relation_type`
- `specific_relation`
- `preferred_lang`
- `is_active`
- `auto_mode`
- `categories`
- `rules`

## Notes

- `GET /notes?scope=GLOBAL|CATEGORY|CONTACT&contact_id=&category=`
- `POST /notes`
- `PATCH /notes/{id}`
- `DELETE /notes/{id}`

Scopes:

- `GLOBAL`: gilt fuer alle Kontakte.
- `CATEGORY`: gilt fuer Kontakte einer Kategorie.
- `CONTACT`: gilt fuer genau einen Kontakt.

Zeitlogik:

- Ohne `expires_at` bleibt eine Notiz dauerhaft.
- Eine zeitliche Notiz ist prompt-aktiv, wenn `valid_from <= now` und `expires_at > now`.
- Abgelaufene Notizen bleiben sichtbar, werden aber nicht in Prompts genutzt.

## Event Tickets

- `POST /event-tickets`
- `GET /event-tickets`
- `GET /event-tickets/{id}`
- `PATCH /event-tickets/{id}` ist noch nicht final ausgebaut.
- `POST /event-tickets/{id}/prepare`
- `POST /event-tickets/{id}/approve`
- `POST /event-tickets/{id}/cancel`
- `GET /event-recipients/due`

Ticket-Status:

- `DRAFT`
- `PREPARED`
- `APPROVED`
- `SENDING`
- `PARTIAL_FAILED`
- `SENT`
- `CANCELLED`

Ein Kategorie-Ticket erzeugt pro Empfaenger einen eigenen Recipient und Draft. Massenversand bleibt standardmaessig Review-before-send.

## Send Attempts

- `POST /drafts/{draft_id}/send-attempts`
- `PATCH /send-attempts/{attempt_id}`
- `PATCH /drafts/{draft_id}/decision`

Android soll echte Sendeversuche so melden:

1. `SENDING`, sobald RemoteInput oder Accessibility wirklich versucht wird.
2. `SENT`, wenn der Kanal Erfolg meldet.
3. `FAILED`, `FAILED_NO_CHANNEL` oder `FAILED_ACCESSIBILITY`, wenn kein sicherer Versand moeglich ist.

Stand 2026-05-12: Backend-Endpunkte sind vorhanden. Die vollstaendige Verdrahtung in `WhatsAppListener`/RemoteInput/Accessibility ist der naechste wichtige Implementierungsschritt.

## Status Model

Nachrichten/Drafts/Attempts verwenden ein gemeinsames Vokabular:

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

Alte Status wie `CAPTURED`, `REPLY_PENDING` und `REPLY_SENT` duerfen nur noch fuer Compatibility oder historische Daten auftauchen.

## Current Android Contract

Android nutzt:

- `BuildConfig.BACKEND_BASE_URL` statt hartcodierter ngrok-URL.
- `BuildConfig.APP_API_TOKEN` fuer Bearer Auth.
- Room DB v8 mit Tabellen fuer Messages, Notes, EventTickets, EventRecipients und SendAttempts.
- `SyncWorker` fuer `/messages/inbound` und faellige EventRecipients.

Offen:

- RemoteInput-SendAttempt-Rueckmeldung voll auf v3 verdrahten.
- Accessibility-Fallback nur mit Consent und harten Identitaetschecks nutzen.
- UI voll auf Review/Events/Tickets redesignen.
