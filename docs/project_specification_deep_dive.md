# WA Agent Project Specification v3

Dieses Dokument beschreibt den aktuellen technischen Zielzustand und den bereits implementierten v3-Foundation-Stand.

## 1. Architektur

```text
app/
  Android Personal Companion
  Notification capture, local queue, RemoteInput, Review UI

backend/
  FastAPI API
  SQLAlchemy domain model
  Seed/persona system

docs/
  Review, Vision, API, Datenbank, Projektlog und Agent-Handbook
```

Der wichtigste Architekturwechsel ist die Trennung zwischen Generierung, Entscheidung und echtem Versand. Ein Draft ist nicht gleich `SENT`.

## 2. Backend Modules

### `backend/database.py`

Definiert Enums, SQLAlchemy Models, Session Factory und `init_db()`.

Wichtige Modelle:

- `Contact`
- `ContactCategory`
- `ContactRule`
- `Message`
- `Draft`
- `SendAttempt`
- `Note`
- `EventTicket`
- `EventRecipient`
- `Event`
- `AuditLog`

### `backend/main.py`

FastAPI-App mit Token-Auth, Decision Flow, Notes, Contacts, Event Tickets, SendAttempts und Legacy Compatibility.

Kernendpunkt:

- `POST /messages/inbound`

### `backend/migrate.py`

Idempotentes Seed-System fuer:

- Persona-Kontakte
- Kategorien
- Auto-Modi
- Regeln
- globale Notizen
- Kategorie-Notizen
- Beispiel-Event-Ticket

## 3. Android Core Files

Wichtige Dateien:

- `app/src/main/java/com/example/whatsappagent/WhatsAppListener.kt`
- `app/src/main/java/com/example/whatsappagent/AutoreplyService.kt`
- `app/src/main/java/com/example/whatsappagent/AgentApplication.kt`
- `app/src/main/java/com/example/whatsappagent/worker/SyncWorker.kt`
- `app/src/main/java/com/example/whatsappagent/data/AppDatabase.kt`
- `app/src/main/java/com/example/whatsappagent/data/MessageEntity.kt`
- `app/src/main/java/com/example/whatsappagent/data/NoteEntity.kt`
- `app/src/main/java/com/example/whatsappagent/data/EventTicketEntity.kt`
- `app/src/main/java/com/example/whatsappagent/data/EventRecipientEntity.kt`
- `app/src/main/java/com/example/whatsappagent/data/SendAttemptEntity.kt`
- `app/src/main/java/com/example/whatsappagent/data/remote/AgentApiService.kt`
- `app/src/main/java/com/example/whatsappagent/data/remote/ApiModels.kt`
- `app/src/main/java/com/example/whatsappagent/data/remote/AgentRepository.kt`

## 4. Message Decision Flow

```text
Notification
  -> WhatsAppListener
  -> Room messages_queue
  -> SyncWorker
  -> POST /messages/inbound
  -> ContactResolver + NoteContext + PolicyEngine + ReplyGenerator
  -> Draft + AgentDecisionResponse
  -> Android local status
  -> RemoteInput only if AUTO_SEND_ALLOWED
  -> SendAttempt status callback
```

Entscheidungen:

- `IGNORE`
- `DRAFT_ONLY`
- `NEEDS_REVIEW`
- `AUTO_SEND_ALLOWED`
- `BLOCKED`

Risiko:

- `LOW`
- `MEDIUM`
- `HIGH`
- `CRITICAL`

## 5. Notes and Memory

Notiz-Scopes:

- `GLOBAL`
- `CATEGORY`
- `CONTACT`

Notizen koennen pinned, priorisiert und zeitlich begrenzt sein. Abgelaufene Notizen bleiben fuer den Nutzer sichtbar, werden aber nicht mehr in Prompts genutzt.

## 6. Event Tickets

Ein Event Ticket ist ein Auftrag, nicht nur ein einzelnes geplantes Senden.

Beispiel:

- Anlass: Feiertag
- Zielgruppe: `EXTENDED_FAMILY`
- Modus: Review-before-send
- geplanter Zeitpunkt: morgen 09:00
- Basisprompt: persoenlicher Gruss

`prepare` erzeugt Empfaenger und Drafts. `approve` gibt den Versand frei. Jeder Empfaenger hat eigenen Status und spaeter eigenen SendAttempt.

## 7. Security and Safety

Aktueller Stand:

- Backend-Requests sind mit Bearer Token geschuetzt.
- Android URL und Token sind zentral in `BuildConfig`.
- `allowBackup=false` ist gesetzt.
- eigene Services sind soweit moeglich nicht exportiert.
- NotificationListener/Accessibility bleiben an Android-Bind-Permissions gebunden.

Weiter offen:

- Accessibility-Fallback strenger begrenzen.
- Consent-Screen fuer Accessibility.
- SendAttempt-Rueckmeldung aus RemoteInput/Accessibility fertig verdrahten.
- Logs konsequent redaktieren.

## 8. UI Target

Die UI soll nicht mehr primaer eine technische Queue-App sein, sondern ein Kontrollzentrum:

- Setup und Health
- globaler Pause-Modus
- Review Inbox
- Contacts mit Auto-Modus, Safety-Profil, Kategorien und Notizen
- Global Notes und Kategorie-Notizen
- Events/Tickets mit Empfaenger-Vorschau, Drafts, Freigabe und Teilfehlern
- Queue mit Drafts, SendAttempts und EventRecipient-Status
- Logs ohne private Inhalte

## 9. Current Verification

Bekannter erfolgreicher Android-Compile:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:compileDebugKotlin
```

Python ist auf diesem System nicht direkt auf PATH verfuegbar. Der Nutzer fuehrt Backend-Skripte ueber Anaconda aus.

## 10. Next Implementation Priorities

1. RemoteInput/Accessibility-SendAttempt-Rueckmeldung komplett auf v3.
2. UI-Redesign fuer Review/Events/Tickets.
3. Backend Tests fuer Auth, ContactResolver, Notes, Policy, Events und Statusflow.
4. Android Tests fuer Dedupe, SendAttempt, Retry und Pause/Review ViewModels.
5. Event Recipient Scheduler und Retry verfeinern.
