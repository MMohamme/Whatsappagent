package com.example.whatsappagent.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.whatsappagent.ui.model.ContactCategory
import com.example.whatsappagent.ui.model.UiEvent
import com.example.whatsappagent.ui.screens.ChatScreenNew
import com.example.whatsappagent.ui.screens.ContactsScreen
import com.example.whatsappagent.ui.screens.DashboardScreen
import com.example.whatsappagent.ui.screens.EventsScreen
import com.example.whatsappagent.ui.screens.LiveLogScreen
import com.example.whatsappagent.ui.screens.QueueScreenNew
import com.example.whatsappagent.ui.theme.useAgentColors
import com.example.whatsappagent.ui.viewmodel.ChatViewModel
import com.example.whatsappagent.ui.viewmodel.ContactsViewModel
import com.example.whatsappagent.ui.viewmodel.DashboardViewModel
import com.example.whatsappagent.ui.viewmodel.EventsViewModel
import com.example.whatsappagent.ui.viewmodel.LogViewModel
import com.example.whatsappagent.ui.viewmodel.MainViewModel
import com.example.whatsappagent.ui.viewmodel.QueueViewModel
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FloatingActionButton
import androidx.compose.ui.Alignment

/**
 * Navigation graph for the WhatsApp Agent app
 * Defines all screens and navigation between them
 */
@Composable
fun AgentNavGraph(
    navController: NavHostController,
    paddingValues: PaddingValues,
    mainViewModel: MainViewModel,
    dashboardViewModel: DashboardViewModel,
    contactsViewModel: ContactsViewModel,
    eventsViewModel: EventsViewModel,
    queueViewModel: QueueViewModel,
    chatViewModel: ChatViewModel,
    logViewModel: LogViewModel
) {
    val C = useAgentColors()
    
    NavHost(
        navController = navController,
        startDestination = "dashboard",
        modifier = Modifier.padding(paddingValues)
    ) {
        composable("dashboard") {
            DashboardScreen(
                stats = dashboardViewModel.stats.collectAsState().value,
                pingLatency = mainViewModel.pingLatency.collectAsState().value,
                backendConnected = mainViewModel.backendConnected.collectAsState().value,
                logEntries = logViewModel.logEntries.collectAsState().value,
                onRefresh = { 
                    dashboardViewModel.refreshStats()
                },
                C = C
            )
        }
        
        composable("contacts") {
            ContactsScreen(
                contacts = contactsViewModel.uiContacts.collectAsState().value,
                notes = contactsViewModel.uiNotes.collectAsState().value,
                selectedContact = contactsViewModel.selectedContact.collectAsState().value,
                isLoading = contactsViewModel.isLoading.collectAsState().value,
                error = contactsViewModel.error.collectAsState().value,
                onToggleContact = { phone, active -> 
                    contactsViewModel.toggleContactActive(phone, active) 
                },
                onAddContact = { contact ->
                    contactsViewModel.addContact(contact)
                },
                onDeleteContact = { contactName ->
                    contactsViewModel.deleteContact(contactName)
                },
                onSelectContact = { contactName ->
                    contactsViewModel.selectContact(contactName)
                },
                onClearSelection = {
                    contactsViewModel.clearSelectedContact()
                },
                onRefresh = {
                    contactsViewModel.refreshContacts()
                },
                onClearError = {
                    contactsViewModel.clearError()
                },
                onAddNote = { contactName, text, pinned, expiry -> 
                    contactsViewModel.addNote(contactName, text, pinned, expiry)
                },
                onDeleteNote = { contactName, id -> 
                    contactsViewModel.deleteNote(contactName, id) 
                },
                onTogglePin = { contactName, id, current -> 
                    contactsViewModel.togglePin(contactName, id, current) 
                },
                showAddDialog = contactsViewModel.showAddContactDialog.collectAsState().value,
                onShowAddDialog = {
                    contactsViewModel.showAddContactDialog()
                },
                onHideAddDialog = {
                    contactsViewModel.hideAddContactDialog()
                },
                agentColors = C
            )
        }
        
        composable("events") {
            EventsScreen(
                events = eventsViewModel.events.collectAsState().value,
                contacts = contactsViewModel.uiContacts.collectAsState().value,
                isLoading = eventsViewModel.isLoading.collectAsState().value,
                onAddEvent = { contact, title, date, desc ->
                    eventsViewModel.addEvent(contact, title, date, desc)
                },
                onBroadcastEvent = { category, title, date, desc ->
                    eventsViewModel.broadcastEvent(category, title, date, desc)
                },
                onTriggerNow = { eventsViewModel.triggerNow(it) },
                onDeleteEvent = { eventsViewModel.deleteEvent(it) },
                onRefresh = { eventsViewModel.loadAll() },
                C = C
            )
        }
        
        composable("queue") {
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
        
        composable("chat") {
            ChatScreenNew(
                messages = chatViewModel.messages.collectAsState().value,
                senders = chatViewModel.senders.collectAsState().value,
                selectedSender = chatViewModel.selectedSender.collectAsState().value,
                onSelectSender = { sender ->
                    chatViewModel.selectSender(sender)
                },
                onClearSelection = {
                    chatViewModel.clearSelectedSender()
                },
                C = C
            )
        }
        
        composable("log") {
            LiveLogScreen(
                logEntries = logViewModel.logEntries.collectAsState().value,
                isAutoScroll = logViewModel.isAutoScroll.collectAsState().value,
                filterLevel = logViewModel.filterLevel.collectAsState().value,
                onToggleAutoScroll = {
                    logViewModel.toggleAutoScroll()
                },
                onSetFilterLevel = { level ->
                    logViewModel.setFilterLevel(level)
                },
                onClearLogs = {
                    logViewModel.clearLogs()
                },
                C = C
            )
        }
    }
}
