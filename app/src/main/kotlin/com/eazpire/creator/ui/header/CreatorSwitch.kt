package com.eazpire.creator.ui.header

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FrontHand
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eazpire.creator.EazColors
import com.eazpire.creator.EazpireCreatorTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    val scope = rememberCoroutineScope()
    val trackWidth = 126.dp
    val trackHeight = 34.dp
    val padding = 2.dp
    val thumbWidth = (trackWidth - padding * 2) / 2 - 2.dp
    val shopThumbOffset = padding
    val creatorThumbOffset = thumbWidth + padding + 1.dp
    val thumbOffsetTarget: Dp = if (isCreatorMode) creatorThumbOffset else shopThumbOffset

    var isTutorialPlaying by remember { mutableStateOf(false) }
    var triggerTutorial by remember { mutableStateOf(0) }
    var trackSize by remember { mutableStateOf(IntSize.Zero) }
    var isDragging by remember { mutableStateOf(false) }
    var dragPositionPx by remember { mutableStateOf(0f) }
    val thumbOffsetPx = remember { Animatable(with(density) { thumbOffsetTarget.toPx() }) }

    val shopPx = with(density) { shopThumbOffset.toPx() }
    val creatorPx = with(density) { creatorThumbOffset.toPx() }
    val minPx = shopPx
    val maxPx = creatorPx

    LaunchedEffect(thumbOffsetTarget, isTutorialPlaying, isDragging) {
        if (!isTutorialPlaying && !isDragging) {
            thumbOffsetPx.snapTo(with(density) { thumbOffsetTarget.toPx() })
        }
    }

    // Klick: Tutorial-Animation (1.2s wie Shop – Hand schiebt Thumb, springt zurück)
    LaunchedEffect(triggerTutorial) {
        if (triggerTutorial == 0) return@LaunchedEffect
        if (isTutorialPlaying) return@LaunchedEffect
        isTutorialPlaying = true
        val direction = if (isCreatorMode) "left" else "right"
        val targetPx = if (direction == "right") creatorPx else shopPx
        val duration = 1200
        // Shop-Timing: 0–20% hold, 20–55% move, 55–70% hold, 70–100% snap back
        val startPx = with(density) { thumbOffsetTarget.toPx() }
        thumbOffsetPx.animateTo(
            targetValue = startPx,
            animationSpec = keyframes {
                durationMillis = duration
                startPx at 0
                startPx at (duration * 0.2f).toInt()
                targetPx at (duration * 0.55f).toInt()
                targetPx at (duration * 0.7f).toInt()
                startPx at duration
            }
        )
        isTutorialPlaying = false
    }

    Box(
        modifier = modifier
            .width(trackWidth)
            .height(trackHeight)
            .onSizeChanged { trackSize = it }
            .clip(RoundedCornerShape(percent = 50))
            .background(if (isCreatorMode) TrackBgCreator else TrackBgLight)
            .border(1.dp, if (isCreatorMode) TrackBorderCreator else TrackBorderLight, RoundedCornerShape(percent = 50))
            .padding(padding)
            .pointerInput(Unit) {
                var totalDrag = 0f
                var startOffsetPx = 0f
                val thresholdPx = with(density) { 30.dp.toPx() }
                detectHorizontalDragGestures(
                    onDragStart = {
                        totalDrag = 0f
                        isDragging = true
                        startOffsetPx = thumbOffsetPx.value
                        dragPositionPx = startOffsetPx
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        totalDrag += dragAmount
                        dragPositionPx = (startOffsetPx + totalDrag).coerceIn(minPx, maxPx)
                    },
                    onDragEnd = {
                        isDragging = false
                        when {
                            totalDrag < -thresholdPx -> {
                                scope.launch {
                                    thumbOffsetPx.animateTo(shopPx, tween(250, easing = FastOutSlowInEasing))
                                }
                                onModeChange(false)
                            }
                            totalDrag > thresholdPx -> {
                                scope.launch {
                                    thumbOffsetPx.animateTo(creatorPx, tween(250, easing = FastOutSlowInEasing))
                                }
                                onModeChange(true)
                            }
                            else -> triggerTutorial++
                        }
                    }
                )
            }
    ) {
        val handOffsetPx = remember { Animatable(0f) }
        val handOpacity = remember { Animatable(0f) }

        // Sliding orange thumb (folgt Finger beim Swipe)
        val displayPx = if (isDragging) dragPositionPx else thumbOffsetPx.value
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(thumbWidth)
                .offset(x = with(density) { displayPx.toDp() })
                .align(Alignment.CenterStart)
                .zIndex(0f)
                .shadow(4.dp, RoundedCornerShape(percent = 50), spotColor = Color(0x4DF97316))
                .clip(RoundedCornerShape(percent = 50))
                .background(EazColors.Orange)
        )
        // Tutorial-Hand (wie Shop: 20%→55% oder 55%→20%, 1.2s ease-in-out)
        if (isTutorialPlaying) {
            LaunchedEffect(isTutorialPlaying) {
                val trackW = trackSize.width.toFloat().coerceAtLeast(1f)
                val handStart = trackW * 0.2f
                val handEnd = trackW * 0.55f
                val pushRight = !isCreatorMode
                handOffsetPx.snapTo(if (pushRight) handStart else handEnd)
                handOpacity.snapTo(0f)
                handOpacity.animateTo(1f, tween(180))
                handOffsetPx.animateTo(if (pushRight) handEnd else handStart, tween(420, easing = FastOutSlowInEasing))
                delay(180)
                handOpacity.animateTo(0f, tween(180))
            }
            Icon(
                imageVector = Icons.Outlined.FrontHand,
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = with(density) { handOffsetPx.value.toDp() })
                    .size(24.dp)
                    .graphicsLayer {
                        scaleX = if (isCreatorMode) -1f else 1f
                        alpha = handOpacity.value
                    }
                    .zIndex(10f),
                tint = if (isCreatorMode) Color.White.copy(alpha = 0.9f) else LabelInactive
            )
        }
        // Labels
        Row(
            modifier = Modifier.fillMaxHeight().zIndex(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                Text("Shop", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if (isCreatorMode) LabelInactive else Color.White)
            }
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                Text("Creator", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if (isCreatorMode) Color.White else LabelInactive)
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
