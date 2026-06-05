package com.eazpire.creator.ui.nav

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
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
import com.eazpire.creator.EazColors

@Composable
fun EazNavTablerIcon(
    handle: String,
    modifier: Modifier = Modifier,
    tint: Color = EazColors.Orange,
    iconSize: Dp = 22.dp,
) {
    EazNavTablerIconByName(
        iconName = EazNavTablerIcons.iconNameForHandle(handle),
        modifier = modifier,
        tint = tint,
        iconSize = iconSize,
    )
}

@Composable
fun EazNavTablerIconByName(
    iconName: String,
    modifier: Modifier = Modifier,
    tint: Color = EazColors.Orange,
    iconSize: Dp = 22.dp,
) {
    val paths = remember(iconName) { EazNavTablerIcons.paths[iconName] ?: EazNavTablerIcons.paths["box"].orEmpty() }
    val composePaths = remember(paths) {
        paths.mapNotNull { pathData ->
            runCatching {
                PathParser.createPathFromPathData(pathData).asComposePath()
            }.getOrNull()
        }
    }
    val viewBox = 24f
    Canvas(modifier = modifier.size(iconSize)) {
        val scaleFactor = size.minDimension / viewBox
        val stroke = Stroke(
            width = 2f * (viewBox / size.minDimension),
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
            join = androidx.compose.ui.graphics.StrokeJoin.Round,
        )
        translate(
            (size.width - viewBox * scaleFactor) / 2f,
            (size.height - viewBox * scaleFactor) / 2f,
        ) {
            scale(scaleFactor, scaleFactor, pivot = Offset.Zero) {
                composePaths.forEach { path ->
                    drawPath(path, tint, style = stroke)
                }
            }
        }
    }
}

@Composable
fun EazNavTablerIconChip(
    handle: String,
    modifier: Modifier = Modifier,
    tint: Color = EazColors.Orange,
    iconSize: Dp = 18.dp,
    chipSize: Dp = 32.dp,
    onDarkHeader: Boolean = false,
) {
    val chipBg = if (onDarkHeader) Color.White.copy(alpha = 0.2f) else Color(0xFFFFF5ED)
    val chipBorder = if (onDarkHeader) Color.White.copy(alpha = 0.35f) else Color(0xFFF97316).copy(alpha = 0.18f)
    val iconTint = if (onDarkHeader) Color.White else tint
    Box(
        modifier = modifier
            .size(chipSize)
            .background(chipBg, CircleShape)
            .border(width = if (onDarkHeader) 1.5.dp else 2.dp, color = chipBorder, shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        EazNavTablerIconByName(
            iconName = EazNavTablerIcons.iconNameForHandle(handle),
            tint = iconTint,
            iconSize = iconSize,
        )
    }
}
