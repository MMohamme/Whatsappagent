package com.example.whatsappagent.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.whatsappagent.ui.model.ContactCategory
import com.example.whatsappagent.ui.theme.AgentColors

// ─────────────────────────────────────────────────────────────────────────────
// TOGGLE
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun AgentToggle(on: Boolean, onToggle: () -> Unit, C: AgentColors) {
    val thumbOffset by animateDpAsState(if (on) 18.dp else 3.dp, label = "toggle")
    Box(
        modifier = Modifier
            .size(width = 36.dp, height = 20.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (on) C.accent else C.border)
            .clickable { onToggle() }
    ) {
        Box(
            modifier = Modifier
                .padding(start = thumbOffset, top = 3.dp)
                .size(14.dp)
                .clip(CircleShape)
                .background(if (on) Color.White else C.textSecondary)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PILL BADGE
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun AgentPill(label: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// STATUS BADGE (rounded rectangle)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun AgentBadge(label: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(label.uppercase(), color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SECTION LABEL
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun SectionLabel(text: String, C: AgentColors) {
    Text(text.uppercase(), color = C.textMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp)
}

// ─────────────────────────────────────────────────────────────────────────────
// SURFACE CARD
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun AgentCard(
    C: AgentColors,
    modifier: Modifier = Modifier,
    borderColor: Color? = null,
    backgroundColor: Color? = null,
    padding: Int = 16,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor ?: C.surface)
            .border(1.dp, borderColor ?: C.border, RoundedCornerShape(16.dp))
            .padding(padding.dp),
        content = content,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// AVATAR CIRCLE
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun AvatarCircle(letter: Char, color: Color, size: Int = 36) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.3f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(letter.toString(), color = color, fontSize = (size / 2.5).sp, fontWeight = FontWeight.Bold)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// INPUT FIELD
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun AgentTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    C: AgentColors,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    val borderColor by animateColorAsState(
        targetValue = when {
            !enabled -> C.border.copy(alpha = 0.5f)
            isFocused -> C.accent
            else -> C.border
        },
        animationSpec = tween(durationMillis = 200),
        label = "border_color"
    )

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        enabled = enabled,
        interactionSource = interactionSource,
        textStyle = TextStyle(
            color = if (enabled) C.textPrimary else C.textMuted,
            fontSize = 14.sp
        ),
        cursorBrush = SolidColor(C.accent),
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) C.inputBg else C.surfaceHigh)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        decorationBox = { inner ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (leadingIcon != null) {
                    leadingIcon()
                    Spacer(Modifier.width(8.dp))
                }
                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(placeholder, color = C.textMuted, fontSize = 14.sp)
                    inner()
                }
            }
        }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// EMPTY STATE
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun EmptyState(
    title: String,
    description: String,
    icon: String, // Emoji or simple icon string
    C: AgentColors,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = icon,
            fontSize = 48.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Text(
            text = title,
            color = C.textPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = description,
            color = C.textSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// STAT CARD (Dashboard)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun StatCard(label: String, value: String, color: Color, icon: String, C: AgentColors, modifier: Modifier = Modifier) {
    AgentCard(C = C, modifier = modifier, padding = 12) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label.uppercase(), color = C.textSecondary, fontSize = 10.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
            Text(icon, color = color.copy(alpha = 0.5f), fontSize = 16.sp)
        }
        Spacer(Modifier.height(12.dp))
        Text(value, color = color, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DIVIDER
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun AgentDivider(C: AgentColors) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(C.border)
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// CATEGORY COLOR helper
// ─────────────────────────────────────────────────────────────────────────────
fun categoryColor(cat: ContactCategory, C: AgentColors) = when (cat) {
    ContactCategory.CORE_FAMILY     -> C.purple
    ContactCategory.EXTENDED_FAMILY -> C.blue
    ContactCategory.WORK           -> C.yellow
    ContactCategory.FRIEND         -> C.accent
    ContactCategory.UNKNOWN        -> C.textMuted
}

fun categoryLabel(cat: ContactCategory) = when (cat) {
    ContactCategory.CORE_FAMILY     -> "Kernfamilie"
    ContactCategory.EXTENDED_FAMILY -> "Verwandte"
    ContactCategory.WORK           -> "Arbeit"
    ContactCategory.FRIEND         -> "Freunde"
    ContactCategory.UNKNOWN        -> "Unbekannt"
}
