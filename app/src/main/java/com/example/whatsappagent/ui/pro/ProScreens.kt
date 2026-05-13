package com.example.whatsappagent.ui.pro

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.whatsappagent.AgentLogger
import com.example.whatsappagent.data.remote.EventTicketResponse
import com.example.whatsappagent.data.remote.QueueMessageResponse
import com.example.whatsappagent.data.remote.StatsResponse
import com.example.whatsappagent.ui.model.ContactCategory
import com.example.whatsappagent.ui.model.UiContact
import com.example.whatsappagent.ui.model.UiNote
import com.example.whatsappagent.ui.viewmodel.LogLevel
import java.util.Calendar
import java.util.Locale

@Composable
fun ProDashboard(
    stats: StatsResponse?,
    queue: List<QueueMessageResponse>,
    contacts: List<UiContact>,
    tickets: List<EventTicketResponse>,
    logs: List<AgentLogger.LogEntry>,
    backendConnected: Boolean,
    notificationEnabled: Boolean,
    contactsEnabled: Boolean,
    accessibilityEnabled: Boolean,
    autoPaused: Boolean,
    onRefresh: () -> Unit,
    colors: ProColors,
) {
    val reviewCount = queue.count { it.isReviewable }
    val failedCount = stats?.messages?.failedReplies ?: queue.count { it.status.startsWith("FAILED") }
    val newestRisk = queue.firstOrNull { it.isReviewable || it.riskLevel.equals("HIGH", ignoreCase = true) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            SectionHeader("Operations", "Scan state, queue pressure, and safety gates", Icons.Default.Shield, onRefresh, colors)
        }
        item {
            ProCard(colors, borderColor = if (reviewCount > 0) colors.warning.copy(alpha = 0.48f) else colors.outline) {
                Text(
                    if (autoPaused) "Auto-send paused. Review queue is the active control surface." else "Auto-send live. Watch review pressure and failed attempts.",
                    color = if (autoPaused) colors.warning else colors.textMuted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                newestRisk?.let {
                    Spacer(Modifier.height(6.dp))
                    Text("Newest risk: ${it.contactName} - ${it.reason ?: it.draftText}".cleanUiText(), color = colors.textSubtle, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                MetricTile("Review", reviewCount.toString(), colors.warning, Icons.Default.ListAlt, colors, Modifier.weight(1f))
                MetricTile("Pending", queue.count { it.displayStatus == "SEND_PENDING" || it.displayStatus == "SENDING" }.toString(), colors.info, Icons.Default.Send, colors, Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                MetricTile("Failed", failedCount.toString(), colors.danger, Icons.Default.Close, colors, Modifier.weight(1f))
                MetricTile("Contacts", contacts.size.toString(), colors.primary, Icons.Default.Contacts, colors, Modifier.weight(1f))
            }
        }
        item {
            ProCard(colors) {
                Text("System health", color = colors.text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.height(10.dp))
                HealthLine("Backend", backendConnected, colors)
                HealthLine("Notification listener", notificationEnabled, colors)
                HealthLine("Contact sync", contactsEnabled, colors)
                HealthLine("Accessibility optional", accessibilityEnabled, colors)
                HealthLine("Auto-send guard", !autoPaused, colors)
            }
        }
        item {
            ProCard(colors) {
                Text("Ticket lifecycle", color = colors.text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.height(10.dp))
                LifecycleBar(tickets, colors)
            }
        }
        item {
            ProCard(colors) {
                Text("Recent signal", color = colors.text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.height(8.dp))
                val recent = logs.takeLast(5).reversed()
                if (recent.isEmpty()) EmptyPanel("No log entries yet", colors) else recent.forEach { LogLine(it, colors) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProQueue(
    queue: List<QueueMessageResponse>,
    currentFilter: String?,
    isLoading: Boolean,
    errorMessage: String?,
    onFilterChange: (String?) -> Unit,
    onApprove: (QueueMessageResponse, String?) -> Unit,
    onBlock: (QueueMessageResponse, String?) -> Unit,
    onSync: () -> Unit,
    colors: ProColors,
) {
    val filters = listOf(null, "NEEDS_REVIEW", "SEND_PENDING", "SENDING", "SENT", "FAILED", "BLOCKED")
    var reviewTarget by remember { mutableStateOf<QueueMessageResponse?>(null) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { SectionHeader("Review Queue", "Approve, block, or inspect generated drafts", Icons.Default.ListAlt, onSync, colors) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                filters.forEach { status ->
                    val selected = currentFilter == status
                    FilterChip(
                        selected = selected,
                        onClick = { onFilterChange(status) },
                        label = { Text(status?.shortStatus() ?: "ALL", fontSize = 10.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = colors.surface,
                            selectedContainerColor = colors.primary.copy(alpha = 0.18f),
                            labelColor = colors.textMuted,
                            selectedLabelColor = colors.primary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selected,
                            borderColor = if (selected) colors.primary else colors.outline
                        )
                    )
                }
            }
        }
        if (isLoading) item { LoadingPanel(colors) }
        if (!errorMessage.isNullOrBlank()) item { EmptyPanel(errorMessage, colors) }
        if (queue.isEmpty() && !isLoading) item { EmptyPanel("Queue is clear", colors) }
        items(queue, key = { it.id ?: it.msgId }) { item ->
            QueueReviewRow(
                item = item,
                onInspect = { reviewTarget = item },
                onApprove = { onApprove(item, null) },
                onBlock = { onBlock(item, null) },
                colors = colors
            )
        }
    }
    reviewTarget?.let { item ->
        ReviewDraftDialog(
            item = item,
            onDismiss = { reviewTarget = null },
            onApprove = { reply ->
                onApprove(item, reply)
                reviewTarget = null
            },
            onBlock = { reason ->
                onBlock(item, reason)
                reviewTarget = null
            },
            colors = colors
        )
    }
}

@Composable
fun ProContacts(
    contacts: List<UiContact>,
    notes: List<UiNote>,
    selectedContactName: String?,
    isLoading: Boolean,
    onToggleActive: (String, Boolean) -> Unit,
    onSelectContact: (String) -> Unit,
    onBack: () -> Unit,
    onAddContact: () -> Unit,
    onEditContact: (UiContact) -> Unit,
    onDeleteContact: (String) -> Unit,
    onSaveContact: (UiContact) -> Unit,
    onDismissContactForm: () -> Unit,
    showContactForm: Boolean,
    editingContact: UiContact?,
    onAddNote: (String, String, Boolean, Long?) -> Unit,
    onDeleteNote: (String, Int) -> Unit,
    onTogglePin: (String, Int, Boolean) -> Unit,
    onRefresh: () -> Unit,
    colors: ProColors,
) {
    val selected = selectedContactName?.let { name -> contacts.firstOrNull { it.name == name } }
    if (showContactForm) {
        ContactFormDialog(initial = editingContact, onDismiss = onDismissContactForm, onSave = onSaveContact, colors = colors)
    }
    if (selected != null) {
        ContactDetailScreen(
            contact = selected,
            notes = notes,
            onBack = onBack,
            onToggleActive = { onToggleActive(selected.phoneNumber, selected.active) },
            onEdit = { onEditContact(selected) },
            onDelete = { onDeleteContact(selected.name) },
            onAddNote = { text, pinned, expiry -> onAddNote(selected.name, text, pinned, expiry) },
            onDeleteNote = { id -> onDeleteNote(selected.name, id) },
            onTogglePin = { id, pinned -> onTogglePin(selected.name, id, pinned) },
            colors = colors
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            SectionHeader("Contacts", "Trust level, phone sync, and auto controls", Icons.Default.People, onRefresh, colors, "New", onAddContact)
        }
        if (isLoading) item { LoadingPanel(colors) }
        if (contacts.isEmpty() && !isLoading) item { EmptyActionPanel("No contacts synced yet", "Add contact", onAddContact, colors) }
        items(contacts, key = { it.phoneNumber }) { contact ->
            ContactTrustRow(contact, onToggleActive, { onSelectContact(contact.name) }, colors)
        }
    }
}

@Composable
fun ProEvents(
    tickets: List<EventTicketResponse>,
    isLoading: Boolean,
    contacts: List<UiContact>,
    onRefresh: () -> Unit,
    onNewEvent: () -> Unit,
    onAddEvent: (String, String, String, String?) -> Unit,
    onBroadcastEvent: (ContactCategory, String, String, String?) -> Unit,
    showEventForm: Boolean,
    onDismissEventForm: () -> Unit,
    onPrepare: (Long) -> Unit,
    onApprove: (Long) -> Unit,
    onCancel: (Long) -> Unit,
    colors: ProColors,
) {
    if (showEventForm) {
        EventFormDialog(contacts, onDismissEventForm, onAddEvent, onBroadcastEvent, colors)
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            SectionHeader("Event Tickets", "Prepare, approve, and monitor lifecycle state", Icons.Default.Event, onRefresh, colors, "New", onNewEvent)
        }
        if (isLoading) item { LoadingPanel(colors) }
        if (tickets.isEmpty() && !isLoading) item { EmptyActionPanel("No event tickets planned", "Create event", onNewEvent, colors) }
        items(tickets, key = { it.id }) { ticket -> EventTicketRow(ticket, onPrepare, onApprove, onCancel, colors) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProLogs(
    logs: List<AgentLogger.LogEntry>,
    filter: LogLevel?,
    onFilter: (LogLevel?) -> Unit,
    onClear: () -> Unit,
    colors: ProColors,
) {
    val filtered = logs.filter { entry ->
        when (filter) {
            null -> true
            LogLevel.ERROR -> entry.type == AgentLogger.LogType.ERROR
            LogLevel.WARNING -> entry.type == AgentLogger.LogType.WAIT
            LogLevel.INFO -> entry.type != AgentLogger.LogType.ERROR
            LogLevel.DEBUG -> true
        }
    }.asReversed()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { SectionHeader("Live Logs", "Filtered system signal and failure causes", Icons.Default.Info, onClear, colors) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                listOf(null, LogLevel.INFO, LogLevel.WARNING, LogLevel.ERROR).forEach { level ->
                    FilterChip(
                        selected = filter == level,
                        onClick = { onFilter(level) },
                        label = { Text(level?.name ?: "ALL", fontSize = 10.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = colors.surface,
                            selectedContainerColor = colors.primary.copy(alpha = 0.18f),
                            labelColor = colors.textMuted,
                            selectedLabelColor = colors.primary
                        )
                    )
                }
            }
        }
        if (filtered.isEmpty()) item { EmptyPanel("No matching logs", colors) }
        items(filtered.take(200)) { entry -> LogLine(entry, colors, raised = true) }
    }
}

@Composable
private fun QueueReviewRow(item: QueueMessageResponse, onInspect: () -> Unit, onApprove: () -> Unit, onBlock: () -> Unit, colors: ProColors) {
    val displayStatus = item.displayStatus
    val color = statusColor(displayStatus, colors)
    val priority = when {
        item.status.contains("FAILED") -> "Repair"
        item.riskLevel.equals("HIGH", ignoreCase = true) -> "High risk"
        item.isReviewable -> "Needs decision"
        else -> "Monitor"
    }
    ProCard(colors, modifier = Modifier.clickable(onClick = onInspect), borderColor = color.copy(alpha = 0.38f)) {
        Row(verticalAlignment = Alignment.Top) {
            StatusDot(color)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.contactName, color = colors.text, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    StatusPill(priority, color, colors)
                }
                Text(item.draftText, color = colors.textMuted, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (!item.reason.isNullOrBlank()) Text(item.reason, color = colors.textSubtle, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    StatusPill(displayStatus.shortStatus(), color, colors)
                    item.riskLevel?.takeIf { it.isNotBlank() }?.let { StatusPill("RISK ${it.uppercase(Locale.getDefault())}", statusColor(it, colors), colors) }
                    Text(item.timestamp?.formatIsoTime() ?: "just now", color = colors.textSubtle, fontSize = 11.sp)
                }
            }
        }
        if (item.isReviewable) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onBlock, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Block, contentDescription = null, tint = colors.danger, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Block", color = colors.danger)
                }
                Button(onClick = onApprove, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = colors.success)) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Approve", color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun ContactTrustRow(contact: UiContact, onToggleActive: (String, Boolean) -> Unit, onClick: () -> Unit, colors: ProColors) {
    val color = contact.category.toColor(colors)
    ProCard(colors) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(onClick = onClick)) {
            Box(modifier = Modifier.size(38.dp).clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Text(contact.name.firstOrNull()?.uppercase() ?: "?", color = color, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(contact.name, color = colors.text, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(contact.phoneNumber, color = colors.textMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusPill(contact.category.name, color, colors)
                    StatusPill(contactTone(contact), if (contact.active) colors.success else colors.textSubtle, colors)
                }
            }
            Switch(checked = contact.active, onCheckedChange = { onToggleActive(contact.phoneNumber, contact.active) })
        }
    }
}

@Composable
private fun EventTicketRow(ticket: EventTicketResponse, onPrepare: (Long) -> Unit, onApprove: (Long) -> Unit, onCancel: (Long) -> Unit, colors: ProColors) {
    val color = statusColor(ticket.status, colors)
    ProCard(colors, borderColor = color.copy(alpha = 0.34f)) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Default.DateRange, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(ticket.title, color = colors.text, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    StatusPill(ticket.status.shortStatus(), color, colors)
                }
                Text("${ticket.targetType}: ${ticket.targetCategory ?: ticket.targetContactId ?: "GLOBAL"}", color = colors.textMuted, fontSize = 12.sp)
                Text("${ticket.recipientsCount} recipients - ${ticket.scheduledAt.formatIsoTime()}", color = colors.textSubtle, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ActionButton("Prepare", Icons.Default.ListAlt, ticket.status == "DRAFT", { onPrepare(ticket.id) }, colors, Modifier.weight(1f))
            ActionButton("Approve", Icons.Default.Send, ticket.status == "PREPARED", { onApprove(ticket.id) }, colors, Modifier.weight(1f))
            ActionButton("Cancel", Icons.Default.Close, ticket.status != "SENT" && ticket.status != "CANCELLED", { onCancel(ticket.id) }, colors, Modifier.weight(1f), danger = true)
        }
    }
}

@Composable
private fun ReviewDraftDialog(item: QueueMessageResponse, onDismiss: () -> Unit, onApprove: (String) -> Unit, onBlock: (String?) -> Unit, colors: ProColors) {
    var replyText by remember(item.id, item.msgId) { mutableStateOf(item.draftText) }
    var blockReason by remember(item.id, item.msgId) { mutableStateOf(item.reason.orEmpty()) }
    Dialog(onDismissRequest = onDismiss) {
        ProCard(colors, borderColor = colors.primary.copy(alpha = 0.45f)) {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 620.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ListAlt, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Review draft", color = colors.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(item.contactName, color = colors.textMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = colors.textMuted)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    StatusPill(item.displayStatus.shortStatus(), statusColor(item.displayStatus, colors), colors)
                    item.riskLevel?.takeIf { it.isNotBlank() }?.let { StatusPill("RISK ${it.uppercase(Locale.getDefault())}", statusColor(it, colors), colors) }
                    item.category?.takeIf { it.isNotBlank() }?.let { StatusPill(it.uppercase(Locale.getDefault()), colors.info, colors) }
                }
                OutlinedTextField(value = replyText, onValueChange = { replyText = it }, label = { Text("Draft text") }, minLines = 4, maxLines = 8, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = blockReason, onValueChange = { blockReason = it }, label = { Text("Review note / block reason") }, minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth())
                if (!item.notificationKey.isNullOrBlank() || !item.messageCustomId.isNullOrBlank()) {
                    Text("Source ${item.messageCustomId ?: item.notificationKey}", color = colors.textSubtle, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { onBlock(blockReason) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Block, contentDescription = null, tint = colors.danger, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Block", color = colors.danger)
                    }
                    Button(onClick = { onApprove(replyText) }, enabled = replyText.isNotBlank(), modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = colors.success)) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Approve", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactDetailScreen(
    contact: UiContact,
    notes: List<UiNote>,
    onBack: () -> Unit,
    onToggleActive: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onAddNote: (String, Boolean, Long?) -> Unit,
    onDeleteNote: (Int) -> Unit,
    onTogglePin: (Int, Boolean) -> Unit,
    colors: ProColors,
) {
    var showNoteForm by remember { mutableStateOf(false) }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = colors.primary) }
                Column(modifier = Modifier.weight(1f)) {
                    Text(contact.name, color = colors.text, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Contact control module - ${contactTone(contact)}", color = colors.textMuted, fontSize = 12.sp)
                }
                IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.Edit, contentDescription = "Edit contact", tint = colors.info) }
            }
        }
        item {
            ProCard(colors) {
                DetailLine("Phone", contact.phoneNumber, colors)
                DetailLine("Relation", contact.relation, colors)
                DetailLine("Language", contact.lang, colors)
                DetailLine("Category", contact.category.name, colors)
                Spacer(Modifier.height(8.dp))
                Text("Persona", color = colors.textSubtle, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(contact.style.ifBlank { "No persona rules set." }, color = colors.textMuted, fontSize = 13.sp, lineHeight = 18.sp)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ToggleLine(if (contact.active) "Agent active" else "Agent off", contact.active, { onToggleActive() }, if (contact.active) colors.success else colors.textSubtle, Modifier.weight(1f), colors)
                    TextButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = colors.danger, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Delete", color = colors.danger)
                    }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Notes", color = colors.text, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                Button(onClick = { showNoteForm = !showNoteForm }, shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = colors.primary), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp), modifier = Modifier.height(34.dp)) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add note", color = Color.White, fontSize = 12.sp)
                }
            }
        }
        if (showNoteForm) item {
            NoteFormCard({ text, pinned, expiry ->
                onAddNote(text, pinned, expiry)
                showNoteForm = false
            }, { showNoteForm = false }, colors)
        }
        if (notes.isEmpty() && !showNoteForm) item { EmptyActionPanel("No notes for this contact", "Add note", { showNoteForm = true }, colors) }
        items(notes, key = { it.id }) { note ->
            NoteRow(note, { onDeleteNote(note.id.toInt()) }, { onTogglePin(note.id.toInt(), isPinnedNote(note)) }, colors)
        }
    }
}

@Composable
private fun NoteFormCard(onSave: (String, Boolean, Long?) -> Unit, onCancel: () -> Unit, colors: ProColors) {
    var text by remember { mutableStateOf("") }
    var pinned by remember { mutableStateOf(false) }
    var hasExpiry by remember { mutableStateOf(false) }
    var expiry by remember { mutableStateOf<Long?>(null) }
    ProCard(colors, borderColor = colors.primary.copy(alpha = 0.35f)) {
        OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Note") }, minLines = 3, modifier = Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = pinned, onCheckedChange = { pinned = it })
            Text("Pinned", color = colors.textMuted, fontSize = 12.sp)
            Spacer(Modifier.width(12.dp))
            Checkbox(checked = hasExpiry, onCheckedChange = { hasExpiry = it })
            Text("Expires", color = colors.textMuted, fontSize = 12.sp)
        }
        if (hasExpiry) DateTimeField(value = expiry, onChange = { expiry = it }, colors = colors)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel", color = colors.textMuted) }
            Button(onClick = { onSave(text.trim(), pinned, if (hasExpiry) expiry else null) }, enabled = text.isNotBlank(), modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = colors.primary)) {
                Text("Save", color = Color.White)
            }
        }
    }
}

@Composable
private fun NoteRow(note: UiNote, onDelete: () -> Unit, onTogglePin: () -> Unit, colors: ProColors) {
    val pinned = isPinnedNote(note)
    ProCard(colors, borderColor = if (pinned) colors.primary.copy(alpha = 0.42f) else colors.outline) {
        Row(verticalAlignment = Alignment.Top) {
            StatusDot(if (pinned) colors.primary else colors.textSubtle)
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(if (pinned) "Pinned note" else "Note", color = if (pinned) colors.primary else colors.textSubtle, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(note.text.removePrefix("ðŸ“Œ ").removePrefix("ðŸ“Œ").trim(), color = colors.textMuted, fontSize = 13.sp, lineHeight = 18.sp)
                Text("Created ${note.createdAt}${if (note.expiresAt.isNotBlank()) " - until ${note.expiresAt}" else ""}", color = colors.textSubtle, fontSize = 10.sp)
            }
            IconButton(onClick = onTogglePin, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Check, contentDescription = "Toggle pin", tint = if (pinned) colors.primary else colors.textSubtle, modifier = Modifier.size(16.dp)) }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Delete, contentDescription = "Delete note", tint = colors.danger, modifier = Modifier.size(16.dp)) }
        }
    }
}

@Composable
private fun ContactFormDialog(initial: UiContact?, onDismiss: () -> Unit, onSave: (UiContact) -> Unit, colors: ProColors) {
    var name by remember(initial) { mutableStateOf(initial?.name.orEmpty()) }
    var phone by remember(initial) { mutableStateOf(initial?.phoneNumber.orEmpty()) }
    var relation by remember(initial) { mutableStateOf(initial?.relation.orEmpty()) }
    var lang by remember(initial) { mutableStateOf(initial?.lang ?: "Deutsch") }
    var category by remember(initial) { mutableStateOf(initial?.category ?: ContactCategory.UNKNOWN) }
    var style by remember(initial) { mutableStateOf(initial?.style.orEmpty()) }
    var active by remember(initial) { mutableStateOf(initial?.active ?: true) }
    Dialog(onDismissRequest = onDismiss) {
        FormShell(if (initial == null) "New contact" else "Edit contact", colors) {
            ProTextField("WhatsApp name", name, { name = it }, enabled = initial == null)
            ProTextField("Phone number", phone, { phone = it })
            ProTextField("Relation", relation, { relation = it })
            ProTextField("Language", lang, { lang = it })
            CategoryPicker(category, { category = it }, colors)
            ProTextField("Persona and behavior rules", style, { style = it }, minLines = 4)
            ToggleLine(if (active) "Agent active" else "Agent off", active, { active = it }, if (active) colors.success else colors.textSubtle, Modifier.fillMaxWidth(), colors)
            FormFooter(onDismiss, enabled = name.isNotBlank(), colors = colors) {
                onSave(UiContact(name.trim(), phone.trim().ifBlank { name.trim() }, lang.trim().ifBlank { "Deutsch" }, relation.trim(), category, style.trim(), active, initial?.replies ?: 0, initial?.lastSeen ?: "new"))
            }
        }
    }
}

@Composable
private fun EventFormDialog(
    contacts: List<UiContact>,
    onDismiss: () -> Unit,
    onSaveContact: (String, String, String, String?) -> Unit,
    onSaveCategory: (ContactCategory, String, String, String?) -> Unit,
    colors: ProColors,
) {
    var categoryMode by remember { mutableStateOf(false) }
    var contactName by remember { mutableStateOf(contacts.firstOrNull()?.name.orEmpty()) }
    var category by remember { mutableStateOf(ContactCategory.FRIEND) }
    var title by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var scheduledAt by remember { mutableStateOf(System.currentTimeMillis() + 60 * 60 * 1000) }
    Dialog(onDismissRequest = onDismiss) {
        FormShell("New event", colors) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ModeButton("Contact", !categoryMode, { categoryMode = false }, colors, Modifier.weight(1f))
                ModeButton("Category", categoryMode, { categoryMode = true }, colors, Modifier.weight(1f))
            }
            if (categoryMode) CategoryPicker(category, { category = it }, colors) else ContactPicker(contactName, contacts, { contactName = it }, colors)
            ProTextField("Title", title, { title = it })
            DateTimeField(scheduledAt, { scheduledAt = it ?: scheduledAt }, colors)
            ProTextField("Message", message, { message = it }, minLines = 4)
            FormFooter(onDismiss, enabled = title.isNotBlank() && (categoryMode || contactName.isNotBlank()), colors = colors) {
                val iso = scheduledAt.toIsoLocal()
                if (categoryMode) onSaveCategory(category, title.trim(), iso, message.trim().ifBlank { null })
                else onSaveContact(contactName.trim(), title.trim(), iso, message.trim().ifBlank { null })
            }
        }
    }
}

@Composable
private fun FormShell(title: String, colors: ProColors, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surface)
            .border(1.dp, colors.outline, RoundedCornerShape(8.dp))
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(title, color = colors.text, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        content()
    }
}

@Composable
private fun FormFooter(onDismiss: () -> Unit, enabled: Boolean, colors: ProColors, onSave: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel", color = colors.textMuted) }
        Button(onClick = onSave, enabled = enabled, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = colors.primary)) {
            Text("Save", color = Color.White)
        }
    }
}

@Composable
private fun ProTextField(label: String, value: String, onValueChange: (String) -> Unit, enabled: Boolean = true, minLines: Int = 1) {
    OutlinedTextField(value = value, onValueChange = onValueChange, label = { Text(label) }, enabled = enabled, minLines = minLines, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun ModeButton(label: String, selected: Boolean, onClick: () -> Unit, colors: ProColors, modifier: Modifier = Modifier) {
    Box(modifier = modifier.clip(RoundedCornerShape(8.dp)).background(if (selected) colors.primary else colors.surfaceRaised).clickable(onClick = onClick).padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
        Text(label, color = if (selected) Color.White else colors.textMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CategoryPicker(selected: ContactCategory, onSelect: (ContactCategory) -> Unit, colors: ProColors) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        ContactCategory.entries.forEach { category ->
            val color = category.toColor(colors)
            Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (selected == category) color.copy(alpha = 0.18f) else colors.surfaceRaised).border(1.dp, if (selected == category) color else colors.outline, RoundedCornerShape(8.dp)).clickable { onSelect(category) }.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Text(category.name, color = if (selected == category) color else colors.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ContactPicker(value: String, contacts: List<UiContact>, onSelect: (String) -> Unit, colors: ProColors) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(colors.surfaceRaised).border(1.dp, colors.outline, RoundedCornerShape(8.dp)).clickable { expanded = true }.padding(12.dp)) {
            Text(value.ifBlank { "Select contact" }, color = if (value.isBlank()) colors.textSubtle else colors.text, fontSize = 13.sp)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            contacts.forEach { contact ->
                DropdownMenuItem(text = { Text(contact.name) }, onClick = {
                    onSelect(contact.name)
                    expanded = false
                })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimeField(value: Long?, onChange: (Long?) -> Unit, colors: ProColors) {
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var selectedDate by remember(value) { mutableStateOf(value ?: System.currentTimeMillis()) }
    val dateState = rememberDatePickerState(initialSelectedDateMillis = selectedDate)
    val calendar = Calendar.getInstance().apply { timeInMillis = selectedDate }
    val timeState = rememberTimePickerState(initialHour = calendar.get(Calendar.HOUR_OF_DAY), initialMinute = calendar.get(Calendar.MINUTE))
    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(colors.surfaceRaised).border(1.dp, colors.outline, RoundedCornerShape(8.dp)).clickable { showDate = true }.padding(12.dp)) {
        Text(value?.toDisplayDate() ?: "Select date and time", color = colors.text, fontSize = 13.sp)
    }
    if (showDate) {
        DatePickerDialog(onDismissRequest = { showDate = false }, confirmButton = {
            TextButton(onClick = {
                selectedDate = dateState.selectedDateMillis ?: selectedDate
                showDate = false
                showTime = true
            }) { Text("Next") }
        }) { DatePicker(state = dateState) }
    }
    if (showTime) {
        Dialog(onDismissRequest = { showTime = false }) {
            ProCard(colors) {
                TimePicker(state = timeState)
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { showTime = false }) { Text("Cancel") }
                    TextButton(onClick = {
                        val result = Calendar.getInstance().apply {
                            timeInMillis = selectedDate
                            set(Calendar.HOUR_OF_DAY, timeState.hour)
                            set(Calendar.MINUTE, timeState.minute)
                            set(Calendar.SECOND, 0)
                        }.timeInMillis
                        onChange(result)
                        showTime = false
                    }) { Text("OK") }
                }
            }
        }
    }
}

private fun isPinnedNote(note: UiNote): Boolean = note.text.startsWith("ðŸ“Œ")
