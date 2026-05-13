# Plan: WhatsApp Notification Parsing, One-Time Migration, Contact Sync, UI/UX Redesign

## Summary

- Fix WhatsApp notification ingestion so the app never treats the app label (`WhatsApp`, `WhatsApp Business`) as the contact.
- Parse compact/multiple-message notifications using Android `MessagingStyle` extras first, then safe fallbacks.
- Stop running `migrate.py` from backend startup; make migration/seeding a manual one-time step.
- Use Android phone contacts as the source for phone numbers and sync them into Room and backend contacts.
- Redesign the Android UI toward a modern, scalable, professional control-center style.

## Key Changes

- `WhatsAppListener` should receive validated parsed messages from a dedicated parser instead of reading `android.title` directly as the sender.
- `Notification.EXTRA_MESSAGES` / `android.messages` and `MessagingStyle` fields are the primary source for sender, text, timestamp, conversation title, and group status.
- Ambiguous app-title, compact summary, media, reaction, deleted, and group notifications must not auto-reply.
- Backend startup should only initialize/connect the schema; `python -m backend.migrate` seeds manually and is idempotent by default.
- Android contact indexing should read WhatsApp JIDs and normal phone contacts, update Room `contact_cache`, then PATCH backend contacts with `phone_number`.
- UI should emphasize operational control: Review Queue, Contacts confidence, Event ticket lifecycle, Safety controls, and Logs.

## Test Plan

- Unit-test notification parsing for one-to-one WhatsApp, WhatsApp Business, compact multi-contact, summary/app-title, group, media/deleted/reaction cases.
- Backend tests should verify startup without seeding, idempotent migrate runs, contact phone PATCH, and preservation of non-seed user metadata.
- Android checks: READ_CONTACTS triggers indexing, contact cache/backend phone numbers populate, SyncWorker includes phone number, `compileDebugKotlin` and `testDebugUnitTest` pass.

## Assumptions

- Android `MessagingStyle` extras are the safest source because Android documents sender/message/conversation metadata there.
- Backend migration remains manual; automatic phone completion happens from Android after contact permission is granted.
- Manual reset remains possible via `python -m backend.migrate --reset`.
