package com.eazpire.creator.ui.nav

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
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
fun EazModalTablerIcon(
    tabId: String,
    modifier: Modifier = Modifier,
    tint: Color = EazColors.TextSecondary,
    iconSize: Dp = 18.dp,
) {
    if (tabId.equals("loyalitee", ignoreCase = true)) {
        EazLoyaliTeeIcon(modifier = modifier, tint = tint, iconSize = iconSize)
        return
    }
    val iconName = remember(tabId) { EazModalTablerIcons.iconNameForTab(tabId) }
    EazModalTablerIconByName(
        iconName = iconName,
        modifier = modifier,
        tint = tint,
        iconSize = iconSize,
    )
}

@Composable
fun EazModalTablerIconByName(
    iconName: String,
    modifier: Modifier = Modifier,
    tint: Color = EazColors.TextSecondary,
    iconSize: Dp = 18.dp,
    viewBox: Float = 24f,
    strokeWidth: Float = 1.75f,
) {
    val paths = remember(iconName) { EazModalTablerIcons.pathsForIcon(iconName) }
    val composePaths = remember(paths) {
        paths.mapNotNull { pathData ->
            runCatching {
                PathParser.createPathFromPathData(pathData).asComposePath()
            }.getOrNull()
        }
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(iconSize)) {
            val scaleFactor = size.minDimension / viewBox
            val stroke = Stroke(
                width = strokeWidth * (viewBox / size.minDimension),
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
}

@Composable
fun EazLoyaliTeeIcon(
    modifier: Modifier = Modifier,
    tint: Color = EazColors.TextSecondary,
    iconSize: Dp = 18.dp,
) {
    val paths = remember { EazModalTablerIcons.loyaliteePaths }
    val composePaths = remember(paths) {
        paths.mapNotNull { pathData ->
            runCatching {
                PathParser.createPathFromPathData(pathData).asComposePath()
            }.getOrNull()
        }
    }
    val viewBox = 20f
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(iconSize)) {
            val scaleFactor = size.minDimension / viewBox
            val stroke = Stroke(
                width = 1.4f * (viewBox / size.minDimension),
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
}
