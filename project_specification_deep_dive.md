# WA Agent: Deep-Dive Project Specification (v2.1)

This document provides a highly granular, file-by-file and logic-by-logic breakdown of the WA Agent project, reflecting the current state of version 2.1.

---

## 1. Project Directory Structure & File Roles

```text
app/src/main/java/com/example/whatsappagent/
├── data/
│   ├── AppDatabase.kt          (Room DB singleton, migration logic, v7)
│   ├── MessageEntity.kt        (Schema for captured messages & AI replies)
│   ├── MessageStatus.kt        (Enum for granular status tracking)
│   ├── Converters.kt           (Type converters for enums)
│   ├── MessageDao.kt           (Queries for sync queue, stats & chat history)
│   ├── ContactSettingsEntity.kt (Per-contact configuration, PK: phoneNumber)
│   ├── ContactSettingsDao.kt    (CRUD for contact settings by phone/name)
│   ├── ContactCacheEntity.kt   (Phone -> Name mapping, PK: phoneNumber)
│   ├── ContactCacheDao.kt      (Query/Insert for contact cache)
│   ├── NoteEntity.kt           (User notes per contact - UI feature)
│   ├── EventEntity.kt          (Scheduled/Triggered events - UI feature)
│   └── remote/
│       ├── AgentApiService.kt  (Retrofit interface for backend)
│       ├── AgentRepository.kt  (Abstraction layer for DB & API)
│       └── ApiModels.kt        (JSON request/response classes)
├── worker/
│   ├── SyncWorker.kt           (Main sync logic: Room -> Backend -> DB -> Broadcast)
│   ├── RetryReplyWorker.kt     (Retries failed broadcasts/pending replies)
│   ├── ContactIndexerWorker.kt (Scans system contacts for WA & WA Business profiles)
│   └── WorkManagerHelper.kt    (Triggers for sync, indexing, and retry work)
├── ui/
│   ├── theme/
│   │   ├── Theme.kt            (Theme setup)
│   │   ├── Color.kt            (Color palette definitions)
│   │   └── AgentColors.kt      (Custom color wrapper for light/dark/amoled)
│   ├── viewmodel/
│   │   ├── MainViewModel.kt    (Permissions, connectivity status, theme state)
│   │   ├── DashboardViewModel.kt (Home stats aggregation)
│   │   ├── ContactsViewModel.kt (Contact list management, success messaging)
│   │   ├── QueueViewModel.kt    (Sync queue monitoring, retry logic)
│   │   ├── EventsViewModel.kt   (Event management, rate-limited broadcasting)
│   │   ├── LogViewModel.kt      (Real-time log aggregation for Live Log screen)
│   │   └── ChatViewModel.kt     (History viewing, threaded conversation state)
│   ├── screens/
│   │   ├── DashboardScreen.kt  (Stat cards, charts, rolling logs)
│   │   ├── ContactsScreen.kt   (Contact list & detail, responsive view, edit mode)
│   │   ├── QueueScreenNew.kt   (Message list, manual sync/index buttons)
│   │   ├── EventsScreen.kt     (Scheduling with Date/Time Pickers, Preview mode)
│   │   └── ChatScreenNew.kt    (Threaded view, responsive mobile/tablet layout)
│   └── components/             (Shared UI widgets: AgentCard, AgentTextField, etc.)
├── WhatsAppListener.kt         (Notification Listener Service - NLS, capture & reply)
├── AutoreplyService.kt          (Accessibility Service - AS, UI automation fallback)
├── DeviceControl.kt            (Screen/Keyguard management, WakeLock handling)
├── AgentService.kt              (Foreground service: backend health, NLS auto-recovery)
├── AgentLogger.kt               (Local logging utility: File, Broadcast, FIFO buffer)
├── AgentApplication.kt          (Global DI container, centralized Singleton management)
└── MainActivity.kt              (UI Entry point, navigation, BackHandler, Permission orchestration)
```

---

## 2. Granular Logic Breakdown

### A. The "Message Capture" Pipeline
1.  **Intercept**: `WhatsAppListener.onNotificationPosted` triggers on incoming system notifications.
2.  **Filter**:
    - **Package Check**: Only `com.whatsapp` or `com.whatsapp.w4b` (Business).
    - **Summary Check**: Ignores meta-notifications like "5 new messages".
    - **Blacklist Check**: Ignores senders in a configurable list (e.g., "Bank", "Arzt").
    - **Group Check**: Uses regex and `subText` analysis to filter out group chats.
    - **Reaction/Media Check**: Ignores emoji reactions ("reagierte auf...") and non-text (Photo, Sticker).
3. **Deduplicate**: Uses persistent `processed_keys` (SharedPreferences) with a 5-minute TTL to prevent re-processing after service restarts.
4. **Resolve**: Queries `ContactCacheDao` using `phoneNumber` mapping to identify the sender.
5. **Persist**: Inserts `MessageEntity` into Room.
    - `customId`: SHA256 hash of `sender + text + timestamp`.
    - `status`: `CAPTURED`.
    - `isSynced`: `false`.
6.  **Auto-Provision**: If no `ContactSettings` exist, creates a default entry (`isActive=true`, `category=UNKNOWN`, standard delays 5s-15s).
7.  **Trigger**: Calls `WorkManagerHelper.triggerSync()` to initiate background processing.

### B. The "Sync & Generate" Pipeline
1.  **Fetch**: `SyncWorker` queries `MessageDao.getPendingMessages()` for unsynced captures.
2.  **History Assembly**: Fetches the last 12 messages from Room, alternates roles using `buildConversationHistory` to normalize multi-message bursts.
3.  **API Call**: Sends JSON payload to `BASE_URL/generate`.
4.  **Process Response**:
    - Updates original message to `isSynced = true`, `status = REPLY_PENDING`.
    - Inserts the reply as a new `MessageEntity` with `role = "assistant"`, `status = REPLY_SENT`.
5.  **Notify**: Sends a `LocalBroadcast` with `ACTION_SEND_REPLY` to trigger the physical response.
6.  **Event Polling**: `SyncWorker` also polls `GET /events?status=TRIGGERED` to fetch proactive messages scheduled via the dashboard.

### C. The "Reply & Fallback" Pipeline
1.  **Receive**: `WhatsAppListener` catches the broadcast via `replyReceiver`.
2.  **Delay**: Applies per-contact jitter delay (random between `delayMin` and `delayMax`) to simulate human typing.
3.  **Action 1 (RemoteInput)**:
    - Uses cached `NotificationData` (StatusBarNotification + Action).
    - If notification is < 10 mins old, sends reply directly via binder.
    - On success, updates Local Room and calls Backend `PATCH /messages/{id}` to sync status to `REPLY_SENT`.
4.  **Action 2 (Accessibility Fallback)**:
    - Triggered if RemoteInput fails, notification expired, or `DeadObjectException` occurs.
    - `DeviceControl.wakeScreen()` ensures interactivity.
    - Opens chat via `contentIntent` or `wa.me` URI.
    - `AutoReplyService.tryReply()` uses UI tree inspection to find the input field, paste the text, and click send.

---

## 3. Core Architecture & Stability

### NLS Auto-Recovery Mechanism
- `WhatsAppListener` maintains a static `isRunning` flag and `instance` reference.
- `AgentService` runs a health check every 5 seconds.
- If the listener is enabled in Android settings but `isRunning` is false or the binder is invalid (verified via `activeNotifications` access), it triggers `NotificationListenerService.requestRebind()`.
- A 60-second cooldown is enforced to prevent Binder transaction conflicts (`Bad key 0` / `WTF` errors).

### Centralized Dependency Management
- `AgentApplication` acts as the master DI container.
- Singletons for `AppDatabase`, `AgentApiService`, and `AgentRepository` are initialized lazily.
- Ensures all components (UI, Workers, Services) use the same configuration (Base URL, Timeouts, Interceptors).

### Navigation & Back-Stack
- `MainActivity` uses an Enum-based navigation state.
- `BackHandler` is implemented to provide intuitive hierarchical navigation:
    1.  Deselect contact/chat detail view.
    2.  Return to Dashboard from secondary screens.
    3.  Exit app if on Dashboard.

---

## 4. UI Implementation Details

### Responsive UI Architecture
- **Contacts & Chat**: Use `BoxWithConstraints` to detect screen width.
    - **Mobile (< 700dp)**: List-Detail flow (only one visible at a time).
    - **Tablet/Landscape (> 700dp)**: Side-by-side Layout.
- **Lazy List Keying**: All lists use persistent keys (`msgId`, `phoneNumber`) to ensure smooth scrolling and correct update animations.

### Material 3 Forms & Pickers
- **Events/Notes**: Integrated Material 3 `DatePicker` and `TimePicker`.
- **Validation**: Save buttons are context-aware (disabled if required fields are missing).
- **Feedback**: `SnackbarHost` manages success notifications across screens.
- **Topic Selection**: Chip-based `CategorySelector` for faster contact classification.

### Theme Engine
- Managed in `MainViewModel` via `StateFlow<AgentTheme>`.
- `AgentColors` maps theme variants (LIGHT, DARK, AMOLED) to optimized color palettes.
- `AMOLED` theme uses pure black (#000000) backgrounds for battery efficiency.

---

## 5. Data & Communication

### Room Database Schemas (v7)
- **`messages_queue`**: `customId` (PK), `sender`, `text`, `role`, `timestamp`, `isSynced`, `phoneNumber`, `status` (Enum: CAPTURED, SYNCING, REPLY_PENDING, REPLY_SENT, REPLY_FAILED).
- **`contact_cache`**: `phoneNumber` (PK), `name`, `lastUpdated` (Resolves names from NLS to numbers).
- **`contact_settings`**: `phoneNumber` (PK), `contactName`, `isActive`, `category`, `relation`, `style`, `delayMin`, `delayMax`.
- **`notes`**: `id` (PK), `contactName`, `text`, `createdAt`, `expiresAtMillis` (Contextual AI filter).

### API Contract (Retrofit)
- `POST /generate`: Message generation.
- `PATCH /messages/{msg_id}`: Status synchronization.
- `GET /stats`: Dashboard metrics.
- `POST /contacts/{name}/events`: Multi-mode scheduling (Single vs Category).
- `PATCH /contacts/{name}`: Persona and delay updates.

---

## 6. Security & Protections

- **Self-Loop Protection**: Tracks the last 20 sent messages in `sentReplies`. Incoming messages matching these are discarded.
- **Rate-Limit Handling**: `EventsViewModel` implements exponential backoff and 300ms inter-request delays when broadcasting to a category (handling HTTP 429).
- **WakeLock Management**: `SCREEN_BRIGHT_WAKE_LOCK` is acquired for a maximum of 15 seconds during accessibility fallbacks and explicitly released upon completion.
- **Keyguard Dismissal**: Uses `KeyguardManager.requestDismissKeyguard` with callbacks to ensure the UI is unlocked before automation attempts.
- **Log Rotation**: `AgentLogger` rotates files at 1MB to prevent disk exhaustion.
