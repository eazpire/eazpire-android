package com.eazpire.creator.ui.header

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eazpire.creator.EazColors
import com.eazpire.creator.EazpireCreatorTheme

/** Shop-Farben für Creator Switch (exakt wie theme/eaz-redesign-base.css) */
private val TrackBgLight = Color(0xFFF3F4F6)
private val TrackBorderLight = Color(0xFFE5E7EB)
private val TrackBgCreator = Color(0xFF020617)
private val TrackBorderCreator = Color(0xFF0B1120)
private val LabelInactive = Color(0xFF4B5563)

@Composable
fun CreatorSwitch(
    isCreatorMode: Boolean,
    onModeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val trackWidth = 126.dp
    val trackHeight = 34.dp
    val padding = 2.dp
    val thumbWidth = (trackWidth - padding * 2) / 2 - 2.dp
    val thumbOffsetTarget = if (isCreatorMode) thumbWidth + padding + 1.dp else padding
    val thumbOffset by animateDpAsState(thumbOffsetTarget, animationSpec = tween(250))

    Box(
        modifier = modifier
            .width(trackWidth)
            .height(trackHeight)
            .clip(RoundedCornerShape(percent = 50))
            .background(if (isCreatorMode) TrackBgCreator else TrackBgLight)
            .border(1.dp, if (isCreatorMode) TrackBorderCreator else TrackBorderLight, RoundedCornerShape(percent = 50))
            .padding(padding)
            .pointerInput(Unit) {
                var totalDrag = 0f
                val thresholdPx = with(density) { 30.dp.toPx() }
                detectHorizontalDragGestures(
                    onDragStart = { totalDrag = 0f },
                    onHorizontalDrag = { _, dragAmount -> totalDrag += dragAmount },
                    onDragEnd = {
                        when {
                            totalDrag < -thresholdPx -> onModeChange(true)   // Swipe links → Creator
                            totalDrag > thresholdPx -> onModeChange(false)  // Swipe rechts → Shop
                            else -> { /* Tap wird von Segment-Clickables behandelt */ }
                        }
                    }
                )
            }
    ) {
        // Sliding orange thumb (hinten, wie Shop: box-shadow 0 2px 8px rgba(249,115,22,0.3))
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(thumbWidth)
                .offset(x = thumbOffset)
                .align(Alignment.CenterStart)
                .zIndex(0f)
                .shadow(4.dp, RoundedCornerShape(percent = 50), spotColor = Color(0x4DF97316))
                .clip(RoundedCornerShape(percent = 50))
                .background(EazColors.Orange)
        )
        // Klickbare Labels (vorne, transparent)
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .zIndex(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { if (isCreatorMode) onModeChange(false) }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Shop",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isCreatorMode) LabelInactive else Color.White
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { if (!isCreatorMode) onModeChange(true) }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Creator",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isCreatorMode) Color.White else LabelInactive
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF5F5F5)
@Composable
private fun CreatorSwitchPreviewShop() {
    EazpireCreatorTheme {
        Box(
            modifier = Modifier
                .padding(24.dp)
                .background(Color(0xFFF5F5F5))
        ) {
            CreatorSwitch(isCreatorMode = false, onModeChange = {})
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF5F5F5)
@Composable
private fun CreatorSwitchPreviewCreator() {
    EazpireCreatorTheme {
        Box(
            modifier = Modifier
                .padding(24.dp)
                .background(Color(0xFFF5F5F5))
        ) {
            CreatorSwitch(isCreatorMode = true, onModeChange = {})
        }
    }
}
