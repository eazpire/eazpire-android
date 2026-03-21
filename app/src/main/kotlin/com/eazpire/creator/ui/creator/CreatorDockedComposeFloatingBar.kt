package com.eazpire.creator.ui.creator

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.eazpire.creator.chat.EazyMascotIcon
import com.eazpire.creator.i18n.TranslationStore
import kotlin.math.roundToInt

/**
 * Docked Eazy stays in header; this row is the bottom “Generate” strip (Eazy left, bubble right, tail toward Eazy).
 * Draggable; offset resets after generation ends (loading true → false).
 */
@Composable
fun CreatorDockedComposeFloatingBar(
    visible: Boolean,
    loading: Boolean,
    onStart: () -> Unit,
    translationStore: TranslationStore,
    modifier: Modifier = Modifier
) {
    if (!visible) return
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var prevLoading by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (!visible) {
            offsetX = 0f
            offsetY = 0f
        }
    }
    LaunchedEffect(loading) {
        if (prevLoading && !loading) {
            offsetX = 0f
            offsetY = 0f
        }
        prevLoading = loading
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(end = 8.dp, bottom = 4.dp)
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .pointerInput(visible) {
                detectDragGestures { change, drag ->
                    change.consume()
                    offsetX += drag.x
                    offsetY += drag.y
                }
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End
    ) {
        EazyMascotIcon(
            modifier = Modifier.size(48.dp),
            lookLeft = false
        )
        Spacer(modifier = Modifier.width(10.dp))
        CreatorHeaderEazyStartBubble(
            label = translationStore.t("creator.generator_eazy.bubble_start", "Start generation"),
            loading = loading,
            enabled = !loading,
            onClick = onStart,
            tailTowardEnd = true
        )
    }
}
