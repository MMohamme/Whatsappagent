# Research Artifact v3

Dieses Dokument fasst die Recherche-Entscheidung fuer Pfad A und Pfad B zusammen. Details und Links stehen in `docs/vision.md`.

## Core Finding

Fuer die normale WhatsApp-App und die mobile WhatsApp Business App gibt es keinen stabilen offiziellen API-Weg, um private Nachrichten vollautomatisch zu lesen und zu beantworten.

## Pfad A: Android Personal Companion

Geeignet fuer:

- eigene Nutzung
- Prototyp
- Lernen ueber Kontakte, Persona, Policy, Notes, Events und UI

Technischer Weg:

- NotificationListener fuer eingehende Notifications
- RemoteInput fuer Direct Reply
- WorkManager fuer Sync und faellige Tasks
- Accessibility nur optionaler Fallback mit Consent

Risiken:

- Notifications koennen fehlen, gruppiert oder abgelaufen sein.
- RemoteInput haengt von WhatsApp-Notification-Actions ab.
- Accessibility ist fragil und policy-riskant.
- Gesperrter Bildschirm und Battery Optimization bleiben echte Failure Modes.

## Pfad B: Professionelles Produkt

Geeignet fuer:

- echte Nutzer
- robuste Zustellung
- Audits
- Business-Kommunikation
- skalierbaren Betrieb

Technischer Zielweg:

- WhatsApp Business Platform / Cloud API
- Webhooks fuer eingehende Nachrichten
- Graph API fuer ausgehende Nachrichten
- Status-Webhooks fuer sent/delivered/read/failed
- Templates ausserhalb des 24h-Servicefensters

## Architecture Consequence

Pfad A wird so gebaut, dass spaeter Pfad B nicht bei null beginnt:

- Contacts, Notes, Drafts, Decisions, EventTickets und SendAttempts bleiben channel-neutral.
- Android-spezifisch sind nur Capture/Sender-Adapter.
- Spaeter kann `WHATSAPP_CLOUD_API` als neuer ChannelAdapter ergaenzt werden.
