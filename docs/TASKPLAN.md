# High-End Taskplan: Pfad A Android Personal Companion

Stand: 2026-05-12

Dieser Taskplan ist der aktuelle Leitfaden fuer Pfad A. Er basiert auf `docs/review.md`, `docs/vision.md` und dem inzwischen implementierten v3-Foundation-Stand.

## Bereits umgesetzt

- Backend in `backend/` konsolidiert.
- Markdown-Dokumente in `docs/` konsolidiert.
- `.env.example` angelegt.
- Backend-Auth per Bearer Token eingefuehrt.
- Android-Konfiguration auf `BuildConfig.BACKEND_BASE_URL` und `BuildConfig.APP_API_TOKEN` zentralisiert.
- Android Manifest gehaertet: `allowBackup=false`, Services restriktiver exportiert.
- Backend v3 Datenmodell fuer Contacts, Rules, Messages, Drafts, SendAttempts, Notes, EventTickets, EventRecipients, Events und AuditLogs angelegt.
- `backend/migrate.py` als Seed-System fuer Persona, Kontakte, Notizen und Beispiel-Event aufgebaut.
- Android Room v8 mit Clean Reset und neuen lokalen Tabellen vorbereitet.
- `SyncWorker` auf `/messages/inbound` Decision Flow umgestellt.
- Android Kotlin Compile erfolgreich verifiziert.

## Phase 1: SendAttempt-Rueckmeldung stabilisieren

Ziel: Android darf niemals nur wegen generiertem Draft `SENT` anzeigen.

- `WhatsAppListener` so refactoren, dass RemoteInput-Sendeversuche `SENDING`, `SENT` oder `FAILED` lokal und im Backend melden.
- NotificationData-Registry pro NotificationKey/Sender/TextHash stabilisieren.
- Self-loop-Schutz ueber Draft-ID, SendAttempt-ID, Reply-Hash und Zeitfenster.
- Mehrere schnelle Nachrichten desselben Kontakts sauber speichern und einzeln entscheiden.
- Alte/entfernte Notifications als `FAILED` oder `NEEDS_REVIEW` behandeln.

Akzeptanz:

- Zwei schnelle Nachrichten gehen nicht verloren.
- Keine doppelte Antwort.
- UI zeigt `SENT` nur nach realem RemoteInput-Erfolg.

## Phase 2: Accessibility-Fallback begrenzen

Ziel: Accessibility ist optionaler Fallback, kein blinder Autopilot.

- Consent-Screen vor Nutzung.
- `maybeAddTypo()` entfernen oder deaktivieren.
- Kein Auto-Send bei Arbeit, unbekannt, Gruppe oder sensiblen Themen.
- Chatname vor dem Senden gegen Zielkontakt pruefen.
- Kategorie-Events nicht per blindem Klicken durch Empfaengerlisten senden.

Akzeptanz:

- Falscher Chat kann nicht automatisch gesendet werden.
- Fehler erscheinen als `FAILED_ACCESSIBILITY`.

## Phase 3: UI als Kontrollzentrum redesignen

Ziel: Die App wird auf Review, Events/Tickets und Kontrolle ausgerichtet.

- Eine MainActivity/NavGraph-Struktur festlegen.
- Globaler Pause-Schalter immer erreichbar.
- Setup/Health Screen: Backend, Token, Notification Access, Accessibility optional, Contacts, Battery Optimization, Simulation.
- Dashboard: naechste Aktionen, Review-Drafts, faellige Tickets, failed SendAttempts, letzte echte Sendung.
- Review Inbox: Draft anzeigen, bearbeiten, senden, verwerfen, Kontakt pausieren, Risiko/Grund anzeigen.
- Events/Tickets UI: Ticket erstellen, Zielgruppe waehlen, Empfaenger-Vorschau, Drafts pro Empfaenger, Freigabe, Fortschritt, Teilfehler.
- Contacts: Auto-Modus, Safety-Profil, Telefonnummer/Identitaet, Kategorien, Notizen-Tab.
- Global Notes: globale und Kategorie-Notizen mit Zeitfenster und Vorschau.
- Queue: Drafts, SendAttempts und EventRecipient-Status getrennt anzeigen.
- Logs: private Inhalte redaktieren.

Akzeptanz:

- Nutzer kann Auto-Send jederzeit global stoppen.
- Jede generierte Antwort ist als Draft, Auto-Send oder Sent erkennbar.
- Event-Ticket zeigt Empfaenger, Drafts und Einzelstatus.

## Phase 4: Scheduler, Retry und Event Tickets

Ziel: Events funktionieren auch ohne neue eingehende WhatsApp-Nachricht.

- Backend markiert faellige Tickets/Recipients.
- Android pollt faellige Recipients.
- Kategorie-Tickets erzeugen gestaffelte Sendungen.
- Retry nur fuer konkrete SendAttempts/Recipients, nicht blind fuer ganze Tickets.
- Teilfehler sauber speichern.

Akzeptanz:

- Kategorie-Ticket kann vorbereitet, reviewed, teilweise gesendet und nachverfolgt werden.

## Phase 5: Tests und Verifikation

- Backend: Auth, ContactResolver, NoteContext, PolicyEngine, EventTicketService, SendAttempt-Flow.
- Android: Dedupe-Key, RemoteInputSender, RetryWorker, EventRecipient Retry, PermissionHealth, Pause/Review ViewModels.
- Manuell: WhatsApp normal, WhatsApp Business, Bildschirm an/aus, Notification entfernt, offline/online, Gruppe, unbekannt, deaktivierter Kontakt, Kategorie-Event.

## Phase 6: Pfad B vorbereiten

- Channel-Abstraktion behalten.
- Ticket, Recipient, Draft und SendAttempt channel-neutral halten.
- Spaeterer Adapter: `WHATSAPP_CLOUD_API`.
- Android-Fragilitaet und UI-Learnings dokumentieren.
