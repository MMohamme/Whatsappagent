# Manual Device Test Matrix

Stand: 2026-05-13

Run these on a real Android device after backend seed and app install.

## Core Send Flow

| Case | Setup | Expected |
| --- | --- | --- |
| Trusted contact, low risk | Contact `AUTO_LOW_RISK`, notification present | Draft is created, RemoteInput attempt becomes `SENDING`, then `SENT`. |
| Global pause on | Toggle `Auto pausiert` in top bar | Backend draft remains visible, Android does not broadcast auto send, status goes to Review. |
| Notification removed before delay | Swipe notification away before delay expires | SendAttempt becomes `FAILED` or fallback is blocked/reported. |
| Two quick messages same contact | Send two different messages within delay window | Both inbound messages are persisted; delayed send uses matching cached notification/custom ID. |
| Backend offline then online | Stop backend, send message, restart backend | Message remains queued/retried by WorkManager; no false `SENT`. |

## Accessibility Fallback

| Case | Setup | Expected |
| --- | --- | --- |
| Accessibility permission off | RemoteInput unavailable | `FAILED_NO_CHANNEL`; no UI automation. |
| Accessibility permission on, fallback consent off | Toggle Android permission only | `FAILED_NO_CHANNEL`; no UI automation. |
| Fallback consent on, wrong chat open | Open different WhatsApp chat | `FAILED_ACCESSIBILITY`; no send. |
| Fallback consent on, correct chat open | Open exact target chat | Sends only if header matches target and send button exists. |

## Safety / Review

| Case | Setup | Expected |
| --- | --- | --- |
| Work contact | Relation `WORK` | Draft goes to `NEEDS_REVIEW`, never auto-send. |
| Unknown contact | No known phone/contact rule | Draft goes to `NEEDS_REVIEW` or `BLOCKED`. |
| Group notification | Group-like notification title/subText | Ignored or review-only; never auto-send. |
| Sensitive topic | Money, health, legal, password, work consequence | `NEEDS_REVIEW` / `BLOCKED`. |
| Deactivated contact | Contact local inactive | Skipped locally; no backend auto-send. |

## Event Tickets

| Case | Setup | Expected |
| --- | --- | --- |
| Category ticket prepare | Target `EXTENDED_FAMILY` | Recipient rows and drafts are created per contact. |
| Category ticket approve | Mix trusted and work/unknown recipients | Trusted auto-enabled recipients become `SEND_PENDING`; unsafe recipients stay Review with reason. |
| Event recipient due without inbound message | Approved due recipient, no new WhatsApp message | Periodic WorkManager poll fetches due recipients. |
| Partial failure | One recipient fails send | Ticket becomes `PARTIAL_FAILED`; successful recipients remain `SENT`. |

## UI Checks

| Case | Expected |
| --- | --- |
| Top bar pause switch | Always visible and toggles persisted pause state. |
| Sidebar A11y fallback | Disabled until Accessibility permission is enabled; consent is explicit. |
| Queue filters | Shows v3 statuses: `NEEDS_REVIEW`, `SEND_PENDING`, `SENDING`, `SENT`, `FAILED`, `BLOCKED`. |
| Dashboard | Counts do not imply generated drafts are sent. |
