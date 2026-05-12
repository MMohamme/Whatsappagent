# Backend

FastAPI backend for the Android WhatsApp Personal Companion.

This folder is the v3 backend home. Root-level `main.py`, `database.py` and `migrate.py` were consolidated here.

Run from the project root with Anaconda/Python:

```powershell
cd C:\Users\bassa\AndroidStudioProjects\Whatsappagent
python -m uvicorn backend.main:app --reload
```

Useful scripts:

```powershell
python -m backend.migrate
```

Environment:

- Copy `.env.example` to `.env`.
- Set `GEMINI_API_KEY`.
- Keep `APP_API_TOKEN` aligned with Android `APP_API_TOKEN` Gradle property.

Core endpoints:

- `GET /health`
- `POST /messages/inbound`
- `POST /generate` as a temporary compatibility wrapper
- `GET|POST|PATCH|DELETE /contacts`
- `GET|POST|PATCH|DELETE /notes`
- `POST|GET /event-tickets`
- `POST /event-tickets/{id}/prepare`
- `POST /event-tickets/{id}/approve`
- `POST /event-tickets/{id}/cancel`
- `GET /event-recipients/due`
- `POST /drafts/{draft_id}/send-attempts`
- `PATCH /send-attempts/{attempt_id}`

All non-health endpoints require:

```http
Authorization: Bearer <APP_API_TOKEN>
```

Default local behavior:

- SQLite DB path: project-root `whatsapp_agent.db`
- `RESET_DB_ON_START=1` recreates the v3 schema on backend startup
- `python -m backend.migrate` seeds contacts, notes and a sample event ticket
