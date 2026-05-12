# WA Agent: Complete Technical Handbook (Agent-Ready)

This document is a comprehensive technical specification designed for an AI Agent to understand, maintain, and extend the WA Agent project without needing to read the raw source code.

---

## 1. System Overview
The WA Agent is an Android-based automation tool that intercepts WhatsApp/WhatsApp Business messages, processes them via a backend AI, and sends automated replies. It uses a hybrid capture strategy (Notifications + Accessibility) to ensure 100% reliability.

---

## 2. Core Service Components

### A. `WhatsAppListener` (`NotificationListenerService`)
**Purpose**: Primary entry point for background message interception.
- **Interception Logic**:
    - Filters by package: `com.whatsapp` and `com.whatsapp.w4b`.
    - Extracts `android.title` (Sender) and `android.text` (Message Content).
    - **NotificationData Cache**: Immediately extracts `RemoteInput` and `contentIntent` into an in-memory map (`extractedNotifications`) keyed by sender name. This allows replies even if the system dismisses the notification.
- **Filtering & Security**:
    - **Spam Protection**: Uses `processedKeys` (sender:text hash) to ignore duplicates.
    - **Loop Prevention**: Maintains `sentReplies` set; ignores incoming messages that match the last sent reply.
    - **Min Interval**: Enforces `MIN_REPLY_INTERVAL_MS` (60s) per sender.
    - **Group Filter**: Detects and ignores group chats using regex on titles and subtexts.
- **Output**: Generates a deterministic SHA-256 `customId`, saves a `MessageEntity` to Room, and triggers `WorkManagerHelper.triggerSync()`.

### B. `AutoReplyService` (`AccessibilityService`)
**Purpose**: Foreground interaction and ultimate fallback for replying.
- **Scraping Mode**: Continuously monitors `TYPE_WINDOW_CONTENT_CHANGED`. If the WhatsApp chat window is open, it finds the chat name (header) and message texts (`message_text` view IDs) to capture messages that don't trigger notifications.
- **Automation Actions**:
    - `tryReply(targetContact, text)`: If not in the correct chat, calls `openChatByName`.
    - `openChatByName(name)`: Clicks the search button, types the contact name, and clicks the first result.
    - `insertTextAndSend(text)`: Finds the input field (`com.whatsapp:id/entry`), sets text, and clicks the send button.
- **Human Simulation**: `maybeAddTypo(text)` has a 20% chance to introduce a character swap in a random word to simulate a human typist.

### C. `DeviceControl` (Utility)
**Purpose**: Screen and Power management.
- `wakeScreen(context)`: Uses `PowerManager` to turn on the screen when a message arrives.
- `dismissKeyguard(activity)`: Uses `KeyguardManager` to bypass the lock screen (for non-secure locks) or prompt the user.

---

## 3. Data Architecture (Room Database v5)

### Entities
1. **`MessageEntity`**:
    - `customId` (PK): SHA-256 hash.
    - `sender`: Contact name.
    - `text`: Message body.
    - `role`: "user" or "assistant".
    - `phoneNumber`: Resolved via cache.
    - `isSynced`: Boolean (true after backend processing).
    - `status`: CAPTURED -> SYNCED -> DONE/FAILED.
2. **`ContactSettingsEntity`**:
    - `contactName` (PK).
    - `isActive`: Toggle for the agent.
    - `category`: FREUNDE, ARBEIT, etc.
3. **`ContactCacheEntity`**:
    - `name` (PK) -> `phoneNumber`. Populated by `ContactIndexerWorker`.

### DAOs
- `MessageDao`: Queries for pending messages (`isSynced=false`) and retrieves chat history for the AI context (limit 12).
- `ContactCacheDao`: Resolves phone numbers by name for `wa.me` deep linking.

---

## 4. Background Processing (WorkManager)

### `SyncWorker`
- **Payload Structure**:
  ```json
  {
    "custom_id": "hash",
    "sender": "Name",
    "phone_number": "12345",
    "text": "Hello",
    "history": [{"role": "user", "text": "..."}, {"role": "assistant", "text": "..."}]
  }
  ```
- **Sync Logic**: Sends payload to `/generate` endpoint. On success, marks message as synced, inserts the "assistant" reply into the DB, and broadcasts `ACTION_SEND_REPLY` to the `WhatsAppListener`.
- **Retry Policy**: Exponential backoff.

### `ContactIndexerWorker`
- Queries `ContactsContract.Data` for MIME type `vnd.android.cursor.item/vnd.com.whatsapp.profile`.
- Extracts phone numbers from the JID in `DATA1`.

---

## 5. UI Layout (Jetpack Compose)

### Navigation
- **Sidebar**: Toggle permissions (Notification, Accessibility, Contacts) and switch between screens.
- **Status Indicator**: Visual feedback on Backend Connectivity (online/offline).

### Features
- **Dashboard**: High-level stats and a rolling log view.
- **Queue Screen**: View the live status of the message queue. Includes "Sync Now" and "Index Contacts" buttons.
- **Chat Screen**: Threaded view of intercepted conversations.
- **Events Screen**: Create automated triggers (e.g., automated holiday greetings).

---

## 6. Critical Implementation Details for Maintenance

- **Base URL**: `https://humming-opposite-deforest.ngrok-free.dev` (Stored in `SyncWorker` and `MainActivity`).
- **View IDs**: The project depends on `com.whatsapp:id/entry`, `com.whatsapp:id/send`, and `com.whatsapp:id/conversation_contact_name`. These must be updated if WhatsApp changes its internal layout.
- **Permissions Required**:
    - `BIND_NOTIFICATION_LISTENER_SERVICE`
    - `BIND_ACCESSIBILITY_SERVICE`
    - `READ_CONTACTS`
    - `WAKE_LOCK`
    - `DISABLE_KEYGUARD`
- **Conflict Handling**: The `WhatsAppListener` and `AutoReplyService` share a "self-reply" protection set to ensure the agent doesn't talk to itself.

---

## 7. Known Logic Chains
1. **Message In** -> `WhatsAppListener` captures -> Saved to DB.
2. **WorkManager** -> `SyncWorker` sends to Backend -> Gets AI Reply.
3. **Reply Received** -> `WhatsAppListener` attempts `RemoteInput` (Hidden reply).
4. **If RemoteInput Fails** -> `DeviceControl` wakes screen -> `AutoReplyService` opens chat -> Types and Sends.
