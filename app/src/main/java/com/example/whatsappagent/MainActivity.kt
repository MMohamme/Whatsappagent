package com.example.whatsappagent

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.whatsappagent.data.AppDatabase
import com.example.whatsappagent.data.remote.AgentApiService
import com.example.whatsappagent.data.remote.AgentRepository
import com.example.whatsappagent.ui.model.ContactCategory
import com.example.whatsappagent.ui.model.UiContact
import com.example.whatsappagent.ui.pro.BottomNav
import com.example.whatsappagent.ui.pro.ProContacts
import com.example.whatsappagent.ui.pro.ProDark
import com.example.whatsappagent.ui.pro.ProDashboard
import com.example.whatsappagent.ui.pro.ProEvents
import com.example.whatsappagent.ui.pro.ProLight
import com.example.whatsappagent.ui.pro.ProLogs
import com.example.whatsappagent.ui.pro.ProQueue
import com.example.whatsappagent.ui.pro.ProTopBar
import com.example.whatsappagent.ui.pro.Screen
import com.example.whatsappagent.ui.theme.AgentTheme
import com.example.whatsappagent.ui.viewmodel.ChatViewModel
import com.example.whatsappagent.ui.viewmodel.ContactsViewModel
import com.example.whatsappagent.ui.viewmodel.DashboardViewModel
import com.example.whatsappagent.ui.viewmodel.EventsViewModel
import com.example.whatsappagent.ui.viewmodel.LogViewModel
import com.example.whatsappagent.ui.viewmodel.MainViewModel
import com.example.whatsappagent.ui.viewmodel.QueueViewModel
import com.example.whatsappagent.worker.WorkManagerHelper
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    private lateinit var mainViewModel: MainViewModel
    private lateinit var logViewModel: LogViewModel

    private val backendConnected = mutableStateOf(false)
    private val logEntries = mutableStateListOf<AgentLogger.LogEntry>()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) WorkManagerHelper.triggerContactIndexing(applicationContext)
        mainViewModel.checkPermissions()
    }

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            backendConnected.value = intent?.getBooleanExtra(AgentService.EXTRA_CONNECTED, false) ?: false
        }
    }

    private val logReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val raw = intent?.getStringExtra(AgentLogger.EXTRA_LOG_ENTRY) ?: return
            val parts = raw.split("|", limit = 3)
            if (parts.size != 3) return
            val type = runCatching { AgentLogger.LogType.valueOf(parts[1]) }
                .getOrDefault(AgentLogger.LogType.INFO)
            val entry = AgentLogger.LogEntry(parts[0], type, parts[2])
            logEntries.add(entry)
            if (logEntries.size > 100) logEntries.removeAt(0)
            logViewModel.addLogEntry(entry)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            applicationContext.openFileOutput("crash_log.txt", Context.MODE_APPEND).use {
                it.write(throwable.stackTraceToString().toByteArray())
            }
        }
        super.onCreate(savedInstanceState)

        val database = AppDatabase.getDatabase(this)
        val repository = AgentRepository(createApiService(), database)
        val factory = AppViewModelFactory(database, repository, application)
        mainViewModel = androidx.lifecycle.ViewModelProvider(this, factory)[MainViewModel::class.java]
        logViewModel = androidx.lifecycle.ViewModelProvider(this, factory)[LogViewModel::class.java]

        AgentLogger.init(applicationContext)
        ContextCompat.startForegroundService(this, Intent(this, AgentService::class.java))
        logEntries.addAll(AgentLogger.getLogs())

        setContent {
            AgentApp(
                backendConnected = backendConnected.value,
                logEntries = logEntries,
                onRequestContacts = {
                    requestPermissionLauncher.launch(android.Manifest.permission.READ_CONTACTS)
                }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        mainViewModel.checkPermissions()
        DeviceControl.dismissKeyguard(this)
        LocalBroadcastManager.getInstance(this).apply {
            registerReceiver(statusReceiver, IntentFilter(AgentService.ACTION_STATUS_UPDATE))
            registerReceiver(logReceiver, IntentFilter(AgentLogger.ACTION_NEW_LOG))
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
    val repository = remember { AgentRepository(createApiService(), database) }
    val factory = remember { AppViewModelFactory(database, repository, application) }

    val mainViewModel: MainViewModel = viewModel(factory = factory)
    val dashboardViewModel: DashboardViewModel = viewModel(factory = factory)
    val contactsViewModel: ContactsViewModel = viewModel(factory = factory)
    val eventsViewModel: EventsViewModel = viewModel(factory = factory)
    val queueViewModel: QueueViewModel = viewModel(factory = factory)
    val chatViewModel: ChatViewModel = viewModel(factory = factory)
    val logViewModel: LogViewModel = viewModel(factory = factory)

    val theme by mainViewModel.currentTheme.collectAsState()
    val isNotificationEnabled by mainViewModel.isNotificationEnabled.collectAsState()
    val isAccessibilityEnabled by mainViewModel.isAccessibilityEnabled.collectAsState()
    val isContactsEnabled by mainViewModel.isContactsEnabled.collectAsState()
    val isAutoSendPaused by mainViewModel.isAutoSendPaused.collectAsState()
    val isAccessibilityFallbackEnabled by mainViewModel.isAccessibilityFallbackEnabled.collectAsState()
    val stats by dashboardViewModel.stats.collectAsState()
    val queue by queueViewModel.queue.collectAsState()
    val currentFilter by queueViewModel.statusFilter.collectAsState()
    val queueLoading by queueViewModel.isLoading.collectAsState()
    val queueError by queueViewModel.errorMessage.collectAsState()
    val pendingMessages by queueViewModel.pendingMessages.collectAsState()
    val contacts by contactsViewModel.uiContacts.collectAsState()
    val selectedContactName by contactsViewModel.selectedContact.collectAsState()
    val notes by contactsViewModel.uiNotes.collectAsState()
    val tickets by eventsViewModel.tickets.collectAsState()
    val eventsLoading by eventsViewModel.isLoading.collectAsState()
    val logs by logViewModel.logEntries.collectAsState()
    val logFilter by logViewModel.filterLevel.collectAsState()

    var screen by remember { mutableStateOf(Screen.DASHBOARD) }
    var showContactForm by remember { mutableStateOf(false) }
    var editingContact by remember { mutableStateOf<UiContact?>(null) }
    var showEventForm by remember { mutableStateOf(false) }
    val colors = if (theme == AgentTheme.LIGHT) ProLight else ProDark

    LaunchedEffect(Unit) {
        dashboardViewModel.refreshStats()
        queueViewModel.loadQueue()
        contactsViewModel.refreshContacts()
        eventsViewModel.loadAll()
    }

    BackHandler(enabled = screen != Screen.DASHBOARD || selectedContactName != null) {
        when {
            screen == Screen.CONTACTS && selectedContactName != null -> contactsViewModel.clearSelectedContact()
            else -> {
                chatViewModel.clearSelectedSender()
                contactsViewModel.clearSelectedContact()
                screen = Screen.DASHBOARD
            }
        }
    }

    MaterialTheme(colorScheme = if (theme == AgentTheme.LIGHT) lightColorScheme() else darkColorScheme()) {
        Surface(color = colors.background) {
            Scaffold(
                containerColor = colors.background,
                topBar = {
                    ProTopBar(
                        screen = screen,
                        backendConnected = backendConnected,
                        autoPaused = isAutoSendPaused,
                        notificationEnabled = isNotificationEnabled,
                        contactsEnabled = isContactsEnabled,
                        accessibilityEnabled = isAccessibilityEnabled,
                        a11yFallback = isAccessibilityFallbackEnabled,
                        onTogglePause = mainViewModel::setAutoSendPaused,
                        onToggleFallback = mainViewModel::setAccessibilityFallbackEnabled,
                        onOpenNotifications = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                        onOpenAccessibility = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                        colors = colors
                    )
                },
                bottomBar = {
                    BottomNav(
                        current = screen,
                        reviewCount = queue.count { it.isReviewable } + pendingMessages.size,
                        onSelect = { screen = it },
                        colors = colors
                    )
                }
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .background(colors.background)
                ) {
                    when (screen) {
                        Screen.DASHBOARD -> ProDashboard(
                            stats = stats,
                            queue = queue,
                            contacts = contacts,
                            tickets = tickets,
                            logs = logEntries,
                            backendConnected = backendConnected,
                            notificationEnabled = isNotificationEnabled,
                            contactsEnabled = isContactsEnabled,
                            accessibilityEnabled = isAccessibilityEnabled,
                            autoPaused = isAutoSendPaused,
                            onRefresh = {
                                dashboardViewModel.refreshStats()
                                queueViewModel.loadQueue()
                                eventsViewModel.loadAll()
                                contactsViewModel.refreshContacts()
                            },
                            colors = colors
                        )

                        Screen.QUEUE -> ProQueue(
                            queue = queue,
                            currentFilter = currentFilter,
                            isLoading = queueLoading,
                            errorMessage = queueError,
                            onFilterChange = queueViewModel::setFilter,
                            onApprove = queueViewModel::approveDraft,
                            onBlock = queueViewModel::blockDraft,
                            onSync = queueViewModel::triggerSync,
                            colors = colors
                        )

                        Screen.CONTACTS -> ProContacts(
                            contacts = contacts,
                            notes = notes,
                            selectedContactName = selectedContactName,
                            isLoading = contactsViewModel.isLoading.collectAsState().value,
                            onToggleActive = contactsViewModel::toggleContactActive,
                            onSelectContact = contactsViewModel::selectContact,
                            onBack = contactsViewModel::clearSelectedContact,
                            onAddContact = { showContactForm = true },
                            onEditContact = {
                                editingContact = it
                                showContactForm = true
                            },
                            onDeleteContact = {
                                contactsViewModel.deleteContact(it)
                                contactsViewModel.clearSelectedContact()
                            },
                            onSaveContact = {
                                if (editingContact == null) contactsViewModel.addContact(it) else contactsViewModel.updateContact(it)
                                editingContact = null
                                showContactForm = false
                            },
                            onDismissContactForm = {
                                editingContact = null
                                showContactForm = false
                            },
                            showContactForm = showContactForm,
                            editingContact = editingContact,
                            onAddNote = contactsViewModel::addNote,
                            onDeleteNote = contactsViewModel::deleteNote,
                            onTogglePin = contactsViewModel::togglePin,
                            onRefresh = {
                                if (isContactsEnabled) {
                                    WorkManagerHelper.triggerContactIndexing(context)
                                    contactsViewModel.refreshContacts()
                                } else {
                                    onRequestContacts()
                                }
                            },
                            colors = colors
                        )

                        Screen.EVENTS -> ProEvents(
                            tickets = tickets,
                            isLoading = eventsLoading,
                            contacts = contacts,
                            onRefresh = eventsViewModel::loadAll,
                            onNewEvent = { showEventForm = true },
                            onAddEvent = { contact, title, scheduledAt, message ->
                                eventsViewModel.addEvent(contact, title, scheduledAt, message)
                                showEventForm = false
                            },
                            onBroadcastEvent = { category: ContactCategory, title: String, scheduledAt: String, message: String? ->
                                eventsViewModel.broadcastEvent(category, title, scheduledAt, message)
                                showEventForm = false
                            },
                            showEventForm = showEventForm,
                            onDismissEventForm = { showEventForm = false },
                            onPrepare = eventsViewModel::prepareTicket,
                            onApprove = eventsViewModel::approveTicket,
                            onCancel = eventsViewModel::cancelTicket,
                            colors = colors
                        )

                        Screen.LOG -> ProLogs(
                            logs = logs,
                            filter = logFilter,
                            onFilter = logViewModel::setFilterLevel,
                            onClear = logViewModel::clearLogs,
                            colors = colors
                        )
                    }
                }
            }
        }
    }
}

private fun createApiService(): AgentApiService {
    val okHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("Authorization", "Bearer ${BuildConfig.APP_API_TOKEN}")
                .addHeader("ngrok-skip-browser-warning", "1")
                .build()
            chain.proceed(request)
        }
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    return Retrofit.Builder()
        .baseUrl(BuildConfig.BACKEND_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(AgentApiService::class.java)
}

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
