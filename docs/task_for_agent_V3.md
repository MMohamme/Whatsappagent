# Task for New Agent: Continue Pfad A v3

Nutze diesen Text als Kurzbriefing fuer einen neuen Chat.

## Context

Projekt: `C:\Users\bassa\AndroidStudioProjects\Whatsappagent`

Ziel: Android Personal Companion fuer eigene WhatsApp/WhatsApp-Business Nutzung. Hybrid-Modell: Familie/Freunde koennen bei Low-Risk automatisch beantwortet werden; Arbeit, unbekannte Kontakte, Gruppen und sensible Themen gehen in Review.

## Current State

- Backend liegt in `backend/`.
- Dokumente liegen in `docs/`.
- Backend v3 hat Bearer-Token-Auth, neues Datenmodell, Notes, EventTickets, EventRecipients, Drafts und SendAttempts.
- Android nutzt `BuildConfig.BACKEND_BASE_URL` und `BuildConfig.APP_API_TOKEN`.
- Room v8 ist aktiv.
- `SyncWorker` nutzt `/messages/inbound`.
- Android Kotlin Compile war erfolgreich.
- Python ist nicht direkt auf PATH; Backend-Skripte werden vom Nutzer via Anaconda ausgefuehrt.

## Important Docs

- `docs/agent_handbook.md`
- `docs/TASKPLAN.md`
- `docs/BACKEND_INTEGRATION_GUIDE.md`
- `docs/databank.md`
- `docs/project_specification_deep_dive.md`
- `docs/review.md`
- `docs/vision.md`

## Next Work

1. RemoteInput/Accessibility-SendAttempt-Rueckmeldung auf v3 verdrahten.
2. UI voll auf Review/Events/Tickets redesignen.
3. Events/Tickets fuer Kategorie-Gruppen robust machen.
4. Tests und manuelle Device-Testmatrix aufbauen.

## User Preference

Der Nutzer erlaubt, bei Bedarf etwas aus Git wiederherzustellen. Trotzdem keine fremden Aenderungen ohne Not revertieren. Der Nutzer fuehrt Python-Skripte ueber Anaconda aus.

## Verification

Android Build:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:compileDebugKotlin
```

Backend Start:

```powershell
python -m uvicorn backend.main:app --reload
```

Seed:

```powershell
python -m backend.migrate
```
