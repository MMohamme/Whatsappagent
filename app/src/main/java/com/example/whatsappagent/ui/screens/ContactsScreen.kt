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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.whatsappagent.ui.components.*
import com.example.whatsappagent.ui.model.*
import com.example.whatsappagent.ui.theme.AgentColors
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteDateTimePicker(
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
    } ?: "Datum & Uhrzeit wählen..."

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(C.surface)
            .border(1.dp, C.border, RoundedCornerShape(8.dp))
            .clickable { showDatePicker = true }
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("📅", fontSize = 12.sp)
                Text(formattedDate, color = if (initialDateTime != null) C.textPrimary else C.textMuted, fontSize = 12.sp)
            }
            Text("▾", color = C.textMuted, fontSize = 12.sp)
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

// ─────────────────────────────────────────────────────────────────────────────
// CONTACTS SCREEN
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun ContactsScreen(
    contacts: List<UiContact>,
    notes: List<UiNote>,
    selectedContact: String?,
    isLoading: Boolean,
    error: String?,
    onToggleContact: (String, Boolean) -> Unit,
    onAddContact: (UiContact) -> Unit,
    onDeleteContact: (String) -> Unit,
    onSelectContact: (String) -> Unit,
    onClearSelection: () -> Unit,
    onRefresh: () -> Unit,
    onClearError: () -> Unit,
    onClearSuccess: () -> Unit = {}, // Added
    onAddNote: (String, String, Boolean, Long?) -> Unit, 
    onDeleteNote: (String, Int) -> Unit,
    onTogglePin: (String, Int, Boolean) -> Unit = { _, _, _ -> },
    onEditContact: (UiContact) -> Unit = {}, // Added edit handler
    showAddDialog: Boolean,
    onShowAddDialog: () -> Unit,
    onHideAddDialog: () -> Unit,
    successMessage: String? = null, // Added
    agentColors: AgentColors,
) {
    val C = agentColors
    var search by remember { mutableStateOf("") }
    var showEditDialog by remember { mutableStateOf<UiContact?>(null) }
    
    val snackbarHostState = remember { SnackbarHostState() }
    
    LaunchedEffect(successMessage) {
        successMessage?.let {
            snackbarHostState.showSnackbar(it)
            onClearSuccess()
        }
    }

    var localSelectedName by remember { mutableStateOf<String?>(null) }
    val effectiveSelectedName = selectedContact ?: localSelectedName
    
    val filtered = contacts.filter {
        it.name.contains(search, ignoreCase = true) || it.relation.contains(search, ignoreCase = true)
    }
    val selected = effectiveSelectedName?.let { n -> contacts.find { it.name == n } }

    if (showAddDialog) {
        AddContactDialog(
            onSave = { onAddContact(it); onHideAddDialog() },
            onDismiss = { onHideAddDialog() },
            C = C,
        )
    }

    if (showEditDialog != null) {
        AddContactDialog(
            contactToEdit = showEditDialog,
            onSave = { onEditContact(it); showEditDialog = null },
            onDismiss = { showEditDialog = null },
            C = C,
        )
    }

    if (error != null) {
        AlertDialog(
            onDismissRequest = onClearError,
            title = { Text("Fehler") },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = onClearError) { Text("OK") }
            }
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        val isMobile = maxWidth < 700.dp

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = Color.Transparent
        ) { paddingValues ->
            Box(modifier = Modifier.padding(paddingValues)) {
                if ((isMobile && selected != null)) {
                    // Mobile Detail View
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { 
                                onClearSelection()
                            }) {
                                Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = C.accent)
                            }
                            Text("Zurück zur Liste", color = C.accent, fontSize = 14.sp)
                        }
                        Spacer(Modifier.height(8.dp))
                        ContactDetailPanel(
                            contact = selected,
                            notes = notes,
                            onToggle = { onToggleContact(selected.phoneNumber, selected.active) },
                            onEdit = { showEditDialog = selected }, // Added edit
                            onAddNote = { text, pinned, expiry -> onAddNote(selected.phoneNumber, text, pinned, expiry) },
                            onDeleteNote = { id -> onDeleteNote(selected.phoneNumber, id.toInt()) },
                            onTogglePin = { id, current -> onTogglePin(selected.phoneNumber, id.toInt(), current) },
                            onDeleteContact = { 
                                onDeleteContact(selected.name)
                                onClearSelection()
                            },
                            C = C,
                        )
                    }
                } else {
                    Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        // ── Left: list ────────────────────────────────────────────────────────
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Search + Add button
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                AgentTextField(
                                    value = search, onValueChange = { search = it },
                                    placeholder = "Suchen...", C = C,
                                    modifier = Modifier.weight(1f),
                                )
                                
                                IconButton(onClick = onRefresh) {
                                    Icon(androidx.compose.material.icons.Icons.Default.Refresh, contentDescription = "Refresh", tint = C.accent)
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(C.accent)
                                        .clickable { onShowAddDialog() }
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                ) {
                                    Text("+ Neu", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }

                            if (isLoading) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = C.accent)
                            }

                            // Contact list
                            if (contacts.isEmpty() && !isLoading) {
                                EmptyState(
                                    title = "Keine Kontakte",
                                    description = "Klicke auf + Neu, um deinen ersten Kontakt zu konfigurieren.",
                                    icon = "👥",
                                    C = C
                                )
                            } else if (filtered.isEmpty() && !isLoading) {
                                EmptyState(
                                    title = "Keine Treffer",
                                    description = "Deine Suche nach '$search' ergab leider keine Ergebnisse.",
                                    icon = "🔍",
                                    C = C
                                )
                            } else {
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(filtered, key = { it.name }) { contact ->
                                        ContactListItem(
                                            contact = contact,
                                            isSelected = effectiveSelectedName == contact.name,
                                            onSelect = { 
                                                onSelectContact(contact.name)
                                            },
                                            onToggle = { onToggleContact(contact.phoneNumber, contact.active) },
                                            C = C,
                                        )
                                    }
                                }
                            }
                        }

                        // ── Right: detail panel (Tablet only) ───────────────────────────────────────────────
                        if (!isMobile) {
                            Box(modifier = Modifier.width(340.dp).fillMaxHeight()) {
                                if (selected != null) {
                                    ContactDetailPanel(
                                        contact = selected,
                                        notes = notes,
                                        onToggle = { onToggleContact(selected.phoneNumber, selected.active) },
                                        onEdit = { showEditDialog = selected }, // Added edit
                                        onAddNote = { text, pinned, expiry -> onAddNote(selected.phoneNumber, text, pinned, expiry) },
                                        onDeleteNote = { id -> onDeleteNote(selected.phoneNumber, id.toInt()) },
                                        onTogglePin = { id, current -> onTogglePin(selected.phoneNumber, id.toInt(), current) },
                                        onDeleteContact = { 
                                            onDeleteContact(selected.name)
                                            onClearSelection()
                                        },
                                        C = C,
                                    )
                                } else {
                                    EmptyDetailPanel(onAdd = onShowAddDialog, C = C)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// CONTACT LIST ITEM
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ContactListItem(
    contact: UiContact,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onToggle: () -> Unit,
    C: AgentColors,
) {
    val catCol = categoryColor(contact.category, C)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) C.accentLow else C.surface)
            .border(1.dp, if (isSelected) C.accent.copy(alpha = 0.5f) else C.border, RoundedCornerShape(12.dp))
            .clickable(onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AvatarCircle(letter = contact.name[0], color = catCol, size = 40)

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(contact.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = C.textPrimary)
                AgentPill(categoryLabel(contact.category), catCol)
            }
            Spacer(Modifier.height(4.dp))
            Text("${contact.relation} · ${contact.lang}", color = C.textSecondary, fontSize = 12.sp)
        }

        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AgentToggle(on = contact.active, onToggle = onToggle, C = C)
            Text(contact.lastSeen, color = C.textMuted, fontSize = 11.sp)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// CONTACT DETAIL PANEL
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ContactDetailPanel(
    contact: UiContact,
    notes: List<UiNote>,
    onToggle: () -> Unit,
    onEdit: () -> Unit = {}, // Added edit
    onAddNote: (String, Boolean, Long?) -> Unit,
    onDeleteNote: (Long) -> Unit,
    onTogglePin: (Long, Boolean) -> Unit,
    onDeleteContact: () -> Unit,
    C: AgentColors,
) {
    var tab by remember(contact.name) { mutableIntStateOf(0) }
    val catCol = categoryColor(contact.category, C)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .background(C.surface)
            .border(1.dp, C.border, RoundedCornerShape(12.dp))
            .padding(20.dp)
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AvatarCircle(contact.name[0], catCol, 44)
            Column(modifier = Modifier.weight(1f)) {
                Text(contact.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = C.textPrimary)
                Text("${contact.relation} · ${contact.lang}", color = C.textMuted, fontSize = 11.sp)
            }
            IconButton(onClick = onEdit) {
                Text("✎", color = C.accent, fontSize = 18.sp)
            }
            AgentToggle(on = contact.active, onToggle = onToggle, C = C)
        }

        Spacer(Modifier.height(14.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
             Text(
                 "Kontakt löschen", 
                 color = C.red, 
                 fontSize = 11.sp, 
                 modifier = Modifier.clickable { onDeleteContact() }
             )
        }

        Spacer(Modifier.height(10.dp))
        AgentDivider(C)
        Spacer(Modifier.height(10.dp))

        // Tabs
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("Info", "Notizen (${notes.size})").forEachIndexed { i, label ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (tab == i) C.accentLow else Color.Transparent)
                        .clickable { tab = i }
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Text(label, color = if (tab == i) C.accent else C.textMuted, fontSize = 12.sp, fontWeight = if (tab == i) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        AgentDivider(C)
        Spacer(Modifier.height(12.dp))

        // Tab content
        if (tab == 0) {
            InfoTab(contact = contact, C = C)
        } else {
            NotesTab(
                notes = notes,
                onAddNote = onAddNote,
                onDeleteNote = onDeleteNote,
                onTogglePin = onTogglePin,
                C = C,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// INFO TAB
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun InfoTab(contact: UiContact, C: AgentColors) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            "Beziehung" to contact.relation,
            "Sprache" to contact.lang,
            "Kategorie" to categoryLabel(contact.category),
            "Antworten gesamt" to contact.replies.toString(),
            "Zuletzt aktiv" to contact.lastSeen,
        ).forEach { (k, v) ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(k, color = C.textMuted, fontSize = 12.sp)
                Text(v, color = C.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }

        Spacer(Modifier.height(4.dp))
        AgentDivider(C)
        Spacer(Modifier.height(4.dp))
        SectionLabel("Persona-Stil", C)
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(C.inputBg)
                .border(1.dp, C.border, RoundedCornerShape(8.dp))
                .padding(10.dp)
        ) {
            Text(contact.style.ifEmpty { "—" }, color = C.textMuted, fontSize = 12.sp, fontFamily = FontFamily.Monospace, lineHeight = 18.sp)
        }
        Spacer(Modifier.height(4.dp))
        AgentDivider(C)
        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (contact.active) "● Agent aktiv" else "● Agent pausiert",
                color = if (contact.active) C.green else C.red,
                fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
            )
            AgentPill(categoryLabel(contact.category), categoryColor(contact.category, C))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// NOTES TAB
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotesTab(
    notes: List<UiNote>,
    onAddNote: (String, Boolean, Long?) -> Unit,
    onDeleteNote: (Long) -> Unit,
    onTogglePin: (Long, Boolean) -> Unit,
    C: AgentColors,
) {
    var showAddNote by remember { mutableStateOf(false) }
    var noteText by remember { mutableStateOf("") }
    var isPinned by remember { mutableStateOf(false) }
    var hasExpiry by remember { mutableStateOf(false) }
    var expiryMillis by remember { mutableStateOf<Long?>(null) }

    Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {

        // Add Note header
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Notizen (${notes.size})", C)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (showAddNote) C.accentLow else Color.Transparent)
                    .border(1.dp, if (showAddNote) C.accent else C.border, RoundedCornerShape(6.dp))
                    .clickable { showAddNote = !showAddNote; if (!showAddNote) { noteText = ""; isPinned = false; hasExpiry = false } }
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
                Text(if (showAddNote) "✕ Abbrechen" else "+ Neu", color = if (showAddNote) C.accent else C.textMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        // Add form
        if (showAddNote) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(C.inputBg)
                    .border(1.dp, C.accent.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AgentTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    placeholder = "Inhalt der Notiz...",
                    C = C, singleLine = false,
                    modifier = Modifier.fillMaxWidth().height(80.dp),
                )
                
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Checkbox(checked = isPinned, onCheckedChange = { isPinned = it })
                        Text("Angepinnt", color = C.textPrimary, fontSize = 11.sp)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Checkbox(checked = hasExpiry, onCheckedChange = { hasExpiry = it })
                        Text("Läuft ab", color = C.textPrimary, fontSize = 11.sp)
                    }
                }

                if (hasExpiry) {
                    NoteDateTimePicker(
                        initialDateTime = expiryMillis,
                        onDateTimeSelected = { expiryMillis = it },
                        C = C
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(C.accent)
                        .clickable {
                            if (noteText.isNotBlank()) {
                                onAddNote(noteText, isPinned, if (hasExpiry) expiryMillis else null)
                                noteText = ""; isPinned = false; hasExpiry = false; expiryMillis = null; showAddNote = false
                            }
                        }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Notiz speichern", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        if (notes.isEmpty() && !showAddNote) {
            Text("Keine Notizen", color = C.textMuted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
        }

        notes.forEach { note -> 
            NoteCard(
                note = note, 
                onDelete = { onDeleteNote(note.id) }, 
                onTogglePin = { onTogglePin(note.id, note.text.startsWith("📌")) },
                C = C
            ) 
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// NOTE CARD
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun NoteCard(note: UiNote, onDelete: () -> Unit, onTogglePin: () -> Unit, C: AgentColors) {
    val isPinned = note.text.startsWith("📌")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(C.inputBg)
            .border(1.dp, if (isPinned) C.accent.copy(alpha = 0.4f) else C.border, RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (isPinned) "Angepinnt" else "Notiz",
                color = if (isPinned) C.accent else C.textMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable(onClick = onTogglePin).padding(4.dp)) {
                    Text(if (isPinned) "📍" else "📌", fontSize = 12.sp)
                }
                Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable(onClick = onDelete).padding(4.dp)) {
                    Text("✕", color = C.textMuted, fontSize = 13.sp)
                }
            }
        }
        Text(note.text, color = C.textSecondary, fontSize = 12.sp, lineHeight = 18.sp)
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Erstellt: ${note.createdAt}", color = C.textMuted, fontSize = 10.sp)
            if (note.expiresAt.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(C.yellow.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("Bis: ${note.expiresAt}", color = C.yellow, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// EMPTY STATE
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun EmptyDetailPanel(onAdd: () -> Unit, C: AgentColors) {
    Column(
        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).background(C.surface).border(1.dp, C.border, RoundedCornerShape(12.dp)),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("◈", color = C.textMuted, fontSize = 28.sp)
        Spacer(Modifier.height(8.dp))
        Text("Kontakt auswählen", color = C.textMuted, fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(C.accentLow)
                .border(1.dp, C.accent.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                .clickable(onClick = onAdd)
                .padding(horizontal = 16.dp, vertical = 7.dp)
        ) {
            Text("+ Neuen Kontakt anlegen", color = C.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ADD CONTACT DIALOG
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun AddContactDialog(
    contactToEdit: UiContact? = null, // Added for editing
    onSave: (UiContact) -> Unit,
    onDismiss: () -> Unit,
    C: AgentColors,
) {
    var name     by remember { mutableStateOf(contactToEdit?.name ?: "") }
    var phoneNumber by remember { mutableStateOf(contactToEdit?.phoneNumber ?: "") }
    var relation by remember { mutableStateOf(contactToEdit?.relation ?: "") }
    var lang     by remember { mutableStateOf(contactToEdit?.lang ?: "Deutsch") }
    var category by remember { mutableStateOf(contactToEdit?.category ?: ContactCategory.UNKNOWN) }
    var style    by remember { mutableStateOf(contactToEdit?.style ?: "") }
    var active   by remember { mutableStateOf(contactToEdit?.active ?: true) }

    val langs = listOf("Deutsch", "Arabisch (Syrischer Dialekt)", "Arabisch (Marokkanischer Dialekt)", "Englisch", "Türkisch")

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(C.surface)
                .border(1.dp, C.borderHigh, RoundedCornerShape(16.dp))
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Header
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column {
                    Text(if (contactToEdit != null) "Kontakt bearbeiten" else "Neuen Kontakt hinzufügen", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = C.textPrimary)
                    Text("Persona & Verhalten festlegen", color = C.textMuted, fontSize = 12.sp)
                }
                Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onDismiss).padding(4.dp)) {
                    Text("✕", color = C.textMuted, fontSize = 18.sp)
                }
            }

            AgentDivider(C)

            // Name
            FormField("WhatsApp Name *", C) {
                AgentTextField(
                    value = name, 
                    onValueChange = { name = it }, 
                    placeholder = "Genau wie in WhatsApp angezeigt", 
                    C = C, 
                    modifier = Modifier.fillMaxWidth(),
                    enabled = contactToEdit == null // Name is PK in backend, shouldn't change easily
                )
            }

            // Phone Number
            FormField("Telefonnummer", C) {
                AgentTextField(value = phoneNumber, onValueChange = { phoneNumber = it }, placeholder = "z.B. +49123456789", C = C, modifier = Modifier.fillMaxWidth())
            }

            // Relation + Category
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FormField("Beziehung", C, modifier = Modifier.weight(1f)) {
                    AgentTextField(value = relation, onValueChange = { relation = it }, placeholder = "z.B. Mutter, Freund, Chef", C = C, modifier = Modifier.fillMaxWidth())
                }
                FormField("Kategorie", C, modifier = Modifier.weight(1f)) {
                    CategorySelector(category, onSelect = { category = it }, C = C)
                }
            }

            // Language
            FormField("Sprache", C) {
                DropdownField(value = lang, options = langs, onSelect = { lang = it }, C = C)
            }

            // Style
            FormField("Persona & Verhaltensstil", C) {
                AgentTextField(
                    value = style, onValueChange = { style = it },
                    placeholder = "z.B. Antworte warm und liebevoll.\nNenn sie 'amy'. Verwende syrischen Dialekt.\nBei Arbeitsfragen: immer ablehnen mit kreativem Grund.",
                    C = C, singleLine = false,
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                )
                Spacer(Modifier.height(3.dp))
                Text("Wird direkt als System-Prompt-Regel verwendet.", color = C.textMuted, fontSize = 10.sp)
            }

            // Active toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(C.inputBg)
                    .border(1.dp, C.border, RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Agent sofort aktivieren", fontSize = 13.sp, color = C.textPrimary, fontWeight = FontWeight.Medium)
                    Text("Automatisch auf Nachrichten antworten", fontSize = 11.sp, color = C.textMuted)
                }
                AgentToggle(on = active, onToggle = { active = !active }, C = C)
            }

            // Buttons
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, C.border, RoundedCornerShape(10.dp))
                        .clickable(onClick = onDismiss)
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Abbrechen", color = C.textSecondary, fontSize = 13.sp)
                }
                val canSave = name.isNotBlank()
                Box(
                    modifier = Modifier
                        .weight(2f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (canSave) C.accent else C.border)
                        .clickable(enabled = canSave) {
                            onSave(UiContact(
                                name = name.trim(), 
                                phoneNumber = phoneNumber.trim(),
                                lang = lang, 
                                relation = relation.trim(),
                                category = category, 
                                style = style.trim(), 
                                active = active,
                                replies = 0, 
                                lastSeen = "neu",
                            ))
                        }
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Kontakt speichern", color = if (canSave) Color.White else C.textMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun FormField(label: String, C: AgentColors, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label.uppercase(), color = C.textMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.7.sp)
        content()
    }
}

@Composable
private fun CategorySelector(selected: ContactCategory, onSelect: (ContactCategory) -> Unit, C: AgentColors) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        ContactCategory.entries.forEach { cat ->
            val col = categoryColor(cat, C)
            val isSelected = cat == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isSelected) col.copy(alpha = 0.15f) else Color.Transparent)
                    .border(1.dp, if (isSelected) col else C.border, RoundedCornerShape(6.dp))
                    .clickable { onSelect(cat) }
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(categoryLabel(cat), color = if (isSelected) col else C.textMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun DropdownField(value: String, options: List<String>, onSelect: (String) -> Unit, C: AgentColors) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(C.inputBg)
                .border(1.dp, C.border, RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(value, color = C.textPrimary, fontSize = 13.sp)
                Text("▾", color = C.textMuted, fontSize = 12.sp)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.background(C.surfaceHigh)) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(opt, color = if (opt == value) C.accent else C.textPrimary, fontSize = 13.sp) },
                    onClick = { onSelect(opt); expanded = false },
                )
            }
        }
    }
}
