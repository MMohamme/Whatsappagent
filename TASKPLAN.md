# WA Agent – Bugfix & Refactor Taskplan
**Erstellt:** 2026-04-30  
**Basis:** `project_specification_deep_dive.md` + Deep-Dive Analyse  
**Ziel:** Alle identifizierten P0/P1/P2-Probleme systematisch beheben

---

## Legende

| Symbol | Bedeutung |
|--------|-----------|
| 🔴 P0 | Kritisch – sofort fixen, blockiert Kernfunktion |
| 🟠 P1 | Wichtig – bald fixen, verursacht Datenfehler |
| 🟡 P2 | Verbesserung – technische Schuld / Stabilität |
| ✅ | Task abgeschlossen |
| 🔲 | Task offen |

---

## PHASE 1 – Datenbankschema-Fixes (Room Migration)
> **Zuerst ausführen** – alle anderen Tasks hängen davon ab.  
> Room-Migration auf **Version 6** notwendig.

---

### TASK-01 🔴 P0 – `contact_settings`: PK von `contactName` auf `phoneNumber` umstellen

**Problem:** PK ist ein String (Anzeigename). Namensänderungen erzeugen Waisen-Datensätze.  
**Betroffene Dateien:**
- `data/ContactSettingsEntity.kt`
- `data/ContactSettingsDao.kt`
- `data/AppDatabase.kt` (Migration)
- `worker/SyncWorker.kt` (Lookup-Logik)
- `WhatsAppListener.kt` (Settings-Abfrage)

**Schritte:**

1. **`ContactSettingsEntity.kt` anpassen:**
```kotlin
// VORHER
@Entity(tableName = "contact_settings")
data class ContactSettingsEntity(
    @PrimaryKey val contactName: String,
    val isActive: Boolean,
    val category: String,
    val delayMin: Int,
    val delayMax: Int
)

// NACHHER
@Entity(tableName = "contact_settings")
data class ContactSettingsEntity(
    @PrimaryKey val phoneNumber: String,   // ← PK ist jetzt Telefonnummer
    val contactName: String,               // ← Name bleibt als normales Feld
    val isActive: Boolean,
    val category: String,
    val delayMin: Int,
    val delayMax: Int
)
```

2. **`ContactSettingsDao.kt` anpassen:**
```kotlin
// Alle Queries von contactName-Lookup auf phoneNumber-Lookup umstellen
@Query("SELECT * FROM contact_settings WHERE phoneNumber = :phone")
suspend fun getSettingsByPhone(phone: String): ContactSettingsEntity?

// Alten Query entfernen oder als @Deprecated markieren:
// @Query("SELECT * FROM contact_settings WHERE contactName = :name")
```

3. **`AppDatabase.kt` – Migration v5 → v6 hinzufügen:**
```kotlin
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Neue Tabelle mit korrektem Schema erstellen
        database.execSQL("""
            CREATE TABLE contact_settings_new (
                phoneNumber TEXT NOT NULL PRIMARY KEY,
                contactName TEXT NOT NULL,
                isActive INTEGER NOT NULL DEFAULT 1,
                category TEXT NOT NULL DEFAULT '',
                delayMin INTEGER NOT NULL DEFAULT 5,
                delayMax INTEGER NOT NULL DEFAULT 30
            )
        """)
        // Alte Daten migrieren – contactName als PK übernehmen, phoneNumber aus Cache joinen
        database.execSQL("""
            INSERT OR IGNORE INTO contact_settings_new (phoneNumber, contactName, isActive, category, delayMin, delayMax)
            SELECT COALESCE(cc.phoneNumber, cs.contactName), cs.contactName, cs.isActive, cs.category, cs.delayMin, cs.delayMax
            FROM contact_settings cs
            LEFT JOIN contact_cache cc ON cs.contactName = cc.name
        """)
        database.execSQL("DROP TABLE contact_settings")
        database.execSQL("ALTER TABLE contact_settings_new RENAME TO contact_settings")
    }
}

// In .addMigrations() eintragen:
Room.databaseBuilder(context, AppDatabase::class.java, "agent_db")
    .addMigrations(MIGRATION_5_6)
    .build()
```

4. **Alle Aufrufstellen (`SyncWorker`, `WhatsAppListener`) auf `getSettingsByPhone(phoneNumber)` umstellen.**

---

### TASK-02 🟠 P1 – `contact_cache`: PK von `name` auf `phoneNumber` umstellen

**Problem:** Zwei Kontakte mit gleichem Namen überschreiben sich gegenseitig.  
**Betroffene Dateien:**
- `data/ContactCacheEntity.kt`
- `data/ContactCacheDao.kt`
- `data/AppDatabase.kt` (Migration, gleiche Version wie TASK-01)

**Schritte:**

1. **`ContactCacheEntity.kt` anpassen:**
```kotlin
// VORHER
@Entity(tableName = "contact_cache")
data class ContactCacheEntity(
    @PrimaryKey val name: String,
    val phoneNumber: String,
    val lastUpdated: Long
)

// NACHHER
@Entity(tableName = "contact_cache")
data class ContactCacheEntity(
    @PrimaryKey val phoneNumber: String,    // ← PK ist eindeutig
    val name: String,
    val lastUpdated: Long
)
```

2. **`ContactCacheDao.kt` anpassen:**
```kotlin
// Lookup nach Name bleibt für die Resolve-Logik erhalten
@Query("SELECT * FROM contact_cache WHERE name = :name ORDER BY lastUpdated DESC LIMIT 1")
suspend fun getByName(name: String): ContactCacheEntity?

// Aber UPSERT nutzt jetzt phoneNumber als PK – kein stilles Überschreiben mehr
@Insert(onConflict = OnConflictStrategy.REPLACE)
suspend fun upsert(entity: ContactCacheEntity)
```

3. **Migration in `AppDatabase.kt` (zusammen mit TASK-01 in MIGRATION_5_6):**
```kotlin
database.execSQL("""
    CREATE TABLE contact_cache_new (
        phoneNumber TEXT NOT NULL PRIMARY KEY,
        name TEXT NOT NULL,
        lastUpdated INTEGER NOT NULL
    )
""")
database.execSQL("""
    INSERT OR IGNORE INTO contact_cache_new (phoneNumber, name, lastUpdated)
    SELECT phoneNumber, name, lastUpdated FROM contact_cache
""")
database.execSQL("DROP TABLE contact_cache")
database.execSQL("ALTER TABLE contact_cache_new RENAME TO contact_cache")
```

---

## PHASE 2 – Reply-Delivery Absicherung
> Verhindert dass AI-Antworten still verloren gehen.

---

### TASK-03 🔴 P0 – Neuen `MessageStatus`-Enum + `REPLY_PENDING`/`REPLY_FAILED`-Status einführen

**Problem:** Aktuell nur `CAPTURED` und `DONE` – kein Status ob Reply wirklich ankam.  
**Betroffene Dateien:**
- `data/MessageEntity.kt`
- `data/MessageDao.kt`
- `worker/SyncWorker.kt`
- `WhatsAppListener.kt`

**Schritte:**

1. **Enum-Klasse erstellen** (`data/MessageStatus.kt`):
```kotlin
enum class MessageStatus {
    CAPTURED,       // Nachricht empfangen, noch nicht gesynct
    SYNCING,        // API-Call läuft
    DONE,           // AI-Reply generiert
    REPLY_PENDING,  // Reply wurde gesendet, Bestätigung steht aus
    REPLY_SENT,     // Reply erfolgreich zugestellt (RemoteInput oder AS)
    REPLY_FAILED,   // Alle Versuche fehlgeschlagen
    SYNC_FAILED     // Backend nicht erreichbar
}
```

2. **`MessageEntity.kt`** – `status: String` durch `status: MessageStatus` ersetzen  
   (oder als String mit `@TypeConverter` für Abwärtskompatibilität behalten)

3. **`SyncWorker.kt`** – nach erfolgreichem Broadcast:
```kotlin
// Status auf REPLY_PENDING setzen, NICHT auf DONE
messageDao.updateStatus(message.customId, MessageStatus.REPLY_PENDING)
```

4. **`WhatsAppListener.kt`** – nach erfolgreichem Reply:
```kotlin
// Auf REPLY_SENT setzen
messageDao.updateStatus(customId, MessageStatus.REPLY_SENT)

// Im catch / Fallback-Fehlerfall:
messageDao.updateStatus(customId, MessageStatus.REPLY_FAILED)
```

5. **`MessageDao.kt`** – neue Query für fehlgeschlagene Replies:
```kotlin
@Query("SELECT * FROM messages_queue WHERE status = 'REPLY_FAILED'")
fun getFailedReplies(): Flow<List<MessageEntity>>
```

---

### TASK-04 🔴 P0 – Broadcast-Loss absichern: `REPLY_PENDING`-Retry-Mechanismus

**Problem:** Wenn `WhatsAppListener` tot ist, geht der `ACTION_SEND_REPLY`-Broadcast ins Leere.  
**Betroffene Dateien:**
- `worker/SyncWorker.kt`
- `worker/WorkManagerHelper.kt`
- `WhatsAppListener.kt`

**Schritte:**

1. **`WorkManagerHelper.kt`** – neuen `RetryReplyWorker` Trigger hinzufügen:
```kotlin
fun scheduleRetryCheck(context: Context) {
    val request = PeriodicWorkRequestBuilder<RetryReplyWorker>(15, TimeUnit.MINUTES)
        .setConstraints(Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build())
        .build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "retry_reply_worker",
        ExistingPeriodicWorkPolicy.KEEP,
        request
    )
}
```

2. **Neues `worker/RetryReplyWorker.kt` erstellen:**
```kotlin
class RetryReplyWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val dao = AppDatabase.getInstance(applicationContext).messageDao()
        val pending = dao.getMessagesByStatus(MessageStatus.REPLY_PENDING)
        
        pending.forEach { msg ->
            // Broadcast erneut senden
            val intent = Intent(ACTION_SEND_REPLY).apply {
                putExtra(EXTRA_SENDER, msg.sender)
                putExtra(EXTRA_REPLY, msg.text)
                putExtra(EXTRA_CUSTOM_ID, msg.customId)
            }
            LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)
        }
        return Result.success()
    }
}
```

3. **`WhatsAppListener.onStartCommand`** – `RetryReplyWorker` beim Service-Start einplanen.

---

## PHASE 3 – Accessibility-Service Stabilität
> WakeLock und Timing-Probleme beheben.

---

### TASK-05 🔴 P0 – WakeLock-Dauer erhöhen + Completion-Callback implementieren

**Problem:** 3-Sekunden-WakeLock zu kurz für den AS-Flow auf langsamen Geräten.  
**Betroffene Dateien:**
- `DeviceControl.kt`
- `AutoreplyService.kt`

**Schritte:**

1. **`DeviceControl.kt`** – WakeLock verlängerbar machen:
```kotlin
object DeviceControl {
    private var wakeLock: PowerManager.WakeLock? = null

    fun wakeScreen(context: Context, durationMs: Long = 15_000L) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock?.release() // alten freigeben
        wakeLock = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "WAAgent::ReplyWakeLock"
        ).apply {
            acquire(durationMs)  // ← 15s statt 3s (Parameter konfigurierbar)
        }
    }

    fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }
}
```

2. **`AutoreplyService.kt`** – nach erfolgreichem `tryReply()` WakeLock freigeben:
```kotlin
suspend fun tryReply(sender: String, reply: String, customId: String): Boolean {
    return try {
        // ... AS-Logik ...
        val success = performReplyViaAccessibility(sender, reply)
        if (success) {
            DeviceControl.releaseWakeLock()  // ← sofort freigeben wenn fertig
            // Status updaten
            database.messageDao().updateStatus(customId, MessageStatus.REPLY_SENT)
        }
        success
    } catch (e: Exception) {
        AgentLogger.e("AutoreplyService", "tryReply failed: ${e.message}")
        database.messageDao().updateStatus(customId, MessageStatus.REPLY_FAILED)
        false
    }
}
```

3. **`AccessibilityNodeInfo`-Null-Check** in jeder AS-Interaktion:
```kotlin
// Pattern für ALLE Node-Zugriffe im AS:
val node = rootInActiveWindow?.findAccessibilityNodeInfosByText(sender)?.firstOrNull()
    ?: run {
        AgentLogger.w("AutoreplyService", "Node not found for '$sender' – aborting")
        return false  // statt NullPointerException
    }
```

---

### TASK-06 🟠 P1 – `DeadObjectException` bei veralteten `PendingIntents` abfangen

**Problem:** NLS-Neustart macht alle gecachten `NotificationData`-Einträge ungültig.  
**Betroffene Dateien:**
- `WhatsAppListener.kt`

**Schritte:**

1. **Notification-Cache mit Timestamp versehen:**
```kotlin
data class NotificationData(
    val pendingIntent: PendingIntent?,
    val remoteInput: RemoteInput?,
    val remoteInputKey: String,
    val contentIntent: PendingIntent?,
    val capturedAt: Long = System.currentTimeMillis()  // ← NEU
)
```

2. **Bei Lookup: Einträge älter als 10 Minuten verwerfen:**
```kotlin
val MAX_NOTIFICATION_AGE_MS = 10 * 60 * 1000L

val data = notificationCache[sender]
if (data == null || System.currentTimeMillis() - data.capturedAt > MAX_NOTIFICATION_AGE_MS) {
    // Direkt Fallback nutzen
    useFallback(sender, reply, customId)
    return
}
```

3. **`DeadObjectException` explizit fangen:**
```kotlin
try {
    sendRemoteInputReply(data, reply)
} catch (e: DeadObjectException) {
    AgentLogger.w("WhatsAppListener", "PendingIntent dead – switching to fallback")
    useFallback(sender, reply, customId)
} catch (e: Exception) {
    AgentLogger.e("WhatsAppListener", "RemoteInput failed: ${e.message}")
    useFallback(sender, reply, customId)
}
```

---

## PHASE 4 – Sync-Pipeline Robustheit

---

### TASK-07 🟠 P1 – Retrofit Timeout explizit konfigurieren

**Problem:** Kein definierter Timeout → WorkManager-Job läuft 10 Minuten blind.  
**Betroffene Dateien:**
- `data/remote/AgentApiService.kt` (oder wo der Retrofit-Client gebaut wird)

**Schritte:**

```kotlin
// Retrofit-Client mit expliziten Timeouts:
val okHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)    // KI-Antwort kann länger dauern
    .writeTimeout(30, TimeUnit.SECONDS)
    .addInterceptor { chain ->
        try {
            chain.proceed(chain.request())
        } catch (e: SocketTimeoutException) {
            AgentLogger.e("AgentApiService", "Request timed out: ${e.message}")
            throw e  // WorkManager fängt das als Result.retry()
        }
    }
    .build()

val retrofit = Retrofit.Builder()
    .baseUrl(BASE_URL)
    .client(okHttpClient)
    .addConverterFactory(GsonConverterFactory.create())
    .build()
```

**`SyncWorker.kt`** – `Result.retry()` bei Timeout statt `Result.failure()`:
```kotlin
} catch (e: SocketTimeoutException) {
    AgentLogger.e("SyncWorker", "Timeout – scheduling retry")
    return Result.retry()  // ← WorkManager exponential backoff
} catch (e: IOException) {
    return Result.retry()
} catch (e: Exception) {
    return Result.failure()
}
```

---

### TASK-08 🟡 P2 – History Assembly: Rollen-bewusster Context-Aufbau

**Problem:** 12 aufeinanderfolgende User-Nachrichten ohne Assistant-Antwort → schlechter AI-Kontext.  
**Betroffene Dateien:**
- `worker/SyncWorker.kt`

**Schritte:**

```kotlin
// VORHER: Alle 12 letzten Nachrichten blind senden
val history = messageDao.getLastMessages(sender, 12)

// NACHHER: Alternierende Rollen sicherstellen
fun buildConversationHistory(messages: List<MessageEntity>): List<ApiMessage> {
    val result = mutableListOf<ApiMessage>()
    var lastRole = ""
    
    for (msg in messages.sortedBy { it.timestamp }) {
        if (msg.role == lastRole) {
            // Gleiche Rolle: Nachricht an vorherige anhängen statt neue Rolle zu wiederholen
            result.lastOrNull()?.let {
                result[result.lastIndex] = it.copy(content = "${it.content}\n${msg.text}")
            }
        } else {
            result.add(ApiMessage(role = msg.role, content = msg.text))
            lastRole = msg.role
        }
    }
    return result
}
```

---

### TASK-09 🟡 P2 – `delayMin`/`delayMax` aus `ContactSettings` nutzen (statt globale 5-30s)

**Problem:** Per-Kontakt-Delays in DB werden nicht verwendet; globaler 5-30s Delay hardcoded.  
**Betroffene Dateien:**
- `WhatsAppListener.kt`

**Schritte:**

```kotlin
// Im replyReceiver, nach Erhalt des Broadcasts:
val sender = intent.getStringExtra(EXTRA_SENDER) ?: return
val phone = contactCacheDao.getByName(sender)?.phoneNumber

// Settings laden (mit Fallback auf Defaults)
val settings = phone?.let { contactSettingsDao.getSettingsByPhone(it) }
val delayMin = settings?.delayMin ?: 5
val delayMax = settings?.delayMax ?: 30

val delayMs = Random.nextLong(
    delayMin * 1000L,
    delayMax * 1000L
)
delay(delayMs)
// → dann Reply senden
```

---

## PHASE 5 – Filter-Logik Robustheit

---

### TASK-10 🟠 P1 – Group-Check robuster gestalten

**Problem:** Name `"Anna (Hamburg)"` wird als Gruppe gefiltert; `subText` nicht immer vorhanden.  
**Betroffene Dateien:**
- `WhatsAppListener.kt`

**Schritte:**

```kotlin
// VORHER: Naive Klammer-Prüfung
val isGroup = title.contains(Regex("\\(\\d+\\)")) || subText != null

// NACHHER: Nur Zahl-in-Klammern am Ende gilt als Gruppe
fun isGroupNotification(title: String?, subText: String?): Boolean {
    if (title == null) return false
    // Nur "(3)", "(12)" etc. am Ende → Gruppe
    // "(Hamburg)", "(Work)" → kein Match → kein false positive
    val endsWithCount = title.trimEnd().matches(Regex(".*\\(\\d+\\)$"))
    val hasSubText = !subText.isNullOrBlank()
    return endsWithCount || hasSubText
}
```

---

### TASK-11 🟡 P2 – `processedKeys`-Set persistent machen (SharedPreferences)

**Problem:** Set lebt nur im RAM – nach Process-Kill doppelte Verarbeitung möglich.  
**Betroffene Dateien:**
- `WhatsAppListener.kt`

**Schritte:**

```kotlin
// SharedPreferences als leichtgewichtiger Persistent-Store für letzten ~50 Keys
private val prefs by lazy {
    applicationContext.getSharedPreferences("dedup_cache", Context.MODE_PRIVATE)
}
private val MAX_DEDUP_ENTRIES = 50
private val DEDUP_TTL_MS = 5 * 60 * 1000L  // 5 Minuten

fun isDuplicate(key: String): Boolean {
    val stored = prefs.getString(key, null) ?: return false
    val timestamp = stored.toLongOrNull() ?: return false
    return System.currentTimeMillis() - timestamp < DEDUP_TTL_MS
}

fun markProcessed(key: String) {
    val allKeys = prefs.all.keys.toMutableSet()
    if (allKeys.size >= MAX_DEDUP_ENTRIES) {
        // Ältesten Eintrag entfernen
        val oldest = allKeys.minByOrNull { prefs.getString(it, "0")?.toLong() ?: 0L }
        oldest?.let { prefs.edit().remove(it).apply() }
    }
    prefs.edit().putString(key, System.currentTimeMillis().toString()).apply()
}
```

---

## PHASE 6 – Logging & Observability

---

### TASK-12 🟡 P2 – File-basiertes Logging implementieren

**Problem:** `AgentLogger` schreibt nur Broadcasts – nach App-Neustart alles weg.  
**Betroffene Dateien:**
- `AgentLogger.kt`

**Schritte:**

```kotlin
object AgentLogger {
    private const val LOG_FILE = "agent_log.txt"
    private const val MAX_LOG_SIZE_BYTES = 2 * 1024 * 1024  // 2 MB max

    fun log(tag: String, message: String, level: String = "INFO") {
        val entry = "${System.currentTimeMillis()}|$level|[$tag] $message"
        
        // 1. Broadcast (bestehend – nicht entfernen)
        broadcastLog(entry)
        
        // 2. Datei-Log (NEU)
        appendToFile(entry)
    }

    private fun appendToFile(entry: String) {
        try {
            val file = File(appContext.filesDir, LOG_FILE)
            // Rotation bei Überschreitung
            if (file.exists() && file.length() > MAX_LOG_SIZE_BYTES) {
                val backup = File(appContext.filesDir, "agent_log_old.txt")
                file.renameTo(backup)
            }
            file.appendText("$entry\n")
        } catch (e: IOException) {
            // Kein rekursiver Log-Aufruf hier
        }
    }

    fun readLogs(maxLines: Int = 200): List<String> {
        return try {
            File(appContext.filesDir, LOG_FILE)
                .readLines()
                .takeLast(maxLines)
        } catch (e: IOException) {
            emptyList()
        }
    }
}
```

---

## PHASE 7 – Berechtigungen & Android-Kompatibilität

---

### TASK-13 🟠 P1 – `SecurityException` bei `requestDismissKeyguard` absichern

**Problem:** Android 13+ wirft `SecurityException` ohne korrekte Permission.  
**Betroffene Dateien:**
- `DeviceControl.kt`
- `AndroidManifest.xml`

**Schritte:**

1. **`AndroidManifest.xml`:**
```xml
<uses-permission android:name="android.permission.DISABLE_KEYGUARD"/>
```

2. **`DeviceControl.kt`** – Exception abfangen:
```kotlin
fun dismissKeyguard(activity: Activity) {
    try {
        val km = activity.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            km.requestDismissKeyguard(activity, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissError() {
                    AgentLogger.w("DeviceControl", "Keyguard dismiss error – continuing without unlock")
                }
                override fun onDismissSucceeded() {
                    AgentLogger.i("DeviceControl", "Keyguard dismissed")
                }
                override fun onDismissCancelled() {
                    AgentLogger.w("DeviceControl", "Keyguard dismiss cancelled by user")
                }
            })
        }
    } catch (e: SecurityException) {
        AgentLogger.e("DeviceControl", "Missing DISABLE_KEYGUARD permission: ${e.message}")
    }
}
```

---

## Zusammenfassung: Ausführungsreihenfolge

```
PHASE 1  →  PHASE 2  →  PHASE 3  →  PHASE 4  →  PHASE 5  →  PHASE 6  →  PHASE 7
DB-Schema    Reply-        AS-          Sync-       Filter-      Logging     Permissions
Fixes        Delivery     Stabilität   Robustheit   Logik
(TASK-01/02) (TASK-03/04) (TASK-05/06) (TASK-07/08) (TASK-09/10) (TASK-11/12) (TASK-13)
```

> ⚠️ **WICHTIG:** TASK-01 und TASK-02 müssen **gemeinsam** in eine einzige Room-Migration  
> (`MIGRATION_5_6`) gebündelt werden, da beide das Schema von Version 5 auf 6 heben.  
> Nie zwei separate Migrationen für dieselbe Version erstellen.

---

## Checkliste (zum Abhaken in Android Studio)

- [ ] TASK-01 – `contact_settings` PK → `phoneNumber`
- [ ] TASK-02 – `contact_cache` PK → `phoneNumber`
- [ ] TASK-03 – `MessageStatus`-Enum + neue Status-Werte
- [ ] TASK-04 – `RetryReplyWorker` implementieren
- [ ] TASK-05 – WakeLock 15s + Null-Checks im AS
- [ ] TASK-06 – `DeadObjectException` + Cache-TTL
- [ ] TASK-07 – Retrofit Timeouts + `Result.retry()`
- [ ] TASK-08 – History-Assembly Rollen-bewusst
- [ ] TASK-09 – `delayMin`/`delayMax` aus DB nutzen
- [ ] TASK-10 – Group-Check Regex fix
- [ ] TASK-11 – `processedKeys` persistent via SharedPreferences
- [ ] TASK-12 – File-Logging in `AgentLogger`
- [ ] TASK-13 – `SecurityException` Keyguard absichern
