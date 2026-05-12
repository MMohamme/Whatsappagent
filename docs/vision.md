# Vision: Stabiler WhatsApp-Agent

Stand: 2026-05-12

## Statusupdate nach v3-Foundation

Die Vision bleibt gueltig: Pfad A ist der Android Personal Companion, Pfad B bleibt spaeter die professionelle Cloud/API-Produktlinie. Seit der urspruenglichen Analyse wurde die Basis fuer Pfad A konkretisiert:

- Backend wurde in `backend/` konsolidiert.
- Docs wurden in `docs/` konsolidiert.
- Backend v3 hat Auth, neues Datenmodell, Notes, EventTickets, Drafts und SendAttempts.
- Android nutzt zentrale Backend-URL und Token.
- Room v8 ist fuer Notes, EventTickets, EventRecipients und SendAttempts vorbereitet.
- `SyncWorker` spricht den neuen `/messages/inbound` Decision Flow.

Die naechste Produktarbeit ist klar:

- RemoteInput/Accessibility-SendAttempt-Rueckmeldung fertig verdrahten.
- App-UI komplett als Review/Events/Tickets-Kontrollzentrum neu ausrichten.
- Event Tickets fuer Kategorie-Gruppen robust vorbereiten, reviewen, senden und pro Empfaenger nachverfolgen.
- Tests und manuelle Device-Matrix aufbauen.

## Zielbild

Das Ziel ist eine WhatsApp-Agent-App, die unter realen Bedingungen zuverlaessig Nachrichten empfangen, bewerten, Antworten erzeugen und kontrolliert senden kann. Kurzfristig soll sie fuer den eigenen Gebrauch mit WhatsApp/WhatsApp Business App moeglichst stabil funktionieren. Langfristig soll daraus ein professionelles Produkt entstehen, das mit echten Nutzern, Datenschutz, Rollen, Audits, stabiler Infrastruktur und klaren Sicherheitsgrenzen betrieben werden kann.

Die wichtigste strategische Erkenntnis aus Code-Review und Recherche:

- Fuer private WhatsApp-App und WhatsApp Business App gibt es keinen sauberen, offiziellen App-API-Weg fuer vollautomatisches Lesen und Antworten.
- Der aktuelle Android-Weg ueber NotificationListener, RemoteInput und Accessibility kann fuer einen persoenlichen Prototyp funktionieren, bleibt aber fragil.
- Fuer ein professionelles Produkt ist die WhatsApp Business Platform / Cloud API der robuste Zielpfad: Webhooks fuer eingehende Nachrichten, Graph API fuer ausgehende Nachrichten, Status-Webhooks fuer Zustellung, Templates fuer Nachrichten ausserhalb des 24h-Fensters.

## Recherche-Ergebnisse

### WhatsApp Business Platform / Cloud API

Die offizielle Business Platform ist API-basiert und nicht die mobile WhatsApp Business App. Sie ist fuer automatisierte, skalierbare Business-Kommunikation gedacht. Eingehende Nachrichten kommen per Webhook; ausgehende Nachrichten werden ueber HTTP/Graph API gesendet.

Wichtige Punkte:

- Webhooks liefern eingehende Nachrichten und Status-Updates wie sent/delivered/read/failed.
- Webhook-Endpoints muessen oeffentlich per HTTPS erreichbar sein.
- Webhook-Requests sollten per `X-Hub-Signature-256` validiert werden.
- Nach einer User-Nachricht ist ein 24-Stunden-Servicefenster offen, in dem freie Antworten moeglich sind.
- Ausserhalb dieses Fensters sind vorab genehmigte Message Templates erforderlich.
- Die Business Platform ist nicht identisch mit WhatsApp Messenger oder der kleinen WhatsApp Business App.

Quellen:
- [WhatsApp Business Platform Node.js SDK: Receiving Messages](https://whatsapp.github.io/WhatsApp-Nodejs-SDK/receivingMessages/)
- [WhatsApp Business Platform Node.js SDK: Webhooks start/signature handling](https://whatsapp.github.io/WhatsApp-Nodejs-SDK/api-reference/webhooks/start/)
- [WhatsApp Business Platform Node.js SDK: Send text messages](https://whatsapp.github.io/WhatsApp-Nodejs-SDK/api-reference/messages/text/)
- [Meta/WhatsApp API overview via Postman collection](https://www.postman.com/meta/whatsapp-business-platform/documentation/vdi189b/whatsapp-on-premises-api-deprecated)
- [Twilio: WhatsApp Consumer App vs Business App vs Business Platform](https://www.twilio.com/en-us/blog/whatsapp-business-platform-vs-whatsapp-business-app)

### Android NotificationListener + RemoteInput

Android unterstuetzt NotificationListenerServices, die System-Callbacks bekommen, wenn Notifications gepostet oder entfernt werden. Fuer Direct Reply kann `RemoteInput` verwendet werden, wenn die Notification eine passende Reply-Action anbietet.

Wichtige Punkte:

- NotificationListener muss mit `BIND_NOTIFICATION_LISTENER_SERVICE` deklariert werden.
- Android-Dokumentation zeigt `android:exported="false"` fuer den Listener.
- RemoteInput kann Text in ein PendingIntent einlegen, wenn die Notification diese Aktion bereitstellt.
- Diese Methode haengt davon ab, dass WhatsApp eine Notification mit ReplyAction anbietet und diese noch gueltig ist.
- Sie ist nicht dasselbe wie eine offizielle WhatsApp-API.

Quellen:
- [Android NotificationListenerService](https://developer.android.com/reference/android/service/notification/NotificationListenerService)
- [Android RemoteInput](https://developer.android.com/reference/android/app/RemoteInput)

### Android AccessibilityService

AccessibilityService kann UI-Inhalte beobachten und Aktionen ausloesen, ist aber laut Android primaer fuer Barrierefreiheit gedacht. Google Play verlangt fuer Accessibility-Nutzung klare Erklaerung, Zustimmung und Policy-Konformitaet. Besonders kritisch: neue Policy-Hinweise betonen, dass autonome Planung/Ausfuehrung ueber Accessibility fuer normale Apps problematisch beziehungsweise untersagt sein kann.

Wichtige Punkte:

- Android sagt: AccessibilityServices sollen Nutzern mit Behinderungen helfen.
- Google Play verlangt prominent disclosure und Zustimmung, wenn eine App Accessibility nutzt und keine echte Accessibility-App ist.
- Automation ueber Accessibility darf nicht breit/autonom im Sinne eines frei entscheidenden Assistenten agieren.
- Fuer ein professionelles Produkt ist Accessibility als Kern-Sendemechanismus ein Risiko.

Quellen:
- [Android AccessibilityService](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService)
- [Google Play: Use of AccessibilityService API](https://support.google.com/googleplay/android-developer/answer/10964491)
- [Google Play sensitive permissions/API policy preview](https://support.google.com/googleplay/android-developer/answer/16585319)

### Android WorkManager

WorkManager ist der richtige Android-Baustein fuer persistente Hintergrundarbeit, aber er garantiert nicht sofortige Ausfuehrung. Arbeit wird ausgefuehrt, wenn Constraints passen, und Worker haben Zeitlimits.

Wichtige Punkte:

- WorkManager ist fuer persistente Hintergrundarbeit empfohlen.
- Constraints wie Netzwerk, BatteryNotLow, Charging usw. steuern Ausfuehrung.
- Worker haben ein begrenztes Ausfuehrungsfenster.
- Fuer Echtzeit-Reply ist WorkManager allein nicht genug; Notification/Foreground/Backend Push muessen passend kombiniert werden.

Quellen:
- [Android WorkManager](https://developer.android.com/reference/androidx/work/WorkManager.html)
- [Android WorkManager work constraints](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work)

## Strategie-Entscheidung

### Pfad A: Personal Companion auf Android

Dieser Pfad ist fuer den eigenen Gebrauch mit installierter WhatsApp/WhatsApp Business App. Er nutzt:

- NotificationListener zum Lesen neuer Nachrichten aus Notifications.
- RemoteInput als bevorzugten Sendeweg.
- Accessibility nur als optionalen, stark begrenzten Fallback.
- Lokale Room-Queue fuer Offline/Retry.
- Backend fuer LLM, Kontaktregeln und Gedaechtnis.

Vorteile:
- Funktioniert mit privater WhatsApp-App und WhatsApp Business App, ohne Business Platform Migration.
- Geeignet fuer eigenen Prototyp.
- Kann schnell verbessert werden.

Nachteile:
- Fragil gegen WhatsApp-UI/Notification-Aenderungen.
- Nicht offiziell von WhatsApp als API gedacht.
- Accessibility ist policy- und vertrauensriskant.
- Gesperrter Bildschirm und geloeschte Notifications bleiben harte Grenzen.
- Nicht geeignet als professionelles Produkt fuer viele User.

### Pfad B: Professionelles Produkt mit WhatsApp Business Platform

Dieser Pfad ist fuer echte Nutzer, Kunden und Produktreife. Er nutzt:

- Offizielle WhatsApp Cloud API / BSP.
- Webhook fuer eingehende Nachrichten.
- Graph API fuer ausgehende Antworten.
- Status-Webhooks fuer sent/delivered/read/failed.
- Templates fuer Business-initiated Messages ausserhalb des 24h-Fensters.
- Multi-Tenant Backend mit Usern, Rollen, Audit, Billing und Datenschutz.

Vorteile:
- Stabiler und offizieller Kommunikationsweg.
- Keine Android-UI-Automation noetig.
- Server kann auch antworten, wenn Handy aus/gesperrt/offline ist.
- Besser fuer professionelle App, Teams, Kunden, Compliance.

Nachteile:
- Nicht fuer private WhatsApp-Konten.
- Business-Verifikation, Template-Regeln, Kosten und Policy-Grenzen.
- Telefonnummer muss zur Business Platform migriert oder separat betrieben werden.
- General-purpose AI Chatbot-Policy muss genau geprueft werden; Produkt sollte als kundenspezifischer Business-Assistent positioniert werden, nicht als allgemeiner Chatbot.

### Empfohlene Gesamtstrategie

Kurzfristig:
- Android Personal Companion stabilisieren, aber ehrlich als "best effort" markieren.
- RemoteInput priorisieren, Accessibility minimieren.
- Review/Pause/Safety-Modus einbauen.
- Codebase, DB, UI und Statusmodell bereinigen.

Langfristig:
- Business Platform als offizielles Produktziel planen.
- Backend so abstrahieren, dass Message-Quelle und Sendekanal austauschbar sind:
  - `android_notification`
  - `android_remote_input`
  - `whatsapp_cloud_api`
  - spaeter eventuell andere Kanaele
- Android-App wird dann eher Admin/Companion/Inbox-App, nicht der eigentliche WhatsApp-Sender.

## Zielarchitektur

### Kernprinzip

Der Agent darf nicht direkt "Text rein, Antwort raus, sofort senden" sein. Er braucht eine Entscheidungsmaschine:

1. Nachricht empfangen.
2. Kontakt stabil identifizieren.
3. Kontext laden.
4. Risiko klassifizieren.
5. Entscheidung treffen:
   - `IGNORE`
   - `DRAFT_ONLY`
   - `NEEDS_REVIEW`
   - `AUTO_SEND_ALLOWED`
   - `BLOCKED`
6. Antwort generieren.
7. Antwort validieren.
8. Senden oder Review.
9. Sendestatus bestaetigen.
10. Audit speichern.

### Backend-Komponenten

- `ChannelAdapter`: Android/CloudAPI/Webhook abstrahieren.
- `ContactResolver`: Name, Telefonnummer, WhatsApp-ID, Business-ID zusammenfuehren.
- `ConversationStore`: Nachrichten, Threads, Status, Metadaten.
- `MemoryStore`: dauerhafte Fakten, Kontaktregeln, Notizen, Vorlieben.
- `PolicyEngine`: darf der Agent antworten?
- `RiskClassifier`: Thema, Dringlichkeit, Sensitivitaet.
- `ReplyGenerator`: LLM-Antwort im Stil.
- `ReplyValidator`: JSON, Laenge, Sprache, verbotene Inhalte, Halluzinationen.
- `SendOrchestrator`: sendet ueber RemoteInput oder Cloud API.
- `Scheduler`: Events, Follow-ups, Templates.
- `AuditLog`: wer/was/warum/wann.

### Android-Komponenten

- `NotificationCaptureService`: nur Capture + ReplyAction extrahieren.
- `RemoteInputSender`: getrennte Klasse fuer Senden.
- `AccessibilityFallback`: optional, streng begrenzt, mit Disclosure und Review.
- `LocalQueue`: Room mit klaren Status.
- `SyncEngine`: Backend-Sync, Retry, Backoff, Konfliktloesung.
- `PermissionHealth`: Notification, Accessibility, Contacts, Battery, Backend.
- `AdminUI`: Pause, Review, Kontakte, Queue, Logs, Setup.

### Professionelle Cloud-API-Komponenten

- `/webhooks/whatsapp`: Meta Webhook GET Verification + POST ingestion.
- Signature verification mit App Secret.
- Schnelle 200-Antwort, Verarbeitung async in Queue.
- `/channels/whatsapp/send`: ausgehende Nachrichten ueber Graph API.
- Status-Webhook Verarbeitung fuer sent/delivered/read/failed.
- Template-Management fuer ausserhalb des Servicefensters.
- Tenant-Konfiguration fuer Phone Number ID, WABA ID, Access Token.

## Stabilitaetsstrategie fuer WhatsApp Lesen/Senden

### Fuer Android Personal Companion

Prioritaet 1: RemoteInput, nicht Accessibility.

Warum:
- RemoteInput nutzt die offizielle Android-Notification-Antwortschnittstelle.
- Es ist weniger riskant als UI-Klickautomation.
- Es funktioniert auch, wenn WhatsApp nicht aktiv offen ist, solange die Notification gueltig ist.

Massnahmen:
- Notification-Key, packageName, postTime, sender, textHash, actionIndex speichern.
- ReplyAction sofort extrahieren und in einer SendIntent-Registry halten.
- Antwort nicht laenger als die Notification-Lebensdauer verzoegern.
- Wenn Notification entfernt/zu alt ist: nicht blind Accessibility nutzen, sondern `NEEDS_REVIEW` oder "Sendefenster verloren".
- Pro Sender mehrere pending Notifications verwalten, nicht nur eine Map nach Sender.
- Self-loop nicht nur ueber Text vergleichen, sondern ueber eigene reply IDs, Zeitfenster und Status.
- Eingehende Nachrichten immer speichern, auch wenn Rate-Limit/Delay verhindert, dass sofort geantwortet wird.

Prioritaet 2: Accessibility nur als kontrollierter Fallback.

Massnahmen:
- In-App Disclosure + affirmative Consent.
- Nur fuer deterministische, enge Aktionen verwenden.
- Vor dem Senden Chat-Name und wenn moeglich Telefonnummer/Avatar/Thread mehrfach pruefen.
- Niemals ganze Chat-Suche blind mit erstem Treffer.
- Kein autonomer Sendefallback bei unsicherer Identitaet.
- Play-Store-Strategie klaeren: Falls Produkt in Play Store soll, Accessibility als Kernfunktion vermeiden.

Prioritaet 3: Betriebsrealitaet sichtbar machen.

Massnahmen:
- UI zeigt: RemoteInput verfuegbar/nicht verfuegbar.
- UI zeigt: Accessibility aktiv/inaktiv, aber "riskanter Fallback".
- UI zeigt: letzte erfolgreiche echte Sendung.
- UI zeigt: gesperrter Bildschirm kann nicht garantiert werden.

### Fuer professionelles Produkt

Prioritaet 1: WhatsApp Cloud API.

Massnahmen:
- Nummer an Business Platform anbinden.
- Webhooks fuer messages/statuses einrichten.
- App Secret Signature pruefen.
- Permanente Tokens/System User sauber verwalten.
- 24h Customer Service Window modellieren.
- Template-Nachrichten verwalten.
- Kein Android-Sendefallback fuer Business-Tenant.

Prioritaet 2: Inbox + Review.

Massnahmen:
- Nutzer sehen alle eingehenden Nachrichten.
- KI erstellt Vorschlaege.
- Auto-Reply nur fuer explizit erlaubte Low-Risk-Flows.
- Teams/Rollen spaeter: Admin, Agent, Viewer.

## Codebase-Vision

### Backend

Aktuell ist `backend/main.py` monolithisch. Ziel ist eine modulare FastAPI-Struktur:

```text
backend/
  app/
    main.py
    api/
      routes_messages.py
      routes_contacts.py
      routes_events.py
      routes_webhooks.py
      routes_admin.py
    core/
      config.py
      security.py
      logging.py
    db/
      models.py
      session.py
      migrations/
    services/
      contact_resolver.py
      policy_engine.py
      risk_classifier.py
      reply_generator.py
      reply_validator.py
      send_orchestrator.py
      scheduler.py
    channels/
      android.py
      whatsapp_cloud.py
    schemas/
      messages.py
      contacts.py
      events.py
```

Wichtige Verbesserungen:
- Auth fuer App-Requests.
- Webhook Signature Verification.
- Saubere Settings ueber `.env` und `pydantic-settings`.
- Alembic-Migrationen.
- Strukturierte Logs ohne private Message-Bodies per Default.
- Background Queue, z.B. Celery/RQ/Arq oder FastAPI Background Tasks als Start.
- Tests fuer Policy, Dedupe, Statusflow, Prompt JSON.

### Android

Aktuell sind Service, UI, Networking und State teils vermischt. Ziel:

```text
app/
  core/
    config/
    logging/
    permissions/
  data/
    local/
    remote/
    repository/
  domain/
    model/
    usecase/
  whatsapp/
    NotificationCaptureService.kt
    RemoteInputSender.kt
    AccessibilityFallbackService.kt
  worker/
    SyncWorker.kt
    RetryWorker.kt
  ui/
    setup/
    dashboard/
    contacts/
    queue/
    chat/
    events/
    logs/
```

Wichtige Verbesserungen:
- Eine einzige MainActivity/Navigationsarchitektur.
- Dependency Injection, z.B. Hilt.
- BuildConfig fuer Backend URL.
- Network Auth Interceptor.
- UI-State als StateFlow.
- Services klein und testbar machen.
- RemoteInput-Sendestatus getrennt von Backend-Generierungsstatus.

## Datenbank-Vision

### Problem im aktuellen Modell

Backend nutzt `contact_name` als Primary Key. Android nutzt teils Telefonnummer lokal, Backend kennt sie aber nicht. Nachrichtenstatus ist frei als String und vermischt generiert/gesendet. Es gibt keine klare Multi-Tenant-Struktur und keine Audit-Faehigkeit.

### Zielmodell

Kern-Tabellen:

- `users`: App-Nutzer.
- `tenants` oder `workspaces`: spaeter fuer Profi-Version.
- `channels`: android_local, whatsapp_cloud_api.
- `contacts`: stabile interne ID, display name, phone, wa_id, relation.
- `contact_rules`: Sprache, Stil, Auto-Modus, Sperrthemen, Delay-Profil.
- `threads`: Kanal + Kontakt + Thread-ID.
- `messages`: inbound/outbound, channel_message_id, text, media refs, timestamps.
- `reply_decisions`: risk_score, decision, reason, policy_version.
- `drafts`: generierte Antworten, Versionen, Validator-Ergebnis.
- `send_attempts`: provider, status, error, retry_count, sent_at.
- `events`: scheduled jobs, recurrence, target, status.
- `memories`: Fakten/Notizen mit Quelle, Gueltigkeit, Sensitivitaet.
- `audit_logs`: jede kritische Aktion.
- `templates`: WhatsApp Business Templates, Status, Sprache.

### Statusmodell

Empfohlen:

- `RECEIVED`: Nachricht wurde erfasst.
- `DEDUPED`: Duplikat erkannt.
- `CLASSIFIED`: Risiko/Kategorie erkannt.
- `DRAFTED`: Antwort generiert.
- `NEEDS_REVIEW`: Mensch muss pruefen.
- `SEND_PENDING`: Senden geplant.
- `SENDING`: Senden laeuft.
- `SENT`: Provider hat Sendung akzeptiert.
- `DELIVERED`: WhatsApp meldet delivered.
- `READ`: WhatsApp meldet read.
- `FAILED`: Senden fehlgeschlagen.
- `SKIPPED`: bewusst ignoriert.
- `BLOCKED`: Policy/Safety hat blockiert.

Wichtig: `SENT` darf erst gesetzt werden, wenn der Sendekanal wirklich eine Sendebestaetigung hat. Generierte Antworten sind nicht automatisch gesendet.

### Speicherung und Datenschutz

- Private Inhalte nicht unverschluesselt in Git/Backups.
- DB-Dateien ignorieren.
- Optional lokale Verschluesselung.
- Retention-Policy: z.B. Raw Messages nach X Tagen loeschen/anonymisieren.
- Sensitive Memory separat markieren.
- Export/Backup nur verschluesselt.

## UI-Vision

### Leitidee

Die UI muss kein schoenes Dashboard fuer Technikdaten sein, sondern ein Kontrollzentrum fuer Vertrauen. Sie muss dem Nutzer jederzeit beantworten:

- Ist Auto-Reply gerade aktiv?
- Fuer wen darf der Agent automatisch senden?
- Was wird als naechstes gesendet?
- Was wurde wirklich gesendet?
- Wo braucht der Agent meine Entscheidung?
- Warum wurde eine Antwort erzeugt oder blockiert?

### Hauptbereiche

1. Setup
   - Backend-Verbindung.
   - Permissions.
   - WhatsApp-Modus: Android Companion oder Cloud API.
   - Testnachricht.
   - Safety-Grundregeln.

2. Dashboard
   - Globaler Pause/Auto/Review-Schalter.
   - Kritische Warnungen.
   - Naechste Aktionen.
   - Failed Sends.
   - Health-Checks.

3. Inbox/Review
   - Eingehende Nachrichten.
   - KI-Drafts.
   - Bearbeiten, Senden, Verwerfen.
   - Entscheidung begruenden lassen.

4. Kontakte
   - Auto-Modus pro Kontakt.
   - Sprache/Stil.
   - Beziehung.
   - Sperrthemen.
   - Telefonnummer/ID.
   - letzte echte Sendung.

5. Queue
   - Technischer Statusflow.
   - Send Attempts.
   - Retry nur kontrolliert.

6. Events
   - echte Scheduler-Ansicht.
   - Empfaengerliste.
   - Textvorschau.
   - Template/24h-Fenster-Hinweise.

7. Logs/Audit
   - Nutzerfreundliche Fehler.
   - Debug-Details getrennt.
   - Private Inhalte redaktiert.

### UI-Prinzipien

- Default: Review-Modus, nicht sofort Auto.
- Neue Kontakte nie blind automatisch aktivieren.
- Kritische Aktionen brauchen Confirm.
- Icons ueber Material Icons, keine kaputten Emoji-Symbole.
- Deutsch/Arabisch/RTL testen.
- Accessibility Labels fuer Buttons.
- Lange Namen und grosse Schriftgroessen einplanen.

## Feature-Vision

### MVP fuer stabilen eigenen Gebrauch

- Global Pause.
- Review-Modus.
- Kontakt-Auto-Regeln.
- Sauberes Statusmodell.
- RemoteInput-first Sender.
- Keine blinde Accessibility-Automation.
- Dedupe stabilisieren.
- Fehler/Retry sichtbar.
- Persona-Encoding reparieren.
- Simulationsmodus: Antwort generieren ohne Senden.

### Fortgeschrittener Personal Agent

- Stilprofile pro Kontakt.
- Memory mit Gueltigkeit.
- Safety-Klassifizierung.
- Tageszeit/Delay-Modell.
- Voice note/media handling als `NEEDS_REVIEW`.
- Manuelles Feedback: gute/schlechte Antwort.
- Test-Sandbox mit anonymisierten Chat-Szenarien.

### Profi-Version

- WhatsApp Cloud API Onboarding.
- Webhook-Inbox.
- Multi-User/Multi-Tenant.
- Rollen/Rechte.
- Audit Log.
- Templates.
- Team Inbox.
- Analytics.
- SLA/Monitoring.
- Billing/Plans.
- Datenschutzexport/Loeschung.

## Sicherheits- und Policy-Vision

### Safety Gates

Immer Review oder Block bei:

- Notfall/Gesundheit.
- Geld, Bank, Schulden, Zahlungen.
- Recht/Polizei/Behoerden.
- Arbeit mit Konsequenzen: Kuendigung, Schicht, Vertrag.
- Beziehungskonflikte.
- Passwoerter, Codes, private Dokumente.
- unbekannte Kontakte.
- unsicherer Empfaenger.
- Gruppen.

### Produktpositionierung

Fuer Profi-Version sollte die App nicht als "allgemeiner AI-Chatbot in WhatsApp" positioniert werden. Sicherer ist:

- Business-Kommunikationsassistent.
- Kundenservice/Termin/FAQ/Workflow-Assistent.
- Nutzerdefinierte, eng begrenzte Automationen.
- Menschliche Review fuer sensible Faelle.

## Konkrete technische Entscheidungen fuer den Taskplan

1. Zwei Modi definieren:
   - `ANDROID_COMPANION`
   - `WHATSAPP_CLOUD_API`

2. Message-Schnittstelle abstrahieren:
   - `InboundMessage`
   - `OutboundDraft`
   - `SendAttempt`
   - `DeliveryStatus`

3. Statusmodell zuerst umbauen, bevor neue Features kommen.

4. UI auf Kontrolle und Review ausrichten.

5. Datenmodell zuerst mit stabilen IDs planen.

6. Android Accessibility nicht als Kernweg fuer Profi-Ziel einplanen.

7. Cloud API als professionelle Zielarchitektur vorbereiten, auch wenn MVP noch Android nutzt.

8. Tests fuer Entscheidungen statt nur Endpoints:
   - Darf antworten?
   - Muss reviewen?
   - Welcher Kontakt?
   - Wurde wirklich gesendet?
   - Was passiert bei Retry?

## Phasen-Idee fuer den naechsten Taskplan

### Phase 0: Projekt hygiene und Sicherheit

- `.gitignore`, Secrets, DB, Encoding.
- URL-Konfiguration.
- Auth.
- Logging redaction.

### Phase 1: Daten- und Statusmodell

- Kontakte mit IDs/Telefonnummern.
- Messages/Attempts/Drafts.
- Alembic/Room Migrationen.
- Einheitliches Statusmodell.

### Phase 2: Android Stabilisierung

- RemoteInput-first.
- Dedupe.
- Send confirmation.
- Accessibility begrenzen.
- WorkManager/Retry neu.

### Phase 3: Safety und Review

- Policy Engine.
- Risk Classifier.
- Review UI.
- Global Pause.
- Simulationsmodus.

### Phase 4: UI Professionalierung

- Setup Wizard.
- Dashboard als Kontrollzentrum.
- Kontakte als Sicherheitsprofile.
- Queue/Chat/Event Redesign.
- UTF-8/Material Icons/Accessibility.

### Phase 5: Cloud API Vorbereitung

- Webhook Routes.
- Channel Adapter.
- Signature Verification.
- Send API.
- Status Webhooks.
- Templates.

### Phase 6: Profi-Produkt

- Multi-Tenant.
- Rollen.
- Audit.
- Billing.
- Monitoring.
- Datenschutzfeatures.

## Schlussfolgerung

Die beste Vision ist nicht "WhatsApp-App irgendwie besser fernsteuern", sondern ein zweigleisiger Aufbau:

1. Ein stabiler Android Companion fuer den persoenlichen Gebrauch, mit ehrlichen Grenzen und starkem Review/Pause-System.
2. Eine professionelle WhatsApp Business Platform Architektur fuer echte Nutzer, Automatisierung, Skalierung und Compliance.

Alles im Code sollte ab jetzt auf diese Trennung einzahlen. Dann kann das Projekt erst kurzfristig nutzbar und spaeter produktfaehig werden, ohne dass die fragile Android-Automation die ganze Architektur dominiert.
