package com.eazpire.creator.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.PathParser

/**
 * Tabler outline icons (MIT) — paths from theme/snippets/eaz-na-tabler-icon.liquid
 */
private val TABLER_ICON_PATHS: Map<String, List<String>> = mapOf(
    "all" to listOf(
        "M4 5a1 1 0 0 1 1 -1h4a1 1 0 0 1 1 1v4a1 1 0 0 1 -1 1h-4a1 1 0 0 1 -1 -1l0 -4",
        "M14 5a1 1 0 0 1 1 -1h4a1 1 0 0 1 1 1v4a1 1 0 0 1 -1 1h-4a1 1 0 0 1 -1 -1l0 -4",
        "M4 15a1 1 0 0 1 1 -1h4a1 1 0 0 1 1 1v4a1 1 0 0 1 -1 1h-4a1 1 0 0 1 -1 -1l0 -4",
        "M14 15a1 1 0 0 1 1 -1h4a1 1 0 0 1 1 1v4a1 1 0 0 1 -1 1h-4a1 1 0 0 1 -1 -1l0 -4",
    ),
    "women" to listOf(
        "M10 16v5",
        "M14 16v5",
        "M8 16h8l-2 -7h-4l-2 7",
        "M5 11c1.667 -1.333 3.333 -2 5 -2",
        "M19 11c-1.667 -1.333 -3.333 -2 -5 -2",
        "M10 4a2 2 0 1 0 4 0a2 2 0 1 0 -4 0",
    ),
    "men" to listOf(
        "M10 16v5",
        "M14 16v5",
        "M9 9h6l-1 7h-4l-1 -7",
        "M5 11c1.333 -1.333 2.667 -2 4 -2",
        "M19 11c-1.333 -1.333 -2.667 -2 -4 -2",
        "M10 4a2 2 0 1 0 4 0a2 2 0 1 0 -4 0",
    ),
    "kids" to listOf(
        "M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0",
        "M9 10l.01 0",
        "M15 10l.01 0",
        "M9.5 15a3.5 3.5 0 0 0 5 0",
        "M12 3a2 2 0 0 0 0 4",
    ),
    "toddler" to listOf(
        "M6 19a2 2 0 1 0 4 0a2 2 0 1 0 -4 0",
        "M16 19a2 2 0 1 0 4 0a2 2 0 1 0 -4 0",
        "M2 5h2.5l1.632 4.897a6 6 0 0 0 5.693 4.103h2.675a5.5 5.5 0 0 0 0 -11h-.5v6",
        "M6 9h14",
        "M9 17l1 -3",
        "M16 14l1 3",
    ),
    "accessories" to listOf(
        "M6.331 8h11.339a2 2 0 0 1 1.977 2.304l-1.255 8.152a3 3 0 0 1 -2.966 2.544h-6.852a3 3 0 0 1 -2.965 -2.544l-1.255 -8.152a2 2 0 0 1 1.977 -2.304",
        "M9 11v-5a3 3 0 0 1 6 0v5",
    ),
    "home-living" to listOf(
        "M5 12l-2 0l9 -9l9 9l-2 0",
        "M5 12v7a2 2 0 0 0 2 2h10a2 2 0 0 0 2 -2v-7",
        "M9 21v-6a2 2 0 0 1 2 -2h2a2 2 0 0 1 2 2v6",
    ),
)

@Composable
fun HomeCategoryTablerIcon(
    categoryId: String,
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFFF97316),
    iconSize: Dp = 22.dp,
) {
    val paths = remember(categoryId) { TABLER_ICON_PATHS[categoryId] ?: TABLER_ICON_PATHS["all"].orEmpty() }
    val composePaths = remember(paths) {
        paths.mapNotNull { pathData ->
            runCatching {
                PathParser.createPathFromPathData(pathData).asComposePath()
            }.getOrNull()
        }
    }
    val viewBox = 24f
    Canvas(modifier = modifier.size(iconSize)) {
        val scale = size.minDimension / viewBox
        val stroke = Stroke(
            width = 2f * (viewBox / size.minDimension),
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
            join = androidx.compose.ui.graphics.StrokeJoin.Round,
        )
        translate(
            (size.width - viewBox * scale) / 2f,
            (size.height - viewBox * scale) / 2f,
        ) {
            scale(scale, scale, pivot = Offset.Zero) {
                composePaths.forEach { path ->
                    drawPath(path, tint, style = stroke)
                }
            }
        }
    }
}
