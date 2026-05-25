package com.eazpire.creator.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eazpire.creator.EazColors

/** −1 exclude, 0 neutral, 1 include */
typealias FacetTriState = Int

fun clampFacetTriState(value: Int): FacetTriState = when (value) {
    -1, 1 -> value
    else -> 0
}

@Composable
fun FacetTriSwitchRow(
    label: String,
    count: Int?,
    state: FacetTriState,
    onStateChange: (FacetTriState) -> Unit,
    modifier: Modifier = Modifier
) {
    val st = clampFacetTriState(state)
    val labelColor = when (st) {
        -1 -> Color(0xFFDC2626)
        1 -> Color(0xFF16A34A)
        else -> EazColors.TextPrimary
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = labelColor,
            fontWeight = if (st == 1) FontWeight.SemiBold else FontWeight.Normal,
            textDecoration = if (st == -1) TextDecoration.LineThrough else null,
            style = MaterialTheme.typography.bodyMedium
        )
        if (count != null) {
            Text(
                text = "($count)",
                color = EazColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(end = 8.dp)
            )
        }
        FacetTriSwitch(state = st, onStateChange = onStateChange)
    }
}

@Composable
fun FacetTriSwitch(
    state: FacetTriState,
    onStateChange: (FacetTriState) -> Unit,
    modifier: Modifier = Modifier
) {
    val st = clampFacetTriState(state)
    val trackTint = if (st == -1 || st == 1) Color(0xFFFFF7ED) else Color.White
    val trackBorder = if (st == -1 || st == 1) Color(0xFFFED7AA) else Color(0xFFE5E7EB)
    val thumbColor = when (st) {
        -1 -> Color(0xFFDC2626)
        1 -> Color(0xFF16A34A)
        else -> EazColors.Orange
    }
    val thumbOffset = when (st) {
        -1 -> 0.dp
        1 -> 50.dp
        else -> 25.dp
    }
    Box(
        modifier = modifier
            .width(76.dp)
            .height(26.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(trackTint)
            .border(1.dp, trackBorder, RoundedCornerShape(13.dp))
    ) {
        Box(
            modifier = Modifier
                .padding(2.dp)
                .offset(x = thumbOffset)
                .width(22.dp)
                .height(22.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(thumbColor)
        )
        Row(modifier = Modifier.matchParentSize()) {
            TriSwitchSegment("-", st == -1, Modifier.weight(1f)) { onStateChange(-1) }
            TriSwitchSegment("·", st == 0, Modifier.weight(1f), isDot = true) { onStateChange(0) }
            TriSwitchSegment("+", st == 1, Modifier.weight(1f)) { onStateChange(1) }
        }
    }
}

@Composable
private fun TriSwitchSegment(
    glyph: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    isDot: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(26.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isDot) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(if (active) Color.White else Color(0xFF6B7280))
            )
        } else {
            Text(
                text = glyph,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (active) Color.White else Color(0xFF6B7280)
            )
        }
    }
}
