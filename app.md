# Project Log (WA Agent v2.1)

## [2024-05-21] Version 2.1 Feature Expansion & Stability

### Step 1: Global UI/UX Modernization
*   **Action**: Updated all screens to Material 3 styling.
*   **Components**: Added `EmptyState`, refined `AgentCard`, `StatCard`, and `AgentTextField` with focus animations.
*   **Layout**: Increased spacing and added fade-in transitions for activity logs.
*   **Result**: Consistent, modern Look & Feel across Dashboard, Contacts, Events, and Queue.

### Step 2: Contact Management & Identification
*   **Action**: Added manual contact creation via `AddContactDialog`.
*   **Identification**: Internal logic now prefers `phoneNumber` where available, using local `contactCacheDao` for mapping.
*   **UI**: Category badges updated to match backend enums (CORE_FAMILY, etc.).

### Step 3: Category-Based Event Broadcasting
*   **Action**: Enhanced `AddEventDialog` with a "Mode Switcher" (Single vs. Category).
*   **Logic**: Implemented `broadcastEvent` in `EventsViewModel` to create backend events for all active contacts in a category.
*   **DAO**: Added `getSettingsByCategory` to `ContactSettingsDao`.

### Step 4: Time-Limited Notes
*   **Action**: Extended Note model in Python (Backend) and Kotlin (Android).
*   **UI**: Integrated Material 3 `DatePicker` in the Notes creation flow.
*   **Logic**: Added `expiresAtMillis` to `UiNote` and local `NoteEntity`.
*   **Filtering**: Backend now only includes non-expired notes in the AI prompt context.

### Step 5: Queue & Sync Stability
*   **Action**: Fixed "Queue Empty" visual issues.
*   **Features**: Added "Retry All" functionality and pull-to-refresh.
*   **Worker**: Refined `SyncWorker` error handling to correctly use `REPLY_FAILED` and limited retries to 3 attempts.

### Step 6: Backend Synchronization & Documentation
*   **Action**: Created `BACKEND_INTEGRATION_GUIDE.md` to document the API contract.
*   **Mapping**: Re-synced `ApiModels.kt` with Pydantic schemas (renamed `topic` -> `category`).

### Step 7: Service Lifecycle & Error Resolution
*   **WhatsAppListener**: Fixed `Service not registered` crash by implementing `onListenerConnected`/`Disconnected` and cancelling coroutine scopes.
*   **AgentService**: Resolved memory leak in health-check loop.
*   **Permissions**: Replaced fragile settings-based check with robust `AccessibilityManager` API in `MainViewModel`.

### Step 8: Bugfixes & UI/UX Stabilization
*   **Queue Consistency**: Fixed Dashboard vs Queue inconsistency by syncing message status back to backend (`PATCH /messages/{msg_id}`).
*   **Contact Management**: Implemented "Edit Contact" function. Existing and auto-created contacts can now be fully modified.
*   **Form Enhancements**: Integrated Material 3 `DatePicker` and `TimePicker` for Events and Notes.
*   **Responsive Chat**: Redesigned `ChatScreenNew` to scale across mobile and tablet devices.
*   **System Navigation**: Implemented `BackHandler` for consistent behavior of the Android system back button.
*   **Reliability**: Added 429 rate-limit handling and retries for category-based event broadcasts.
*   **Backend Migration**: Manually migrated `whatsapp_agent.db` to include missing columns (`is_active`, `created_at`, `expiry_date`, `status`) to fix SQLAlchemy errors.

### Step 9: Multi-Version WhatsApp Support
*   **Contact Indexing**: Updated `ContactIndexerWorker` to support both standard WhatsApp and WhatsApp Business mimetypes (`vnd.com.whatsapp.profile` & `vnd.com.whatsapp.w4b.profile`).
*   **Logic Fix**: Ensures contacts are correctly fetched and indexed even if the user only has WhatsApp Business installed.

### Step 10: Service Stability & Lifecycle Fixes
*   **NotificationListener Stability**: Resolved `Service not registered` (unbind failure) and Binder transaction conflicts (`Bad key 0`, `WTF`) by implementing a robust rebind mechanism in `AgentService`.
*   **Static Tracking**: Added `isRunning` flag and a static `instance` reference to `WhatsAppListener` to monitor connection state in real-time.
*   **Smart Auto-Recovery**: `AgentService` now uses a 60-second cooldown for `requestRebind()` calls and verifies the actual Binder validity via `activeNotifications` before attempting a rebind. This prevents system-level race conditions and redundant binding requests.

### Step 11: Live Log Synchronization Fix
*   **Unified Flow**: Fixed the issue where the Live Log screen was not updating in real-time.
*   **Startup Hydration**: `AgentLogger` now restores the last 50 log entries from the local log file upon initialization.
*   **Direct Pipe**: `MainActivity` now explicitly pipes incoming log broadcasts into `LogViewModel`, ensuring the UI reflects activities immediately.
*   **Auto-Scroll Support**: Adjusted the list update logic in `LogViewModel` to properly support Compose `LazyColumn` auto-scroll behavior.
