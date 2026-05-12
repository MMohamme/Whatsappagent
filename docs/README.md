# Documentation

Die Markdown-Dokumente liegen gesammelt in `docs/`, damit der Projekt-Root fuer Build-Dateien, `app/` und `backend/` frei bleibt.

## Current Source of Truth

- `agent_handbook.md`: kompakter Arbeitskontext fuer neue Chats und Agenten.
- `BACKEND_INTEGRATION_GUIDE.md`: aktueller v3 API-Vertrag zwischen Android und Backend.
- `databank.md`: aktuelles Backend- und Android-Datenmodell.
- `project_specification_deep_dive.md`: technische v3-Projektspezifikation und naechste Prioritaeten.
- `TASKPLAN.md`: aktueller High-End-Taskplan fuer Pfad A.
- `implementation_plan.artifact.md`: technische Arbeitspakete fuer die naechste Implementierung.
- `task_for_agent_V3.md`: Kurzbriefing fuer einen neuen Chat/Agenten.
- `research.artifact.md`: Recherche-Zusammenfassung fuer Pfad A und Pfad B.

## Strategy and History

- `review.md`: urspruengliches Review plus aktueller Statusblock.
- `vision.md`: Produkt-/Technikvision plus aktueller Statusblock.
- `app.md`: Projektlog mit v3 Foundation Update und aelterer Historie.

## Current State

Stand 2026-05-12:

- Backend liegt in `backend/`.
- Docs liegen in `docs/`.
- Backend v3 mit Auth, neuem Datenmodell, Notes, Event Tickets und SendAttempts ist angelegt.
- Android nutzt zentralisierte Backend-URL und Token.
- Room v8 enthaelt lokale Tabellen fuer Notes, EventTickets, EventRecipients und SendAttempts.
- `SyncWorker` nutzt `/messages/inbound`.
- Offen sind vor allem SendAttempt-Rueckmeldung aus RemoteInput/Accessibility und das UI-Redesign fuer Review/Events/Tickets.
