# Implementation Plan Artifact v3

Dieses Dokument ersetzt den alten Root-Artefaktplan und haelt die implementierte Foundation plus naechste technische Schritte fest.

## Implemented Foundation

- Backend folder: `backend/`
- Docs folder: `docs/`
- Environment template: `.env.example`
- FastAPI auth: Bearer `APP_API_TOKEN`
- Central Android config: `BuildConfig.BACKEND_BASE_URL`, `BuildConfig.APP_API_TOKEN`
- Backend schema: contacts, rules, categories, messages, drafts, send attempts, notes, event tickets, event recipients, events, audit logs
- Seed system: `backend/migrate.py`
- Android Room v8 with local notes, event tickets, event recipients and send attempts
- New API models and Retrofit endpoints
- `SyncWorker` decision flow via `/messages/inbound`

## Implementation Rules Going Forward

- Draft generation is not sending.
- Backend never marks `SENT` without Android SendAttempt success.
- Unknown contacts default to Review.
- Work contacts default to Review.
- Groups do not auto-send.
- Category events are review-before-send by default.
- Accessibility is optional fallback only.

## Immediate Work Packages

### A. RemoteInput Sender

- Extract sender component if needed.
- Persist NotificationData with notification key, package, sender, text hash and post time.
- Create/update SendAttempt around real RemoteInput call.
- Report stale notification as failed/review.

### B. Accessibility Fallback

- Add consent gate.
- Verify active package and chat title.
- Remove typo simulation.
- Disable for category bulk flows unless a safe chat context is already known.

### C. Review/Events UI

- Replace old queue-first UX with control-center UX.
- Add Review Inbox.
- Add Event Ticket detail with recipients, drafts and per-recipient status.
- Add global pause switch and setup health checklist.

### D. Tests

- Backend policy and status flow tests.
- Android Dedupe/SendAttempt/Retry tests.
- Device smoke matrix.
