package com.eazpire.creator.chat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

private data class FigureZone(val slot: String, val pathData: String)

/** Male-adult silhouette zones (from wardrobe-figure.js). */
private val MALE_ADULT_ZONES = listOf(
    FigureZone("head", "M88 12 C88 4, 112 4, 112 12 L112 32 Q112 44 100 46 Q88 44 88 32 Z"),
    FigureZone(
        "upper_body",
        "M60 58 Q70 52 94 54 L106 54 Q130 52 140 58 L140 68 L136 110 Q120 116 100 116 Q80 116 64 110 L60 68 Z",
    ),
    FigureZone(
        "layer",
        "M60 58 L52 62 L42 56 L36 62 L38 102 L48 104 L52 100 L60 68 Z M140 58 L148 62 L158 56 L164 62 L162 102 L152 104 L148 100 L140 68 Z",
    ),
    FigureZone(
        "pants",
        "M66 118 Q80 116 100 118 Q120 116 134 118 L130 200 L112 200 L108 160 L100 148 L92 160 L88 200 L70 200 Z",
    ),
    FigureZone("socks", "M70 200 L88 200 L87 214 L69 214 Z M112 200 L130 200 L131 214 L113 214 Z"),
    FigureZone(
        "feet",
        "M62 214 L87 214 L88 230 Q88 238 80 238 L58 238 Q52 238 52 232 L54 220 Z M113 214 L138 214 L146 220 L148 232 Q148 238 142 238 L120 238 Q112 238 112 230 Z",
    ),
    FigureZone("accessory_1", "M34 102 L48 104 L46 118 L32 116 Z"),
    FigureZone("accessory_2", "M152 104 L166 102 L168 116 L154 118 Z"),
)

private const val VIEW_W = 200f
private const val VIEW_H = 248f

@Composable
fun EazyArtifactsFigure(
    filledSlots: Set<String>,
    selectedSlot: String?,
    onSlotClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val zones = remember {
        MALE_ADULT_ZONES.map { zone ->
            zone to PathParser().parsePathString(zone.pathData).toPath()
        }
    }
    val accent = Color(0xFFF97316)
    val filledColor = Color(0xFF818CF8)
    val defaultColor = Color(0xFFDDE3ED)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(248.dp)
            .pointerInput(zones, filledSlots, selectedSlot) {
                detectTapGestures { tap ->
                    val scale = minOf(size.width / VIEW_W, size.height / VIEW_H)
                    val dx = (size.width - VIEW_W * scale) / 2f
                    val dy = (size.height - VIEW_H * scale) / 2f
                    val local = Offset((tap.x - dx) / scale, (tap.y - dy) / scale)
                    zones.asReversed().forEach { (zone, path) ->
                        if (path.getBounds().contains(local)) {
                            onSlotClick(zone.slot)
                            return@detectTapGestures
                        }
                    }
                }
            },
    ) {
        val scale = minOf(size.width / VIEW_W, size.height / VIEW_H)
        val dx = (size.width - VIEW_W * scale) / 2f
        val dy = (size.height - VIEW_H * scale) / 2f
        withTransform({
            translate(dx, dy)
            scale(scale, scale)
        }) {
            zones.forEach { (zone, path) ->
                val filled = zone.slot in filledSlots
                val selected = zone.slot == selectedSlot
                drawPath(
                    path = path,
                    color = when {
                        selected -> accent
                        filled -> filledColor
                        else -> defaultColor
                    },
                    alpha = if (filled || selected) 0.85f else 0.55f,
                )
                drawPath(
                    path = path,
                    color = Color.White.copy(alpha = 0.35f),
                    style = Stroke(width = 1.2f / scale),
                )
            }
        }
    }
}
