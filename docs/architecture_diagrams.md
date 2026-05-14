# WA Agent Pro Control Center Architecture Diagrams

This document is the durable source of truth for architecture diagrams. Canva is the presentation layer; Mermaid below is the maintainable version.

Sources checked:

- Backend data model: `backend/database.py`
- Backend API and orchestration: `backend/main.py`
- Android Room layer: `app/src/main/java/com/example/whatsappagent/data/*`
- Android API client: `app/src/main/java/com/example/whatsappagent/data/remote/AgentApiService.kt`
- Android services/workers: `WhatsAppListener.kt`, `AutoreplyService.kt`, `AgentService.kt`, `SyncWorker.kt`, `ContactIndexerWorker.kt`, `RetryReplyWorker.kt`
- Pro UI shell/screens: `MainActivity.kt`, `ui/pro/*`

## Backend Database ERM

Source of truth: SQLAlchemy models in `backend/database.py`.

```mermaid
erDiagram
    CONTACT_CATEGORY {
        int id PK
        string name UK
        string label
    }

    CONTACT {
        int id PK
        string display_name
        string phone_number UK
        string relation_type
        string specific_relation
        string preferred_lang
        boolean is_active
        string auto_mode
        datetime created_at
        datetime updated_at
    }

    CONTACT_CATEGORY_LINK {
        int contact_id PK, FK
        int category_id PK, FK
    }

    CONTACT_RULE {
        int id PK
        int contact_id FK, UK
        text style
        text allowed_topics
        text blocked_topics
        int delay_min_ms
        int delay_max_ms
        datetime updated_at
    }

    MESSAGE {
        int id PK
        string custom_id UK
        int contact_id FK
        string channel_message_id
        string package_name
        string notification_key
        string role
        text content
        string status
        string category
        datetime timestamp
    }

    DRAFT {
        int id PK
        int message_id FK
        int event_recipient_id FK
        text reply_text
        string decision
        string risk_level
        text reason
        string category
        int recommended_delay_ms
        datetime created_at
        datetime updated_at
    }

    SEND_ATTEMPT {
        int id PK
        int draft_id FK
        string channel
        string status
        text error
        int attempt_count
        datetime created_at
        datetime updated_at
        datetime sent_at
    }

    NOTE {
        int id PK
        string scope
        int contact_id FK
        string category
        text content
        boolean pinned
        int priority
        datetime valid_from
        datetime expires_at
        datetime created_at
        datetime updated_at
    }

    EVENT_TICKET {
        int id PK
        string title
        string target_type
        int target_contact_id FK
        string target_category
        datetime scheduled_at
        text base_text
        text prompt
        string status
        int stagger_min_seconds
        int stagger_max_seconds
        datetime created_at
        datetime updated_at
    }

    EVENT_RECIPIENT {
        int id PK
        int ticket_id FK
        int contact_id FK
        int draft_id FK
        string status
        text error
        datetime scheduled_at
        datetime created_at
        datetime updated_at
    }

    EVENT {
        int id PK
        int event_recipient_id FK
        int contact_id FK
        datetime scheduled_at
        string status
        text generated_text
        datetime created_at
    }

    AUDIT_LOG {
        int id PK
        string action
        string entity_type
        string entity_id
        text detail
        datetime created_at
    }

    CONTACT ||--o| CONTACT_RULE : owns
    CONTACT ||--o{ MESSAGE : receives
    CONTACT ||--o{ NOTE : scopes
    CONTACT ||--o{ EVENT_RECIPIENT : targets
    CONTACT ||--o{ EVENT : legacy_targets
    CONTACT ||--o{ EVENT_TICKET : target_contact
    CONTACT ||--o{ CONTACT_CATEGORY_LINK : has
    CONTACT_CATEGORY ||--o{ CONTACT_CATEGORY_LINK : groups
    MESSAGE ||--o{ DRAFT : generates
    DRAFT ||--o{ SEND_ATTEMPT : tracks
    EVENT_TICKET ||--o{ EVENT_RECIPIENT : creates
    EVENT_RECIPIENT ||--o| DRAFT : prepared_draft
    EVENT_RECIPIENT ||--o{ EVENT : legacy_event
```

## Android Local Room ERM

Source of truth: Room entities in `app/src/main/java/com/example/whatsappagent/data`.

```mermaid
erDiagram
    MESSAGES_QUEUE {
        string customId PK
        string sender
        string text
        string role
        long timestamp
        boolean isSynced
        string phoneNumber
        string status
        long backendMessageId
        long draftId
        long sendAttemptId
        string notificationKey
        string textHash
        string packageName
    }

    CONTACT_SETTINGS {
        string phoneNumber PK
        string contactName
        boolean isActive
        string relation
        string category
        string style
        int delayMin
        int delayMax
        string preferredLang
        long updatedAt
    }

    CONTACT_CACHE {
        string phoneNumber PK
        string name
        long lastUpdated
    }

    NOTES {
        long id PK
        string contactName
        string type
        string text
        long createdAt
        long expiresAtMillis
        string scope
        string category
        boolean pinned
        int priority
        long validFromMillis
        long backendId
    }

    EVENTS {
        long id PK
        string title
        string emoji
        string message
        string targetCategory
        long expiresAtMillis
        boolean active
        int sentCount
        long createdAt
    }

    EVENT_TICKETS {
        long backendId PK
        string title
        string targetType
        string targetCategory
        long targetContactId
        long scheduledAtMillis
        string baseText
        string prompt
        string status
        int recipientsCount
        long updatedAt
    }

    EVENT_RECIPIENTS {
        long backendId PK
        long ticketId
        long contactId
        string contactName
        long draftId
        string reply
        string status
        long scheduledAtMillis
        string error
        long updatedAt
    }

    SEND_ATTEMPTS {
        long backendId PK
        long draftId
        string channel
        string status
        string error
        int attemptCount
        long sentAtMillis
        long updatedAt
    }

    CONTACT_CACHE ||--o{ CONTACT_SETTINGS : resolves_name_phone
    CONTACT_CACHE ||--o{ MESSAGES_QUEUE : resolves_sender
    CONTACT_SETTINGS ||--o{ MESSAGES_QUEUE : controls_auto_reply
    EVENT_TICKETS ||--o{ EVENT_RECIPIENTS : cached_recipients
    EVENT_RECIPIENTS ||--o| SEND_ATTEMPTS : draft_send_tracking
    MESSAGES_QUEUE ||--o| SEND_ATTEMPTS : inbound_send_tracking
    CONTACT_SETTINGS ||--o{ NOTES : contact_notes
```

## Gesamt-App Context Diagram

Source of truth: Android app shell, services/workers, API client, backend API, WhatsApp send paths.

```mermaid
flowchart LR
    User["User / Operator"]
    UI["Android Pro Control Center UI\nDashboard, Review, Contacts, Events, Logs"]
    NLS["WhatsAppListener\nNotificationListenerService"]
    A11y["AutoReplyService\nAccessibility fallback"]
    AgentService["AgentService\nforeground health/service status"]
    WM["WorkManager\nSyncWorker, RetryReplyWorker, ContactIndexerWorker"]
    Room["Android Room DB\nqueue, contacts, notes, tickets, attempts"]
    Repo["AgentRepository + Retrofit\nAgentApiService"]
    Backend["FastAPI Backend\ncontacts, notes, queue, events, attempts"]
    DB["Backend DB\nSQLAlchemy entities"]
    AI["Gemini / AI client\nreply generation"]
    WA["WhatsApp / WhatsApp Business"]
    AndroidSystem["Android system\npermissions, notifications, contacts"]

    User --> UI
    UI --> Repo
    UI --> Room
    UI --> AgentService
    UI --> AndroidSystem
    AndroidSystem --> NLS
    WA --> AndroidSystem
    NLS --> Room
    NLS --> WM
    WM --> Room
    WM --> Repo
    Repo --> Backend
    Backend --> DB
    Backend --> AI
    Backend --> Repo
    WM --> NLS
    NLS --> WA
    NLS --> A11y
    A11y --> WA
```

## Backend Context Diagram

Source of truth: FastAPI routes in `backend/main.py`.

```mermaid
flowchart TB
    Android["Android client\nUI, Retrofit, WorkManager"]
    Auth["Auth dependency\nBearer APP_API_TOKEN"]

    subgraph FastAPI["FastAPI backend app"]
        direction TB
        Router["FastAPI router\nrequest validation + dependency injection"]
        Health["Health and stats\nGET /health, GET /stats"]

        subgraph Resources["Resource modules"]
            direction LR
            Contacts["Contacts\nGET, POST, PATCH, DELETE /contacts"]
            Notes["Notes\n/notes and legacy contact notes"]
            History["History and legacy events\n/contact history, /events"]
        end

        subgraph Messaging["Messaging modules"]
            direction LR
            Inbound["Inbound messages\nPOST /messages/inbound, POST /generate"]
            Queue["Review queue\nGET /queue"]
            Decisions["Draft decisions\nPATCH /drafts/{id}/decision"]
            Attempts["Send attempts\nPOST /drafts/{id}/send-attempts\nPATCH /send-attempts/{id}"]
        end

        subgraph Eventing["Event ticket modules"]
            direction LR
            Tickets["Event tickets\ncreate, list, detail"]
            Prepare["Prepare ticket\ncreate recipients and drafts"]
            Approve["Approve or cancel ticket"]
            Due["Due recipients\nGET /event-recipients/due"]
        end

        subgraph CoreLogic["Shared decision logic"]
            direction LR
            Policy["Policy helpers\nrisk, auto mode, delay, event send eligibility"]
            Prompt["Prompt builder\nhistory, contact rule, active notes"]
            Audit["Audit log writer"]
        end
    end

    subgraph External["External and persistence"]
        direction TB
        Gemini["Gemini client\nreply generation when configured"]
        DB["SQLAlchemy SessionLocal\ncontacts, messages, drafts, notes, tickets, attempts"]
    end

    Android --> Auth --> Router
    Router --> Health
    Router --> Contacts
    Router --> Notes
    Router --> History
    Router --> Inbound
    Router --> Queue
    Router --> Decisions
    Router --> Attempts
    Router --> Tickets
    Router --> Prepare
    Router --> Approve
    Router --> Due
    Inbound --> Policy
    Inbound --> Prompt --> Gemini
    Queue --> DB
    Decisions --> Policy
    Decisions --> DB
    Attempts --> DB
    Tickets --> DB
    Prepare --> Policy
    Prepare --> DB
    Approve --> DB
    Due --> DB
    Contacts --> DB
    Notes --> DB
    History --> DB
    Health --> DB
    Contacts --> Audit
    Decisions --> Audit
    Tickets --> Audit
    Audit --> DB
```

## End-to-End Message Flow

Source of truth: `WhatsAppListener.kt`, `SyncWorker.kt`, backend `/messages/inbound`, review queue update path.

```mermaid
flowchart TD
    A["WhatsApp notification arrives"]
    B["NotificationParser extracts sender, text, phone, package, notification key"]
    C["WhatsAppListener creates customId and caches notification action by sender/customId/key"]
    D["Room messages_queue insert\nstatus CAPTURED, isSynced false"]
    E["WorkManager triggerSync"]
    F["SyncWorker loads pending local messages"]
    G["POST /messages/inbound\ncustom_id, sender, text, phone, history"]
    H["Backend resolves contact\nloads rules + active notes\nclassifies risk"]
    I["Backend generates reply\nGemini or fallback"]
    J{"Decision"}
    K["AUTO_SEND_ALLOWED\nand auto-send active"]
    L["NEEDS_REVIEW or auto paused"]
    M["BLOCKED / SKIPPED / failed"]
    N["Room assistant draft/reply row\nstores backendMessageId + draftId"]
    O["LocalBroadcast ACTION_SEND_REPLY\nsender, reply, customId, draftId"]
    P["WhatsAppListener RemoteInput send"]
    Q["Accessibility fallback if no RemoteInput or old notification"]
    R["createSendAttempt/updateSendAttempt\nSENDING -> SENT/FAILED"]
    S["Backend queue exposes Draft\nReview UI form can edit reply_text"]

    A --> B --> C --> D --> E --> F --> G --> H --> I --> J
    J --> K --> N --> O --> P --> R
    P --> Q --> R
    J --> L --> N --> S
    J --> M
    S -->|"Approve inbound draft with custom_id"| O
    S -->|"Block"| M
```

## Review Queue State Flow

Source of truth: backend draft decision endpoint, Android `QueueViewModel`, `ProQueue`.

```mermaid
stateDiagram-v2
    direction LR

    state "Backend decision" as BackendDecision {
        [*] --> NEEDS_REVIEW: risky, unknown, or auto paused
        [*] --> AUTO_SEND_ALLOWED: trusted and low risk
        [*] --> BLOCKED: policy or safety block
    }

    state "Human review form" as ReviewForm {
        OPEN: Draft visible in Review Queue
        EDITING: reply_text edited locally
        APPROVING: Approve submitted
        BLOCKING: Block reason submitted

        [*] --> OPEN
        OPEN --> EDITING: operator edits reply
        EDITING --> OPEN: text kept in form
        OPEN --> APPROVING: approve
        OPEN --> BLOCKING: block
    }

    state "Dispatch lifecycle" as Dispatch {
        SEND_PENDING: ready for Android dispatch
        SENDING: send attempt active
        SENT: delivered through WhatsApp path
        FAILED_RETRYABLE: temporary send failure
        FAILED_FINAL: blocked after repeated or hard failure

        SEND_PENDING --> SENDING: create send attempt
        SENDING --> SENT: RemoteInput or Accessibility success
        SENDING --> FAILED_RETRYABLE: channel unavailable or transient error
        FAILED_RETRYABLE --> SEND_PENDING: retry
        FAILED_RETRYABLE --> FAILED_FINAL: retry budget exhausted
    }

    NEEDS_REVIEW --> OPEN: appears in queue
    APPROVING --> AUTO_SEND_ALLOWED: PATCH decision with optional reply_text
    BLOCKING --> BLOCKED: PATCH decision with reason
    AUTO_SEND_ALLOWED --> SEND_PENDING: backend marks message or recipient sendable

    BLOCKED --> [*]
    SENT --> [*]
    FAILED_FINAL --> NEEDS_REVIEW: operator can inspect failure

    note right of SEND_PENDING
        Direct Android dispatch is allowed only
        for inbound drafts that still have custom_id.
        Event ticket drafts are sent by the
        due-recipient polling path.
    end note
```

Review Queue dispatch gate, shown separately to make the production rule explicit:

```mermaid
flowchart LR
    Draft["Draft approved or auto-send allowed"]
    Inbound{"Has inbound custom_id?"}
    EventDraft{"Is event recipient draft?"}
    Direct["QueueViewModel triggers direct dispatch"]
    EventPoll["SyncWorker polls due event recipients"]
    NoDispatch["Stay in backend queue with reason"]
    Sender["WhatsApp sender\nRemoteInput first, Accessibility fallback"]
    Attempt["SendAttempt updated\nSENDING, SENT, FAILED"]

    Draft --> Inbound
    Inbound -->|"yes"| Direct --> Sender --> Attempt
    Inbound -->|"no"| EventDraft
    EventDraft -->|"yes"| EventPoll --> Sender
    EventDraft -->|"no"| NoDispatch
```

## Event Ticket Flow

Source of truth: `EventsViewModel`, `ProEvents`, backend event-ticket routes, `SyncWorker.pollDueEventRecipients`.

```mermaid
flowchart TD
    A["UI Events tab\nNew Event form"]
    B{"Target mode"}
    C["Single contact\n target_contact_id/contact name"]
    D["Category broadcast\n target_category"]
    E["POST /event-tickets\nstatus DRAFT"]
    F["Backend selects recipients"]
    G["Prepare ticket\nPOST /event-tickets/{id}/prepare"]
    H["Create EventRecipient rows\nand Draft rows"]
    I{"Approve ticket"}
    J["Allowed contact\nstatus SEND_PENDING"]
    K["Review-required contact\nstatus NEEDS_REVIEW + reason"]
    L["SyncWorker polls\nGET /event-recipients/due"]
    M["Broadcast ACTION_SEND_REPLY\nis_event, recipient id, draftId"]
    N["SendAttempt lifecycle\nSENDING -> SENT/FAILED"]
    O["Ticket aggregate status\nAPPROVED, SENDING, SENT, PARTIAL_FAILED"]
    P["Review Queue can approve individual recipient draft"]

    A --> B
    B --> C --> E
    B --> D --> E
    E --> F --> G --> H --> I
    I --> J --> L --> M --> N --> O
    I --> K --> P --> J
```

## Contact And Notes Flow

Source of truth: contacts endpoints, ContactIndexerWorker, ContactsViewModel, notes endpoints, prompt builder.

```mermaid
flowchart LR
    AndroidContacts["Android Contacts permission/source"]
    Indexer["ContactIndexerWorker"]
    Cache["Room contact_cache"]
    Settings["Room contact_settings"]
    ContactsUI["Contacts UI\nlist + detail + form"]
    BackendContacts["Backend /contacts"]
    BackendNotes["Backend /notes\n/contact notes legacy"]
    NotesUI["Notes panel\nadd, pin, expiry, delete"]
    Prompt["Backend prompt builder"]
    Inbound["Inbound message processing"]

    AndroidContacts --> Indexer
    Indexer --> Cache
    Indexer --> BackendContacts
    BackendContacts --> ContactsUI
    Settings --> ContactsUI
    ContactsUI -->|"add/edit/delete/toggle active"| BackendContacts
    ContactsUI --> Settings
    ContactsUI --> NotesUI
    NotesUI -->|"create/update/delete/toggle pin"| BackendNotes
    BackendNotes --> NotesUI
    BackendNotes --> Prompt
    Inbound --> Prompt
```

## UI Navigation And Screen Flow

Source of truth: `MainActivity.kt`, `ui/pro/ProComponents.kt`, `ui/pro/ProScreens.kt`.

```mermaid
flowchart TD
    Shell["MainActivity shell\nViewModel wiring + navigation"]
    Top["ProTopBar\nSafety Strip"]
    Bottom["BottomNav\nHome, Review, Contacts, Events, Logs"]
    Dashboard["Dashboard\nhealth, metrics, risks, lifecycle"]
    Queue["Review Queue\nfilters, rows, error/loading/empty"]
    ReviewForm["Review Draft Form\nedit draft, approve, block"]
    Contacts["Contacts\ntrust control list"]
    ContactDetail["Contact Detail\nphone, relation, persona, active, delete"]
    ContactForm["Contact Add/Edit Form"]
    Notes["Notes Panel\npinned, expiry, add/delete/toggle"]
    Events["Events\nlifecycle rows"]
    EventForm["Event Create Form\ncontact/category, schedule, message"]
    Logs["Logs\nfilters, failure signal"]

    Shell --> Top
    Shell --> Bottom
    Bottom --> Dashboard
    Bottom --> Queue
    Bottom --> Contacts
    Bottom --> Events
    Bottom --> Logs
    Queue --> ReviewForm
    Contacts --> ContactDetail
    Contacts --> ContactForm
    ContactDetail --> Notes
    ContactDetail --> ContactForm
    Events --> EventForm
```

## Android Worker And Service Flow

Source of truth: Android services and workers.

```mermaid
flowchart TB
    subgraph AndroidSystem["Android system surfaces"]
        direction TB
        WA["WhatsApp notifications and reply actions"]
        ContactsProvider["Contacts provider"]
        Permissions["Notification, contacts, accessibility permissions"]
    end

    subgraph Foreground["Always-on app services"]
        direction TB
        AgentService["AgentService\nforeground health status"]
        NLS["WhatsAppListener\ncapture notifications\nsend RemoteInput replies"]
        A11y["AutoReplyService\nAccessibility fallback sender"]
    end

    subgraph Scheduled["Scheduled background work"]
        direction TB
        WorkManager["WorkManagerHelper\nschedules one-off and periodic work"]
        Sync["SyncWorker\nsync inbound messages\npoll review queue\npoll due event recipients"]
        Retry["RetryReplyWorker\nretry failed or pending replies"]
        Indexer["ContactIndexerWorker\nindex local contacts\nsync contact cache"]
    end

    subgraph LocalData["Local persistence"]
        direction TB
        Room["Room DB\nmessages_queue, contacts, notes, tickets, attempts"]
    end

    subgraph Network["Backend boundary"]
        direction TB
        API["AgentRepository + Retrofit\nBackend API"]
    end

    Permissions --> AgentService
    Permissions --> NLS
    Permissions --> A11y
    WA --> NLS
    ContactsProvider --> Indexer

    AgentService --> WorkManager
    NLS --> Room
    NLS --> WorkManager
    WorkManager --> Sync
    WorkManager --> Retry
    WorkManager --> Indexer

    Sync --> Room
    Sync --> API
    Retry --> Room
    Retry --> API
    Indexer --> Room
    Indexer --> API

    Sync --> NLS
    Retry --> NLS
    NLS --> WA
    NLS --> A11y
    A11y --> WA

    API --> Room
```

## API Surface Map

Source of truth: `AgentApiService.kt` and route handlers in `backend/main.py`.

```mermaid
flowchart LR
    Android["Android AgentApiService"]
    Health["GET /health"]
    Stats["GET /stats"]
    Queue["GET /queue\nPATCH /drafts/{id}/decision"]
    Inbound["POST /messages/inbound\nPOST /generate"]
    Messages["PATCH /messages/{msg_id}"]
    Contacts["GET/POST/PATCH/DELETE /contacts"]
    ContactNotes["GET/POST/PATCH/DELETE /contacts/{name}/notes"]
    Notes["GET/POST/PATCH/DELETE /notes"]
    Tickets["GET/POST /event-tickets\nGET /event-tickets/{id}\nprepare/approve/cancel"]
    Recipients["GET /event-recipients/due"]
    Attempts["POST /drafts/{id}/send-attempts\nPATCH /send-attempts/{id}"]
    LegacyEvents["GET/PATCH /events"]

    Android --> Health
    Android --> Stats
    Android --> Queue
    Android --> Inbound
    Android --> Messages
    Android --> Contacts
    Android --> ContactNotes
    Android --> Notes
    Android --> Tickets
    Android --> Recipients
    Android --> Attempts
    Android --> LegacyEvents
```

## Canva Presentation Mapping

The current Canva board `DAHJj94e05M` is a responsive single-page design-system board. The available Canva edit operations can replace existing text, but they cannot add the requested new diagram pages to that existing board in this session.

Recommended Canva page structure when page insertion or a presentation workflow is available:

1. Design System Blueprint
2. Backend Database ERM
3. Android Room ERM
4. Gesamt-App Context
5. Backend Context
6. End-to-End Message Flow
7. Review Queue State Flow
8. Event Ticket Flow
9. Contact and Notes Flow
10. UI Navigation and Screen Flow
11. Android Worker and Service Flow
12. API Surface Map

Use this Markdown document as source material for the Canva diagrams. The Canva version should simplify fields and keep only relationships, modules, and lifecycle states needed for presentation.
