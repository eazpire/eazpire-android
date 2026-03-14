package com.eazpire.creator.ui.account.wardrobe

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.core.graphics.PathParser

/**
 * Wardrobe figure – SVG paths from theme/assets/wardrobe-figure.js
 * Variants: male-adult, female-adult, male-child, female-child, male-baby, female-baby
 */
@Composable
fun WardrobeFigureSvg(
    gender: String,
    ageGroup: String,
    slots: Map<String, *>,
    onSlotClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val key = "${gender}-${ageGroup}"
    val paths = remember(key) { FIGURE_PATHS[key] ?: FIGURE_PATHS["male-adult"]!! }
    val viewBoxW = 200f
    val viewBoxH = when (key) {
        "male-adult", "female-adult" -> 248f
        "male-child", "female-child" -> 222f
        else -> 200f
    }

    Canvas(modifier = modifier.pointerInput(paths, onSlotClick) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.type == androidx.compose.ui.input.pointer.PointerEventType.Press) {
                    val pos = event.changes.first().position
                    val scaleX = size.width / viewBoxW
                    val scaleY = size.height / viewBoxH
                    val scale = minOf(scaleX, scaleY)
                    val tx = (size.width - viewBoxW * scale) / 2
                    val ty = (size.height - viewBoxH * scale) / 2
                    val x = (pos.x - tx) / scale
                    val y = (pos.y - ty) / scale
                    for ((slot, pathData) in paths) {
                        if (pathData.isNullOrEmpty()) continue
                        try {
                            val p = PathParser.createPathFromPathData(pathData)
                            val region = android.graphics.Region()
                            region.setPath(p, android.graphics.Region(0, 0, 9999, 9999))
                            if (region.contains(x.toInt(), y.toInt())) {
                                onSlotClick(slot)
                                break
                            }
                        } catch (_: Exception) {}
                    }
                }
            }
        }
    }) {
        val scaleX = size.width / viewBoxW
        val scaleY = size.height / viewBoxH
        val scale = minOf(scaleX, scaleY)
        val tx = (size.width - viewBoxW * scale) / 2
        val ty = (size.height - viewBoxH * scale) / 2

        for ((slot, pathData) in paths) {
            if (pathData.isNullOrEmpty()) continue
            try {
                val androidPath = PathParser.createPathFromPathData(pathData)
                val composePath = androidPath.asComposePath()
                val slotData = slots[slot]
                val isFilled = slotData != null
                val fillColor = if (isFilled) WardrobeColors.FigureFilled else WardrobeColors.FigureDefault
                translate(tx, ty) {
                    scale(scale, scale, pivot = Offset.Zero) {
                        drawPath(composePath, fillColor, alpha = 0.75f)
                        drawPath(composePath, WardrobeColors.FigureDefault, style = Stroke(width = 1.2f), alpha = 0.6f)
                    }
                }
            } catch (_: Exception) {}
        }
    }
}

private val FIGURE_PATHS = mapOf(
    "male-adult" to listOf(
        "head" to "M88 12 C88 4, 112 4, 112 12 L112 32 Q112 44 100 46 Q88 44 88 32 Z",
        "upper_body" to "M60 58 Q70 52 94 54 L106 54 Q130 52 140 58 L140 68 L136 110 Q120 116 100 116 Q80 116 64 110 L60 68 Z",
        "layer" to "M60 58 L52 62 L42 56 L36 62 L38 102 L48 104 L52 100 L60 68 Z M140 58 L148 62 L158 56 L164 62 L162 102 L152 104 L148 100 L140 68 Z",
        "pants" to "M66 118 Q80 116 100 118 Q120 116 134 118 L130 200 L112 200 L108 160 L100 148 L92 160 L88 200 L70 200 Z",
        "socks" to "M70 200 L88 200 L87 214 L69 214 Z M112 200 L130 200 L131 214 L113 214 Z",
        "feet" to "M62 214 L87 214 L88 230 Q88 238 80 238 L58 238 Q52 238 52 232 L54 220 Z M113 214 L138 214 L146 220 L148 232 Q148 238 142 238 L120 238 Q112 238 112 230 Z",
        "accessory_1" to "M34 102 L48 104 L46 118 L32 116 Z",
        "accessory_2" to "M152 104 L166 102 L168 116 L154 118 Z"
    ),
    "female-adult" to listOf(
        "head" to "M86 10 Q86 2 100 2 Q114 2 114 10 L114 33 Q114 45 100 47 Q86 45 86 33 Z",
        "upper_body" to "M66 60 Q76 53 95 55 L105 55 Q124 53 134 60 L132 72 L126 96 Q118 104 100 106 Q82 104 74 96 L68 72 Z",
        "layer" to "M66 60 L56 64 L48 58 L42 64 L44 100 L52 102 L56 96 L66 72 Z M134 60 L144 64 L152 58 L158 64 L156 100 L148 102 L144 96 L134 72 Z",
        "pants" to "M72 108 Q82 104 100 108 Q118 104 128 108 L132 120 L128 200 L110 200 L106 162 L100 150 L94 162 L90 200 L72 200 L68 120 Z",
        "socks" to "M72 200 L90 200 L89 214 L71 214 Z M110 200 L128 200 L129 214 L111 214 Z",
        "feet" to "M64 214 L89 214 L90 230 Q90 238 82 238 L60 238 Q54 238 54 232 L56 220 Z M111 214 L136 214 L144 220 L146 232 Q146 238 140 238 L118 238 Q110 238 110 230 Z",
        "accessory_1" to "M40 100 L52 102 L50 116 L38 114 Z",
        "accessory_2" to "M148 102 L160 100 L162 114 L150 116 Z"
    ),
    "male-child" to listOf(
        "head" to "M82 14 C82 4, 118 4, 118 14 L118 40 Q118 54 100 56 Q82 54 82 40 Z",
        "upper_body" to "M64 66 Q74 60 94 62 L106 62 Q126 60 136 66 L134 108 Q120 114 100 114 Q80 114 66 108 Z",
        "layer" to "M64 66 L54 70 L46 64 L40 70 L42 104 L50 106 L54 100 L64 78 Z M136 66 L146 70 L154 64 L160 70 L158 104 L150 106 L146 100 L136 78 Z",
        "pants" to "M68 116 Q80 114 100 116 Q120 114 132 116 L128 182 L112 182 L108 152 L100 142 L92 152 L88 182 L72 182 Z",
        "socks" to "M72 182 L88 182 L87 194 L71 194 Z M112 182 L128 182 L129 194 L113 194 Z",
        "feet" to "M64 194 L87 194 L88 208 Q88 214 80 214 L60 214 Q54 214 54 208 Z M113 194 L136 194 L146 208 Q146 214 140 214 L120 214 Q112 214 112 208 Z",
        "accessory_1" to "M38 104 L50 106 L48 118 L36 116 Z",
        "accessory_2" to "M150 106 L162 104 L164 116 L152 118 Z"
    ),
    "female-child" to listOf(
        "head" to "M82 12 C82 2, 118 2, 118 12 L118 38 Q118 52 100 54 Q82 52 82 38 Z",
        "upper_body" to "M66 64 Q76 58 94 60 L106 60 Q124 58 134 64 L132 104 Q118 112 100 112 Q82 112 68 104 Z",
        "layer" to "M66 64 L56 68 L48 62 L42 68 L44 100 L52 102 L56 96 L66 74 Z M134 64 L144 68 L152 62 L158 68 L156 100 L148 102 L144 96 L134 74 Z",
        "pants" to "M70 114 Q82 112 100 114 Q118 112 130 114 L132 124 L128 180 L112 180 L108 150 L100 140 L92 150 L88 180 L72 180 L68 124 Z",
        "socks" to "M72 180 L88 180 L87 192 L71 192 Z M112 180 L128 180 L129 192 L113 192 Z",
        "feet" to "M64 192 L87 192 L88 206 Q88 212 80 212 L60 212 Q54 212 54 206 Z M113 192 L136 192 L146 206 Q146 212 140 212 L120 212 Q112 212 112 206 Z",
        "accessory_1" to "M40 100 L52 102 L50 114 L38 112 Z",
        "accessory_2" to "M148 102 L160 100 L162 112 L150 114 Z"
    ),
    "male-baby" to listOf(
        "head" to "M74 16 C74 2, 126 2, 126 16 L126 50 Q126 66 100 68 Q74 66 74 50 Z",
        "upper_body" to "M62 78 Q72 72 92 74 L108 74 Q128 72 138 78 L136 118 Q122 126 100 126 Q78 126 64 118 Z",
        "layer" to "M62 78 L52 82 L46 76 L40 82 L42 112 L50 114 L54 108 L62 90 Z M138 78 L148 82 L154 76 L160 82 L158 112 L150 114 L146 108 L138 90 Z",
        "pants" to "M66 128 Q78 126 100 128 Q122 126 134 128 L130 162 L114 162 L108 148 L100 140 L92 148 L86 162 L70 162 Z",
        "socks" to "M70 162 L86 162 L85 172 L69 172 Z M114 162 L130 162 L131 172 L115 172 Z",
        "feet" to "M62 172 L85 172 L86 184 Q86 190 78 190 L58 190 Q52 190 52 184 Z M115 172 L138 172 L146 184 Q146 190 140 190 L122 190 Q114 190 114 184 Z",
        "accessory_1" to "M38 112 L50 114 L48 126 L36 124 Z",
        "accessory_2" to "M150 114 L162 112 L164 124 L152 126 Z"
    ),
    "female-baby" to listOf(
        "head" to "M74 14 C74 0, 126 0, 126 14 L126 48 Q126 64 100 66 Q74 64 74 48 Z",
        "upper_body" to "M64 76 Q74 70 92 72 L108 72 Q126 70 136 76 L134 116 Q120 124 100 124 Q80 124 66 116 Z",
        "layer" to "M64 76 L54 80 L48 74 L42 80 L44 110 L52 112 L56 106 L64 88 Z M136 76 L146 80 L152 74 L158 80 L156 110 L148 112 L144 106 L136 88 Z",
        "pants" to "M68 126 Q80 124 100 126 Q120 124 132 126 L130 160 L114 160 L108 146 L100 138 L92 146 L86 160 L70 160 Z",
        "socks" to "M70 160 L86 160 L85 170 L69 170 Z M114 160 L130 160 L131 170 L115 170 Z",
        "feet" to "M62 170 L85 170 L86 182 Q86 188 78 188 L58 188 Q52 188 52 182 Z M115 170 L138 170 L146 182 Q146 188 140 188 L122 188 Q114 188 114 182 Z",
        "accessory_1" to "M40 110 L52 112 L50 124 L38 122 Z",
        "accessory_2" to "M148 112 L160 110 L162 122 L150 124 Z"
    )
).mapValues { (_, list) -> list.toMap() }
