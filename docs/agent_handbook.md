# WA Agent Technical Handbook v3

Dieses Handbook ist der kompakte Arbeitskontext fuer spaetere Agenten und neue Chats.

## Product Direction

Pfad A ist ein Android Personal Companion fuer die eigene WhatsApp/WhatsApp-Business Nutzung. Das Ziel ist kein blindes Massen-Auto-Reply-System, sondern ein hybrider Companion:

- vertraute Familie/Freunde koennen bei niedrigem Risiko automatisch beantwortet werden
- Arbeit, unbekannte Kontakte, Gruppen und sensible Themen gehen in Review
- RemoteInput ist der primaere Sendekanal
- Accessibility bleibt optionaler Fallback mit Consent und harten Identitaetschecks
- Events/Tickets und Notizen sind Kernfeatures

## Repository Structure

```text
Whatsappagent/
  app/                 Android/Kotlin app
  backend/             FastAPI, SQLAlchemy models, seed system
  docs/                review, vision, API, database and project docs
  .env.example         environment template
  whatsapp_agent.db    local/private SQLite file
```

Backend entrypoints:

- `backend/main.py`
- `backend/database.py`
- `backend/migrate.py`

## Current Backend

Backend v3 ist token-geschuetzt und channel-neutral modelliert.

Core endpoint:

- `POST /messages/inbound`

Wichtige API-Gruppen:

- Contacts
- Notes
- Event Tickets
- Event Recipients
- Draft decisions
- Send Attempts
- Legacy `/generate` wrapper

Die Backend-DB enthaelt:

- contacts
- contact rules/categories
- messages
- drafts
- send attempts
- notes
- event tickets
- event recipients
- events
- audit logs

## Current Android

Android nutzt:

- `BuildConfig.BACKEND_BASE_URL`
- `BuildConfig.APP_API_TOKEN`
- Room v8
- `SyncWorker` fuer `/messages/inbound`
- lokale Tabellen fuer Notes, EventTickets, EventRecipients und SendAttempts

Der zuletzt bekannte Build war erfolgreich mit:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:compileDebugKotlin
```

## Runtime Flow

1. `WhatsAppListener` erfasst Notification-Daten.
2. Android speichert die eingehende Nachricht lokal.
3. `SyncWorker` sendet die Nachricht an `/messages/inbound`.
4. Backend resolved Kontakt, sammelt aktive Notizen und bewertet Risiko.
5. Backend erzeugt einen Draft und eine Entscheidung.
6. Android speichert Draft/Status lokal.
7. Bei `AUTO_SEND_ALLOWED` darf Android RemoteInput versuchen.
8. Der echte Sendeversuch wird als SendAttempt ans Backend gemeldet.

Stand 2026-05-12: Schritt 8 ist backendseitig vorhanden, aber Android RemoteInput/Accessibility muss noch voll auf die neuen v3-SendAttempt-Endpunkte verdrahtet werden.

## Policy Basics

Auto-Modi:

- `OFF`
- `REVIEW`
- `AUTO_LOW_RISK`
- `AUTO_TRUSTED`

Safety Gates gehen immer in Review oder Block:

- Notfall/Gesundheit
- Geld/Bank/Zahlungen
- Recht/Polizei/Behoerden
- Arbeit mit Konsequenzen
- Beziehungskonflikte
- Passwoerter/Codes/private Dokumente
- unbekannte Kontakte
- unsichere Empfaenger
- Gruppen

## Notes

Notizen sind Prompt-Kontext und UI-Kernfeature:

- globale Notizen
- zeitliche globale Notizen
- Kategorie-Notizen
- Kontakt-Notizen
- zeitliche Kontakt-Notizen
- pinned/priority

Abgelaufene Notizen bleiben sichtbar, werden aber nicht im Prompt genutzt.

## Events/Tickets

Events werden als Tickets modelliert:

- Anlass
- Zielgruppe: Kontakt, Kategorie oder global
- geplante Zeit
- Basisprompt/Text
- Review-/Versandmodus
- Empfaengerliste
- Draft pro Empfaenger
- SendAttempt pro echtem Sendeversuch

Beispiel: Feiertagsgruss an `EXTENDED_FAMILY`.

Kategorie-Events werden vorbereitet und reviewt. Sie duerfen nicht blind an unsichere Empfaenger gesendet werden.

## UI Direction

Die UI soll als Kontrollzentrum neu ausgerichtet werden:

- globaler Pause-Schalter
- Setup/Health Screen
- Dashboard mit naechsten Aktionen
- Review Inbox
- Events/Tickets als eigener Hauptbereich
- Contacts mit Kategorien, Auto-Modus und Notizen
- Global Notes und Kategorie-Notizen
- Queue mit getrennten Drafts, SendAttempts und Recipient-Status
- redaktierte Logs

Alte Screens duerfen als Vorlage dienen, sollen aber nicht die Produktlogik fuehren.

## Open Next Steps

1. RemoteInput-SendAttempt-Rueckmeldung auf v3 verdrahten.
2. Accessibility-Fallback strikt begrenzen und `maybeAddTypo()` entfernen/deaktivieren.
3. UI voll auf Review/Events/Tickets redesignen.
4. Event-Recipient-Scheduler und Retry sauber mit Android verbinden.
5. Backend- und Android-Tests fuer Policy, Notes, Events und Statusflow ergaenzen.
