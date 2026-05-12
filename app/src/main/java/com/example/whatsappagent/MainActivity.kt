package com.example.whatsappagent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import android.app.Application
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.whatsappagent.data.remote.AgentApiService
import com.example.whatsappagent.data.remote.AgentRepository
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import com.example.whatsappagent.ui.viewmodel.*
import com.example.whatsappagent.data.AppDatabase
import com.example.whatsappagent.data.MessageEntity
import com.example.whatsappagent.ui.components.*
import com.example.whatsappagent.ui.model.*
import com.example.whatsappagent.ui.screens.*
import com.example.whatsappagent.ui.theme.*

// ─────────────────────────────────────────────────────────────────────────────
// NAVIGATION
// ─────────────────────────────────────────────────────────────────────────────
enum class Screen(val label: String, val icon: String) {
    DASHBOARD("Dashboard", "⬡"),
    CONTACTS ("Kontakte",  "◈"),
    EVENTS   ("Events",    "🎉"),
    LOG      ("Live Log",  "▣"),
    QUEUE    ("Queue",     "◫"),
    CHAT     ("Chat",      "◻"),
}

// ─────────────────────────────────────────────────────────────────────────────
// MAIN ACTIVITY
// ─────────────────────────────────────────────────────────────────────────────
class MainActivity : ComponentActivity() {

    private lateinit var mainViewModel: MainViewModel

    private lateinit var logViewModel: LogViewModel

    // ── Permission Launcher ──────────────────────────────────────────────────
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        // No explicit action needed, ViewModels will re-check in onResume or via refresh
    }

    // ── State lifted to Activity (survives recomposition) ────────────────────
    private val _backendConnected = mutableStateOf(false)
    private val _logEntries       = mutableStateListOf<AgentLogger.LogEntry>()

    // Broadcast receivers
    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            _backendConnected.value = intent?.getBooleanExtra(AgentService.EXTRA_CONNECTED, false) ?: false
        }
    }

    private val logReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val raw = intent?.getStringExtra(AgentLogger.EXTRA_LOG_ENTRY) ?: return
            val parts = raw.split("|", limit = 3)
            if (parts.size == 3) {
                val type = try { AgentLogger.LogType.valueOf(parts[1]) } catch (_: Exception) { AgentLogger.LogType.INFO }
                val entry = AgentLogger.LogEntry(parts[0], type, parts[2])
                _logEntries.add(entry)
                if (_logEntries.size > 100) _logEntries.removeAt(0)
                
                // Also update LogViewModel for real-time Live Log screen
                logViewModel.addLogEntry(entry)
            }
        }
    }

    // ── Crash handler ─────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            applicationContext.openFileOutput("crash_log.txt", Context.MODE_APPEND).use {
                it.write(throwable.stackTraceToString().toByteArray())
            }
        }
        super.onCreate(savedInstanceState)

        val database = AppDatabase.getDatabase(this)
        val apiService = createApiService()
        val repository = AgentRepository(apiService, database)
        val factory = AppViewModelFactory(database, repository, application)
        mainViewModel = androidx.lifecycle.ViewModelProvider(this, factory)[MainViewModel::class.java]
        logViewModel  = androidx.lifecycle.ViewModelProvider(this, factory)[LogViewModel::class.java]

        AgentLogger.init(applicationContext)
        ContextCompat.startForegroundService(this, Intent(this, AgentService::class.java))
        _logEntries.addAll(AgentLogger.getLogs())

        setContent {
            AgentApp(
                backendConnected = _backendConnected.value,
                logEntries       = _logEntries,
                onRequestContacts = { 
                    requestPermissionLauncher.launch(android.Manifest.permission.READ_CONTACTS) 
                }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        mainViewModel.checkPermissions()
        
        // Handle Keyguard if launched via fallback
        DeviceControl.dismissKeyguard(this)

        LocalBroadcastManager.getInstance(this).apply {
            registerReceiver(statusReceiver, IntentFilter(AgentService.ACTION_STATUS_UPDATE))
            registerReceiver(logReceiver,    IntentFilter(AgentLogger.ACTION_NEW_LOG))
        }
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(this).apply {
            unregisterReceiver(statusReceiver)
            unregisterReceiver(logReceiver)
        }
    }
}

@Composable
fun AgentApp(
    backendConnected: Boolean,
    logEntries: SnapshotStateList<AgentLogger.LogEntry>,
    onRequestContacts: () -> Unit,
) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val database = remember { AppDatabase.getDatabase(context) }
    
    val apiService = remember { createApiService() }
    val repository = remember { AgentRepository(apiService, database) }
    val factory = remember { AppViewModelFactory(database, repository, application) }

    val mainViewModel: MainViewModel = viewModel(factory = factory)
    val dashboardViewModel: DashboardViewModel = viewModel(factory = factory)
    val contactsViewModel: ContactsViewModel = viewModel(factory = factory)
    val eventsViewModel: EventsViewModel = viewModel(factory = factory)
    val queueViewModel: QueueViewModel = viewModel(factory = factory)
    val chatViewModel: ChatViewModel = viewModel(factory = factory)
    val logViewModel: LogViewModel = viewModel(factory = factory)

    val isNotificationEnabled by mainViewModel.isNotificationEnabled.collectAsState()
    val isAccessibilityEnabled by mainViewModel.isAccessibilityEnabled.collectAsState()
    val isContactsEnabled by mainViewModel.isContactsEnabled.collectAsState()
    val currentThemeState by mainViewModel.currentTheme.collectAsState()

    var currentTheme by remember { mutableStateOf(AgentTheme.DARK) }
    
    // Sync theme state with local currentTheme variable
    LaunchedEffect(currentThemeState) {
        currentTheme = currentThemeState
    }

    val onOpenNotifications = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
    val onOpenAccessibility = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }

    // ── Global state ──────────────────────────────────────────────────────────
    var currentScreen by remember { mutableStateOf(Screen.DASHBOARD) }

    val contacts by contactsViewModel.uiContacts.collectAsState()
    val uiNotes by contactsViewModel.uiNotes.collectAsState()
    val pendingMessages by queueViewModel.pendingMessages.collectAsState()

    val selectedContact by contactsViewModel.selectedContact.collectAsState()
    val selectedSender by chatViewModel.selectedSender.collectAsState()

    // ── Back Navigation Handling ──────────────────────────────────────────────
    BackHandler(enabled = currentScreen != Screen.DASHBOARD || selectedContact != null || selectedSender != null) {
        when {
            currentScreen == Screen.CONTACTS && selectedContact != null -> {
                contactsViewModel.clearSelectedContact()
            }
            currentScreen == Screen.CHAT && selectedSender != null -> {
                chatViewModel.clearSelectedSender()
            }
            else -> {
                currentScreen = Screen.DASHBOARD
            }
        }
    }

    val C = agentColors(currentTheme)
    
    val categoryCounts = remember(contacts) {
        contacts.groupBy { it.category.name }.mapValues { it.value.size } 
    }
    
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = C.surface,
                drawerContentColor = C.textPrimary,
                modifier = Modifier.width(260.dp)
            ) {
                Sidebar(
                    currentScreen = currentScreen,
                    backendConnected = backendConnected,
                    isNotificationEnabled = isNotificationEnabled,
                    isAccessibilityEnabled = isAccessibilityEnabled,
                    isContactsEnabled = isContactsEnabled,
                    onNavigate = { 
                        currentScreen = it
                        scope.launch { drawerState.close() }
                    },
                    onOpenNotifs = onOpenNotifications,
                    onOpenA11y = onOpenAccessibility,
                    onOpenContacts = onRequestContacts,
                    C = C,
                    showTitle = true
                )
            }
        }
    ) {
        // ── Root layout ───────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(C.bg)
                .statusBarsPadding(),
        ) {
            // ── Main content area ─────────────────────────────────────────────────
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {

                // Topbar
                Topbar(
                    screen          = currentScreen,
                    activeContacts  = contacts.count { it.active },
                    pendingQueue    = pendingMessages.size,
                    currentTheme    = currentTheme,
                    onThemeChange   = { mainViewModel.setTheme(it) },
                    onOpenNotifs    = onOpenNotifications,
                    onOpenA11y      = onOpenAccessibility,
                    onMenuClick     = { scope.launch { drawerState.open() } },
                    C               = C,
                )

                // Screen content with fade transition
                AnimatedContent(
                    targetState = currentScreen,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    label = "screen_transition",
                ) { screen ->
                    when (screen) {
                        Screen.DASHBOARD -> DashboardScreen(
                            stats = dashboardViewModel.stats.collectAsState().value,
                            pingLatency = mainViewModel.pingLatency.collectAsState().value,
                            backendConnected = backendConnected,
                            logEntries = logEntries,
                            onRefresh = { dashboardViewModel.refreshStats() },
                            C = C,
                        )
                        Screen.CONTACTS -> {
                            ContactsScreen(
                                contacts        = contacts,
                                notes           = uiNotes,
                                selectedContact = contactsViewModel.selectedContact.collectAsState().value,
                                isLoading       = contactsViewModel.isLoading.collectAsState().value,
                                error           = contactsViewModel.error.collectAsState().value,
                                successMessage  = contactsViewModel.successMessage.collectAsState().value,
                                onToggleContact = { phone, active -> contactsViewModel.toggleContactActive(phone, active) },
                                onAddContact    = { contactsViewModel.addContact(it) },
                                onDeleteContact = { contactsViewModel.deleteContact(it) },
                                onSelectContact = { contactsViewModel.selectContact(it) },
                                onClearSelection = { contactsViewModel.clearSelectedContact() },
                                onRefresh       = { contactsViewModel.refreshContacts() },
                                onClearError    = { contactsViewModel.clearError() },
                                onClearSuccess  = { contactsViewModel.clearSuccessMessage() },
                                onAddNote       = { contactName, text, pinned, expiry -> 
                                    contactsViewModel.addNote(contactName, text, pinned, expiry)
                                },
                                onDeleteNote    = { contactName, id -> contactsViewModel.deleteNote(contactName, id) },
                                onTogglePin     = { contactName, id, current -> contactsViewModel.togglePin(contactName, id, current) },
                                onEditContact   = { contactsViewModel.updateContact(it) },
                                showAddDialog   = contactsViewModel.showAddContactDialog.collectAsState().value,
                                onShowAddDialog = { contactsViewModel.showAddContactDialog() },
                                onHideAddDialog = { contactsViewModel.hideAddContactDialog() },
                                agentColors     = C,
                            )
                        }
                        Screen.EVENTS -> {
                            EventsScreen(
                                events = eventsViewModel.events.collectAsState().value,
                                contacts = contacts,
                                isLoading = eventsViewModel.isLoading.collectAsState().value,
                                successMessage = eventsViewModel.successMessage.collectAsState().value,
                                onAddEvent = { contact, title, date, desc ->
                                    eventsViewModel.addEvent(contact, title, date, desc)
                                },
                                onBroadcastEvent = { category, title, date, desc ->
                                    eventsViewModel.broadcastEvent(category, title, date, desc)
                                },
                                onTriggerNow = { eventsViewModel.triggerNow(it) },
                                onDeleteEvent = { eventsViewModel.deleteEvent(it) },
                                onRefresh = { eventsViewModel.loadAll() },
                                onClearSuccess = { eventsViewModel.clearSuccessMessage() },
                                C = C,
                            )
                        }
                        Screen.LOG -> {
                            LiveLogScreen(
                                logEntries = logViewModel.logEntries.collectAsState().value,
                                isAutoScroll = logViewModel.isAutoScroll.collectAsState().value,
                                filterLevel = logViewModel.filterLevel.collectAsState().value,
                                onToggleAutoScroll = { logViewModel.toggleAutoScroll() },
                                onSetFilterLevel = { logViewModel.setFilterLevel(it) },
                                onClearLogs = { logViewModel.clearLogs() },
                                C          = C,
                            )
                        }
                        Screen.QUEUE -> {
                            QueueScreenNew(
                                queue = queueViewModel.queue.collectAsState().value,
                                currentFilter = queueViewModel.statusFilter.collectAsState().value,
                                isLoading = queueViewModel.isLoading.collectAsState().value,
                                onFilterChange = { queueViewModel.setFilter(it) },
                                onRetryMessage = { queueViewModel.retryMessage(it) },
                                onRetryAll = { queueViewModel.retryAll() },
                                onTriggerSync = { queueViewModel.triggerSync() },
                                onTriggerIndex = { queueViewModel.triggerContactIndexing() },
                                C = C
                            )
                        }
                        Screen.CHAT  -> {
                            ChatScreenNew(
                                messages = chatViewModel.messages.collectAsState().value,
                                senders = chatViewModel.senders.collectAsState().value,
                                selectedSender = chatViewModel.selectedSender.collectAsState().value,
                                onSelectSender = { chatViewModel.selectSender(it) },
                                onClearSelection = { chatViewModel.clearSelectedSender() },
                                C = C
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Create API service for backend communication
 */
private fun createApiService(): AgentApiService {
    val okHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("Authorization", "Bearer ${BuildConfig.APP_API_TOKEN}")
                .addHeader("ngrok-skip-browser-warning", "1")
                .build()
            chain.proceed(request)
        }
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.BACKEND_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    return retrofit.create(AgentApiService::class.java)
}

/**
 * ViewModel Factory for dependency injection
 */
class AppViewModelFactory(
    private val database: AppDatabase,
    private val repository: AgentRepository,
    private val application: Application
) : androidx.lifecycle.ViewModelProvider.Factory {
    
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(MainViewModel::class.java) -> MainViewModel(database, application) as T
            modelClass.isAssignableFrom(DashboardViewModel::class.java) -> DashboardViewModel(database, repository) as T
            modelClass.isAssignableFrom(ContactsViewModel::class.java) -> ContactsViewModel(database, repository) as T
            modelClass.isAssignableFrom(EventsViewModel::class.java) -> EventsViewModel(database, repository, application) as T
            modelClass.isAssignableFrom(QueueViewModel::class.java) -> QueueViewModel(database, repository, application) as T
            modelClass.isAssignableFrom(ChatViewModel::class.java) -> ChatViewModel(database) as T
            modelClass.isAssignableFrom(LogViewModel::class.java) -> LogViewModel() as T
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SIDEBAR
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun Sidebar(
    currentScreen: Screen,
    backendConnected: Boolean,
    isNotificationEnabled: Boolean,
    isAccessibilityEnabled: Boolean,
    isContactsEnabled: Boolean,
    onNavigate: (Screen) -> Unit,
    onOpenNotifs: () -> Unit,
    onOpenA11y: () -> Unit,
    onOpenContacts: () -> Unit,
    C: AgentColors,
    showTitle: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(C.surface),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            if (showTitle) {
                // Logo + status
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(top = 22.dp, bottom = 18.dp),
                ) {
                    Text("WA Agent", color = C.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp)
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (backendConnected) C.green else C.red)
                        )
                        Text(
                            if (backendConnected) "Backend online" else "Offline",
                            color = if (backendConnected) C.green else C.red,
                            fontSize = 11.sp, fontWeight = FontWeight.Medium,
                        )
                    }
                }
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(C.border))
                Spacer(Modifier.height(10.dp))
            }

            // Nav items
            Column(modifier = Modifier.padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Screen.entries.forEach { screen ->
                    val isActive = currentScreen == screen
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isActive) C.accentLow else Color.Transparent)
                            .clickable { onNavigate(screen) }
                            .padding(horizontal = 10.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(screen.icon, fontSize = 15.sp, color = if (isActive) C.accent else C.textSecondary)
                        Text(
                            screen.label,
                            color = if (isActive) C.accent else C.textSecondary,
                            fontSize = 13.sp,
                            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.weight(1f),
                        )
                        if (screen == Screen.EVENTS) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(C.purple.copy(alpha = 0.15f))
                                    .padding(horizontal = 5.dp, vertical = 1.dp),
                            ) {
                                Text("NEU", color = C.purple, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                            }
                        }
                    }
                }
            }
        }

        // Footer
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Permission Buttons
            PermissionButton(
                label = "Notifications",
                isActive = isNotificationEnabled,
                onClick = onOpenNotifs,
                C = C
            )
            PermissionButton(
                label = "Accessibility",
                isActive = isAccessibilityEnabled,
                onClick = onOpenA11y,
                C = C
            )
            PermissionButton(
                label = "Contacts",
                isActive = isContactsEnabled,
                onClick = onOpenContacts,
                C = C
            )
            
            Spacer(Modifier.height(4.dp))
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(C.border))
            Spacer(Modifier.height(4.dp))
            Text("gemini-2.5-flash-lite", color = C.textMuted, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 10.dp))
            Text("Free Tier · 1.000 RPD", color = C.textMuted, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 10.dp))
        }
    }
}

@Composable
private fun PermissionButton(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    C: AgentColors
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(C.surfaceHigh)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = C.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        AgentBadge(if (isActive) "Aktiv" else "Inaktiv", if (isActive) C.green else C.red)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TOPBAR
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun Topbar(
    screen: Screen,
    activeContacts: Int,
    pendingQueue: Int,
    currentTheme: AgentTheme,
    onThemeChange: (AgentTheme) -> Unit,
    onOpenNotifs: () -> Unit,
    onOpenA11y: () -> Unit,
    onMenuClick: () -> Unit,
    C: AgentColors,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(C.surface)
            .border(width = 1.dp, color = C.border, shape = RoundedCornerShape(0.dp))
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(C.surfaceHigh)
                    .clickable { onMenuClick() }
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("☰", color = C.textPrimary, fontSize = 18.sp)
            }
            Text("WA Agent", color = C.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            
            // Theme Switcher moved here
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(C.surfaceHigh)
                    .border(1.dp, C.border, RoundedCornerShape(20.dp))
                    .padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                listOf(
                    AgentTheme.DARK to "🌑",
                    AgentTheme.LIGHT to "☀️",
                    AgentTheme.AMOLED to "⬛",
                ).forEach { (theme, icon) ->
                    val isActive = currentTheme == theme
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(if (isActive) C.accent else Color.Transparent)
                            .clickable { onThemeChange(theme) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(icon, fontSize = 10.sp)
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AgentBadge("$activeContacts aktiv", C.green)
            AgentBadge("$pendingQueue pending", C.yellow)

            // Quick settings buttons
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .border(1.dp, C.border, RoundedCornerShape(7.dp))
                    .clickable(onClick = onOpenNotifs)
                    .padding(horizontal = 8.dp, vertical = 5.dp),
            ) {
                Text("🔔", fontSize = 13.sp)
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .border(1.dp, C.border, RoundedCornerShape(7.dp))
                    .clickable(onClick = onOpenA11y)
                    .padding(horizontal = 8.dp, vertical = 5.dp),
            ) {
                Text("♿", fontSize = 13.sp)
            }
        }
    }
}
