package com.example.whatsappagent.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.whatsappagent.data.remote.EventResponse
import com.example.whatsappagent.ui.components.*
import com.example.whatsappagent.ui.model.ContactCategory
import com.example.whatsappagent.ui.model.UiContact
import com.example.whatsappagent.ui.theme.AgentColors
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimePicker(
    initialDateTime: Long?,
    onDateTimeSelected: (Long) -> Unit,
    C: AgentColors
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialDateTime)
    val timePickerState = rememberTimePickerState(
        initialHour = Calendar.getInstance().apply { timeInMillis = initialDateTime ?: System.currentTimeMillis() }.get(Calendar.HOUR_OF_DAY),
        initialMinute = Calendar.getInstance().apply { timeInMillis = initialDateTime ?: System.currentTimeMillis() }.get(Calendar.MINUTE)
    )

    val formattedDate = initialDateTime?.let {
        SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(it))
    } ?: "Zeitpunkt wählen..."

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(C.inputBg)
            .border(1.dp, C.border, RoundedCornerShape(8.dp))
            .clickable { showDatePicker = true }
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(formattedDate, color = if (initialDateTime != null) C.textPrimary else C.textMuted, fontSize = 13.sp)
            Text("📅", fontSize = 14.sp)
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    showDatePicker = false
                    showTimePicker = true
                }) { Text("Weiter") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val calendar = Calendar.getInstance().apply {
                        timeInMillis = datePickerState.selectedDateMillis ?: System.currentTimeMillis()
                        set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                        set(Calendar.MINUTE, timePickerState.minute)
                    }
                    onDateTimeSelected(calendar.timeInMillis)
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Abbrechen") }
            },
            title = { Text("Uhrzeit wählen") },
            text = {
                TimePicker(state = timePickerState)
            }
        )
    }
}

@Composable
fun EventsScreen(
    events: List<EventResponse>,
    contacts: List<UiContact>,
    isLoading: Boolean = false,
    onAddEvent: (String, String, String, String?) -> Unit, // contact, title, date, desc
    onBroadcastEvent: (ContactCategory, String, String, String?) -> Unit,
    onTriggerNow: (Int) -> Unit,
    onDeleteEvent: (Int) -> Unit,
    onRefresh: () -> Unit = {},
    successMessage: String? = null, // Added
    onClearSuccess: () -> Unit = {}, // Added
    C: AgentColors,
) {
    var showAddDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(successMessage) {
        successMessage?.let {
            snackbarHostState.showSnackbar(it)
            onClearSuccess()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = C.accent,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "Event hinzufügen")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionLabel("Geplante Events (${events.size})", C)
                TextButton(onClick = onRefresh) {
                    Text("Refresh", color = C.accent, fontSize = 12.sp)
                }
            }
            
            Spacer(Modifier.height(12.dp))

            if (isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = C.accent)
                Spacer(Modifier.height(12.dp))
            }

            if (events.isEmpty()) {
                EmptyState(
                    title = "Keine Events geplant",
                    description = "Geplante Nachrichten oder Erinnerungen erscheinen hier.",
                    icon = "🎉",
                    C = C,
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(events, key = { it.id }) { event ->
                        EventItem(
                            event = event,
                            onTrigger = { onTriggerNow(event.id) },
                            onDelete = { onDeleteEvent(event.id) },
                            C = C
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddEventDialog(
            contacts = contacts,
            onSave = { contact, title, date, desc ->
                onAddEvent(contact, title, date, desc)
                showAddDialog = false
            },
            onBroadcast = { category, title, date, desc ->
                onBroadcastEvent(category, title, date, desc)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false },
            C = C
        )
    }
}

@Composable
private fun EventItem(
    event: EventResponse,
    onTrigger: () -> Unit,
    onDelete: () -> Unit,
    C: AgentColors
) {
    val statusColor = when (event.status) {
        "PENDING" -> C.yellow
        "TRIGGERED" -> C.accent
        "SENT" -> C.green
        "FAILED" -> C.red
        else -> C.textMuted
    }

    AgentCard(C = C, padding = 16) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(event.title, fontWeight = FontWeight.ExtraBold, color = C.textPrimary, fontSize = 15.sp)
                    Spacer(Modifier.height(2.dp))
                    Text("Empfänger: ${event.contactName}", color = C.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
                AgentBadge(event.status, statusColor)
            }

            if (!event.description.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(C.inputBg)
                        .padding(10.dp)
                ) {
                    Text(event.description, color = C.textSecondary, fontSize = 12.sp, lineHeight = 18.sp)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("📅", fontSize = 10.sp)
                    Text(
                        text = formatIsoDate(event.scheduledAt),
                        color = C.textMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (event.status == "PENDING") {
                        IconButton(onClick = onTrigger, modifier = Modifier.size(28.dp).clip(CircleShape).background(C.accentLow)) {
                            Icon(Icons.Default.Send, contentDescription = "Jetzt auslösen", tint = C.accent, modifier = Modifier.size(16.dp))
                        }
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp).clip(CircleShape).background(C.redLow)) {
                        Icon(Icons.Default.Delete, contentDescription = "Löschen", tint = C.red, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddEventDialog(
    contacts: List<UiContact>,
    onSave: (String, String, String, String?) -> Unit,
    onBroadcast: (ContactCategory, String, String, String?) -> Unit,
    onDismiss: () -> Unit,
    C: AgentColors
) {
    var mode by remember { mutableIntStateOf(0) } // 0 = Single, 1 = Category
    var selectedContact by remember { mutableStateOf(contacts.firstOrNull()?.name ?: "") }
    var selectedCategory by remember { mutableStateOf<ContactCategory>(ContactCategory.FRIEND) }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedDateTimeMillis by remember { mutableStateOf<Long?>(System.currentTimeMillis() + 3600_000) } // Default in 1h

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(C.surface)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Event planen", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = C.textPrimary)
            
            // Mode Selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(C.surfaceHigh)
                    .padding(2.dp)
            ) {
                listOf("Einzelner Kontakt", "Ganze Kategorie").forEachIndexed { index, label ->
                    val isSelected = mode == index
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) C.accent else Color.Transparent)
                            .clickable { mode = index }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) Color.White else C.textSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            if (mode == 0) {
                // Single Contact
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("EMPFÄNGER", color = C.textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    AgentTextField(value = selectedContact, onValueChange = { selectedContact = it }, placeholder = "Kontaktname", C = C)
                }
            } else {
                // Category
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("KATEGORIE", color = C.textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        val categories = ContactCategory.entries.filter { it != ContactCategory.UNKNOWN }
                        categories.forEach { cat ->
                            val isSelected = selectedCategory == cat
                            val col = categoryColor(cat, C)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) col.copy(alpha = 0.15f) else Color.Transparent)
                                    .border(1.dp, if (isSelected) col else C.border, RoundedCornerShape(6.dp))
                                    .clickable { selectedCategory = cat }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(categoryLabel(cat), color = if (isSelected) col else C.textMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    
                    // Contact Preview
                    val filteredContacts = contacts.filter { it.category == selectedCategory && it.active }
                    if (filteredContacts.isNotEmpty()) {
                        Text(
                            "Empfänger Vorschau (${filteredContacts.size}):",
                            color = C.textMuted,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        androidx.compose.foundation.layout.FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            filteredContacts.take(8).forEach { contact ->
                                AgentPill(contact.name, categoryColor(contact.category, C))
                            }
                            if (filteredContacts.size > 8) {
                                Text("...", color = C.textMuted, fontSize = 10.sp)
                            }
                        }
                    } else {
                        Text("Keine aktiven Kontakte in dieser Kategorie", color = C.red, fontSize = 10.sp)
                    }
                }
            }
            
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("TITEL", color = C.textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                AgentTextField(value = title, onValueChange = { title = it }, placeholder = "z.B. Geburtstagsgruß", C = C)
            }
            
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("ZEITPUNKT", color = C.textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                DateTimePicker(
                    initialDateTime = selectedDateTimeMillis,
                    onDateTimeSelected = { selectedDateTimeMillis = it },
                    C = C
                )
            }
            
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("NACHRICHT", color = C.textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                AgentTextField(value = description, onValueChange = { description = it }, placeholder = "Text für die Nachricht...", C = C, singleLine = false, modifier = Modifier.height(100.dp))
            }

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = C.surfaceHigh),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Abbrechen", color = C.textPrimary, fontSize = 13.sp)
                }
                Button(
                    onClick = { 
                        val isoDate = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date(selectedDateTimeMillis!!))
                        if (mode == 0) onSave(selectedContact, title, isoDate, description.ifBlank { null })
                        else onBroadcast(selectedCategory, title, isoDate, description.ifBlank { null })
                    },
                    modifier = Modifier.weight(1f),
                    enabled = (mode == 1 || selectedContact.isNotBlank()) && title.isNotBlank() && selectedDateTimeMillis != null,
                    colors = ButtonDefaults.buttonColors(containerColor = C.accent),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Speichern", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun formatIsoDate(iso: String?): String {
    if (iso == null) return "Unbekannt"
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        val date = sdf.parse(iso)
        SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(date!!)
    } catch (e: Exception) {
        iso
    }
}
