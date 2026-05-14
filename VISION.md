# Vision And Roadmap

This document captures the product motivation, long-term vision, current achievements, and the next steps for WA Agent Pro Control Center. It is the planning anchor for future implementation work.

## 1. Product Idea

The original goal is to build a fully autonomous WhatsApp agent that can reply to private messages automatically while sounding convincingly personal: natural timing, personal tone, small imperfections, context memory, and a recognizable voice.

The agent should eventually:

- detect and filter WhatsApp Business messages;
- identify the sender and relationship, such as mother, work, friends, unknown contacts;
- understand and remember conversation context;
- generate replies in the owner's personal style;
- support German and Syrian Arabic;
- answer automatically when policy allows it;
- work as reliably as possible even when the phone is locked;
- handle voice messages by transcribing, understanding, and answering them.

The deeper vision is not simply auto-reply. It is a personal communication layer that knows when to answer, when to wait, when to ask for review, and when to refuse.

## 2. Product Principles

Autonomy must be controlled and visible.

- The user owns the agent and can pause it at any time.
- The agent should not send sensitive replies without a safety decision.
- New or unknown contacts start in review-first mode.
- Every automatic send should have a traceable reason.
- Generated does not mean sent.
- Voice cloning and personal style features must be treated as sensitive identity features.
- Accessibility fallback is a fallback, not the long-term product foundation.

## 3. Current Achievements

### Backend foundation

- FastAPI backend consolidated under `backend/`.
- Token-protected routes for health, stats, inbound messages, contacts, notes, queue, draft decisions, event tickets, event recipients, and send attempts.
- SQLAlchemy model covering contacts, categories, contact rules, messages, drafts, send attempts, notes, event tickets, event recipients, legacy events, and audit logs.
- Migration/seed script for local development.
- Draft decision flow that separates generation, review, auto-send permission, send attempt, and final status.
- Event ticket model that can prepare recipients and drafts before sending.

### Android foundation

- Android app using Jetpack Compose, Room, WorkManager, Retrofit, and Material 3.
- `WhatsAppListener` for notification capture and RemoteInput reply actions.
- `AutoReplyService` as Accessibility fallback.
- Room cache for messages, contact settings/cache, notes, events, event tickets, event recipients, and send attempts.
- WorkManager paths for inbound sync, retry, contact indexing, queue polling, and event-recipient polling.
- Backend URL and API token wired through Gradle/BuildConfig and `.env`.

### Pro Control Center UI

- Phone-first redesign with Dashboard, Review, Contacts, Events, and Logs.
- Shared pro tokens and reusable Compose components.
- Review Queue with editable draft form, risk/status/category indicators, approve/block actions, and failure state.
- Contact detail and add/edit module restored.
- Notes module restored inside contact detail with add, pin, expiry, delete.
- Event creation form restored for single-contact and category event tickets.
- Safety strip, backend health, permissions status, and operational metrics surfaced.

### Documentation and design

- Architecture diagrams created in Mermaid.
- FigJam board generated for visual architecture review.
- Root README added for GitHub overview.
- UI redesign notes and manual test matrix documented.

## 4. Target Architecture

The architecture should become channel-based. Android notification automation is one channel. WhatsApp Cloud API, Telegram, and email can become later channels.

```text
Inbound Channel
  -> Contact Resolver
  -> Conversation Store
  -> Memory And Notes
  -> Policy Engine
  -> Risk Classifier
  -> Reply Generator
  -> Reply Validator
  -> Review Or Send Decision
  -> Send Orchestrator
  -> Send Attempt And Audit
```

Planned channel types:

- `ANDROID_NOTIFICATION`
- `ANDROID_REMOTE_INPUT`
- `ANDROID_ACCESSIBILITY_FALLBACK`
- `WHATSAPP_CLOUD_API`
- `TELEGRAM`
- `EMAIL`

## 5. Next Roadmap

### Phase 1: Stabilize the current Android companion

Goal: make the existing personal WhatsApp companion reliable enough for daily controlled testing.

- Verify end-to-end inbound notification capture on a real device.
- Confirm RemoteInput send behavior with active, locked, and recently unlocked screen states.
- Make failed send reasons visible and actionable in the UI.
- Strengthen retry behavior and avoid duplicate replies.
- Keep Accessibility fallback explicit, limited, and reviewable.
- Add a simulation mode that generates and reviews replies without sending.
- Improve manual device test coverage for WhatsApp and WhatsApp Business.

Acceptance:

- A received message can become a backend draft, appear in Review, be approved, sent, and tracked as a send attempt.
- A failed send produces a visible reason instead of silently disappearing.
- The user can pause all sending from the control center.

### Phase 2: Strengthen policy, review, and safety

Goal: make autonomy safer before making it broader.

- Define one canonical status model across backend, Room, and UI.
- Add clearer policy reasons for `NEEDS_REVIEW`, `BLOCKED`, `AUTO_SEND_ALLOWED`, and retry failure.
- Add contact-level auto modes: off, review, low-risk auto, trusted auto.
- Add hard safety gates for money, health, legal topics, passwords, unknown contacts, work consequences, and relationship conflict.
- Add better prompt validation before a reply can be sent.
- Redact private message bodies from debug logs by default.

Acceptance:

- A user can understand why a message was auto-approved, blocked, or sent to review.
- Sensitive messages never bypass review by accident.
- Logs are useful without exposing unnecessary private content.

### Phase 3: Improve personality and memory

Goal: make replies feel more personal while keeping control.

- Expand contact profiles: language, tone, persona, relationship, delay profile, allowed topics, blocked topics.
- Add better memory rules for contact notes, category notes, pinned notes, and expiry.
- Add feedback actions: good reply, bad reply, too formal, too cold, too long, wrong language.
- Add a prompt context preview in the Review form so the operator sees what the agent used.
- Support German and Syrian Arabic more explicitly in contact rules and tests.

Acceptance:

- Different contacts receive noticeably different draft styles.
- Notes and relationship context affect drafts in a visible, explainable way.
- The user can improve the agent without editing code.

### Phase 4: Voice message pipeline

Goal: prepare voice understanding and voice replies without jumping straight into unsafe auto-send.

- Add inbound voice-message detection state.
- Add transcription pipeline for received voice notes.
- Store transcript as message content with media metadata.
- Route voice-message replies through `NEEDS_REVIEW` by default.
- Prototype TTS generation with a provider such as ElevenLabs.
- Convert generated audio to WhatsApp-compatible `.ogg` Opus.
- Test Android share/send flow for audio messages.

Acceptance:

- Voice messages can be transcribed and shown in Review.
- Voice replies can be generated as files, but require manual review before sending.
- Voice cloning is treated as opt-in and sensitive.

### Phase 5: Event and scheduler intelligence

Goal: make planned communication reliable and inspectable.

- Improve event ticket lifecycle states and aggregate status.
- Add recipient preview before ticket approval.
- Add per-recipient failure reason and retry.
- Add reminders and recurring event support.
- Support category broadcasts with review rules per recipient.
- Add "prepared but not sent" warnings.

Acceptance:

- It is always clear whether an event message is only drafted, prepared, approved, sending, sent, or failed.
- Category broadcasts cannot silently send to unsafe or unknown recipients.

### Phase 6: Channel abstraction and professional path

Goal: prepare the codebase for channels beyond Android automation.

- Introduce backend channel abstractions for inbound messages, outbound drafts, send attempts, and delivery status.
- Add WhatsApp Cloud API research implementation behind a separate channel.
- Add webhook verification and status webhook handling.
- Add tenant/workspace concepts only after the single-user flow is stable.
- Prepare Telegram and email as later channels.

Acceptance:

- The backend can model messages and send attempts independently of Android.
- Android becomes one channel, not the whole architecture.

### Phase 7: Product hardening

Goal: make the project safer to publish, share, or deploy.

- Add a real license.
- Remove or ignore local databases and secrets from Git.
- Add structured logging and redaction.
- Add backend test coverage for policy, contact resolution, notes, event tickets, and status transitions.
- Add Android tests for ViewModels, dedupe, retry, and queue decisions.
- Add deployment notes for private backend hosting.
- Add privacy and data-retention documentation.

Acceptance:

- A new developer can run the app and backend from the README.
- The project can be reviewed without exposing private data.
- Core safety behavior is covered by automated tests.

## 6. Feature Backlog

### Personal agent

- Global pause.
- Per-contact auto mode.
- Review-first default for new contacts.
- Simulated reply mode.
- Style profiles per relationship.
- Delay model by contact and message priority.
- Memory with source, expiry, priority, and sensitivity.
- Better Arabic/RTL handling.
- Voice note transcription.
- TTS voice replies.

### Professional product path

- WhatsApp Cloud API channel.
- Webhook inbox.
- Delivery/read status.
- Message templates.
- Team inbox.
- Roles and permissions.
- Audit logs.
- Multi-tenant backend.
- Billing and plans.
- Data export and deletion.

## 7. Safety Gates

The agent should always review or block messages involving:

- health or emergency situations;
- money, banking, debt, payments, or contracts;
- legal issues, police, government, or immigration;
- work consequences such as firing, schedules, salary, or HR topics;
- relationship conflict;
- passwords, verification codes, documents, addresses, or private identifiers;
- unknown contacts;
- groups;
- uncertain recipient identity;
- voice cloning or identity-sensitive output.

## 8. North Star

The north star is a personal communication agent that feels useful, calm, and trustworthy. It should reduce repetitive messaging work, protect the user's time, and still respect the reality that private communication is sensitive.

The short-term product is an Android companion with strong review controls. The long-term product is a channel-based personal agent that can support WhatsApp, Telegram, email, text, and voice with clear safety boundaries.
