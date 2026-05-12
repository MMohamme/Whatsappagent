package com.example.whatsappagent.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.whatsappagent.data.MessageEntity
import com.example.whatsappagent.ui.components.*
import com.example.whatsappagent.ui.theme.AgentColors
import java.text.SimpleDateFormat
import java.util.*

/**
 * New ChatScreen that uses real Room data instead of hardcoded dummy data
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreenNew(
    messages: List<MessageEntity>,
    senders: List<String>,
    selectedSender: String?,
    onSelectSender: (String) -> Unit,
    onClearSelection: () -> Unit,
    C: AgentColors
) {
    val listState = rememberLazyListState()
    
    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isMobile = maxWidth < 700.dp
        val padding = if (maxWidth < 600.dp) 0.dp else 20.dp
        
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (isMobile && selectedSender != null) {
                // Mobile Detail View
                ChatArea(
                    messages = messages,
                    selectedSender = selectedSender,
                    onClearSelection = onClearSelection,
                    listState = listState,
                    C = C,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Sender list (left side or full width on mobile)
                    SenderList(
                        senders = senders,
                        selectedSender = selectedSender,
                        onSelectSender = onSelectSender,
                        C = C,
                        modifier = if (isMobile) Modifier.fillMaxSize() else Modifier.width(240.dp).fillMaxHeight()
                    )

                    // Chat area (right side, only on tablet)
                    if (!isMobile) {
                        ChatArea(
                            messages = messages,
                            selectedSender = selectedSender,
                            onClearSelection = onClearSelection,
                            listState = listState,
                            C = C,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SenderList(
    senders: List<String>,
    selectedSender: String?,
    onSelectSender: (String) -> Unit,
    C: AgentColors,
    modifier: Modifier = Modifier
) {
    AgentCard(
        C = C,
        modifier = modifier,
        padding = 12
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SectionLabel("Konversationen", C)
            
            if (senders.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Keine Chats", color = C.textMuted, fontSize = 12.sp)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(senders) { sender ->
                        SenderItem(
                            sender = sender,
                            isSelected = sender == selectedSender,
                            onClick = { onSelectSender(sender) },
                            C = C
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatArea(
    messages: List<MessageEntity>,
    selectedSender: String?,
    onClearSelection: () -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState,
    C: AgentColors,
    modifier: Modifier = Modifier
) {
    AgentCard(
        C = C,
        modifier = modifier,
        padding = 0
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            if (selectedSender != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(C.surfaceHigh)
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AvatarCircle(
                        letter = selectedSender.firstOrNull() ?: '?',
                        color = C.accent,
                        size = 36
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = selectedSender,
                            color = C.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Agent aktiv · via RemoteInput",
                            color = C.green,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    
                    AgentBadge("KI", C.green)
                    
                    IconButton(onClick = onClearSelection, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = C.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                AgentDivider(C)
            }

            // Messages
            if (selectedSender != null) {
                if (messages.isEmpty()) {
                    EmptyState(
                        title = "Keine Nachrichten",
                        description = "Bisher wurden keine Nachrichten mit diesem Kontakt ausgetauscht.",
                        icon = "💬",
                        C = C,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(20.dp)
                    ) {
                        items(messages) { message ->
                            MessageBubble(message, C)
                        }
                    }
                }
            } else {
                EmptyState(
                    title = "Chat auswählen",
                    description = "Wähle einen Kontakt aus der Liste links, um den Gesprächsverlauf zu sehen.",
                    icon = "📱",
                    C = C,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun SenderItem(
    sender: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    C: AgentColors
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) C.accentLow else Color.Transparent)
            .border(1.dp, if (isSelected) C.accent.copy(alpha = 0.5f) else Color.Transparent, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        AvatarCircle(
            letter = sender.firstOrNull() ?: '?',
            color = if (isSelected) C.accent else C.textSecondary,
            size = 32
        )
        Column {
            Text(
                text = sender,
                color = if (isSelected) C.accent else C.textPrimary,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                fontSize = 13.sp,
                maxLines = 1
            )
            Text(
                text = "Online",
                color = if (isSelected) C.accent.copy(alpha = 0.7f) else C.textMuted,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun MessageBubble(message: MessageEntity, C: AgentColors) {
    val isAgent = message.role == "assistant"
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isAgent) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 12.dp, topEnd = 12.dp,
                        bottomEnd = if (isAgent) 3.dp else 12.dp,
                        bottomStart = if (!isAgent) 3.dp else 12.dp,
                    )
                )
                .background(if (isAgent) C.accentLow else C.inputBg)
                .border(
                    1.dp,
                    if (isAgent) C.accent.copy(alpha = 0.2f) else C.border,
                    RoundedCornerShape(
                        topStart = 12.dp, topEnd = 12.dp,
                        bottomEnd = if (isAgent) 3.dp else 12.dp,
                        bottomStart = if (!isAgent) 3.dp else 12.dp,
                    )
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = if (isAgent) Alignment.End else Alignment.Start
        ) {
            Text(
                text = message.text,
                color = C.textPrimary,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = if (isAgent) androidx.compose.ui.text.style.TextAlign.End else androidx.compose.ui.text.style.TextAlign.Start,
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = formatMessageTime(message.timestamp) + if (isAgent) " · Agent" else "",
                color = C.textSecondary,
                fontSize = 11.sp,
                textAlign = if (isAgent) androidx.compose.ui.text.style.TextAlign.End else androidx.compose.ui.text.style.TextAlign.Start,
            )
        }
    }
}

private fun formatMessageTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
