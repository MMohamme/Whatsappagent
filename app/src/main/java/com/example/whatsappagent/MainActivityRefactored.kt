package com.example.whatsappagent

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.compose.rememberNavController
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.whatsappagent.data.AppDatabase
import com.example.whatsappagent.data.remote.AgentApiService
import com.example.whatsappagent.data.remote.AgentRepository
import com.example.whatsappagent.ui.navigation.AdaptiveNavigation
import com.example.whatsappagent.ui.navigation.AgentNavGraph
import com.example.whatsappagent.ui.theme.AgentThemeProvider
import com.example.whatsappagent.ui.theme.AgentTheme
import com.example.whatsappagent.ui.theme.WhatsappagentTheme
import com.example.whatsappagent.ui.theme.useAgentColors
import com.example.whatsappagent.ui.viewmodel.ChatViewModel
import com.example.whatsappagent.ui.viewmodel.ContactsViewModel
import com.example.whatsappagent.ui.viewmodel.DashboardViewModel
import com.example.whatsappagent.ui.viewmodel.EventsViewModel
import com.example.whatsappagent.ui.viewmodel.LogViewModel
import com.example.whatsappagent.ui.viewmodel.MainViewModel
import com.example.whatsappagent.ui.viewmodel.QueueViewModel
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Refactored MainActivity - Clean separation of concerns
 * Only handles Activity lifecycle, services, and broadcasts
 */
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
class MainActivityRefactored : ComponentActivity() {

    private lateinit var agentService: AgentService
    private lateinit var agentLogger: AgentLogger
    
    // BroadcastReceiver for status updates
    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                AgentService.ACTION_STATUS_UPDATE -> {
                    val isConnected = intent.getBooleanExtra(AgentService.EXTRA_CONNECTED, false)
                    // Update ViewModel via repository
                }
                AgentLogger.ACTION_NEW_LOG -> {
                    val logEntry = intent.getStringExtra(AgentLogger.EXTRA_LOG_ENTRY)
                    // Update ViewModel
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize services
        initializeServices()
        
        // Register broadcast receivers
        registerBroadcastReceivers()
        
        // Set up Compose UI
        setContent {
            WhatsAppAgentApp()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        
        // Unregister broadcast receivers
        unregisterReceiver(statusReceiver)
    }

    private fun initializeServices() {
        // Initialize AgentService
        agentService = AgentService()
        
        // Initialize AgentLogger
        agentLogger = AgentLogger
    }

    private fun registerBroadcastReceivers() {
        // Register for status updates
        LocalBroadcastManager.getInstance(this).registerReceiver(
            statusReceiver,
            IntentFilter().apply {
                addAction(AgentService.ACTION_STATUS_UPDATE)
                addAction(AgentLogger.ACTION_NEW_LOG)
            }
        )
    }
}

/**
 * Root Composable - Clean separation from Activity
 */
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun WhatsAppAgentApp() {
    val context = LocalContext.current
    val windowSizeClass = calculateWindowSizeClass(context as Activity)
    val navController = rememberNavController()
    
    // Create dependencies
    val database = AppDatabase.getDatabase(context)
    val apiService = createApiService()
    val repository = AgentRepository(apiService, database)
    val application = context.applicationContext as Application
    
    val factory = ViewModelFactory(database, repository, application)
    
    // Create ViewModels
    val mainViewModel: MainViewModel = viewModel(factory = factory)
    val dashboardViewModel: DashboardViewModel = viewModel(factory = factory)
    val contactsViewModel: ContactsViewModel = viewModel(factory = factory)
    val eventsViewModel: EventsViewModel = viewModel(factory = factory)
    val queueViewModel: QueueViewModel = viewModel(factory = factory)
    val chatViewModel: ChatViewModel = viewModel(factory = factory)
    val logViewModel: LogViewModel = viewModel(factory = factory)
    
    // Get theme state
    val isDarkTheme by mainViewModel.isDarkTheme.collectAsState()
    
    AgentThemeProvider(theme = AgentTheme.DARK) {
        WhatsappagentTheme {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                AdaptiveNavigation(
                    navController = navController,
                    windowSizeClass = windowSizeClass,
                    C = useAgentColors()
                ) { paddingValues ->
                    AgentNavGraph(
                        navController = navController,
                        paddingValues = paddingValues,
                        mainViewModel = mainViewModel,
                        dashboardViewModel = dashboardViewModel,
                        contactsViewModel = contactsViewModel,
                        eventsViewModel = eventsViewModel,
                        queueViewModel = queueViewModel,
                        chatViewModel = chatViewModel,
                        logViewModel = logViewModel
                    )
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
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val retrofit = Retrofit.Builder()
        .baseUrl("https://humming-opposite-deforest.ngrok-free.dev/")
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    return retrofit.create(AgentApiService::class.java)
}

/**
 * ViewModel Factory for dependency injection
 */
class ViewModelFactory(
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
