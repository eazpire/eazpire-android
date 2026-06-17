package com.eazpire.creator.chat

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

private fun Color.simonHighlight(): Color =
    copy(
        red = (red + 0.14f).coerceIn(0f, 1f),
        green = (green + 0.14f).coerceIn(0f, 1f),
        blue = (blue + 0.14f).coerceIn(0f, 1f),
    )

private fun Color.simonShadow(): Color =
    copy(
        red = red * 0.68f,
        green = green * 0.68f,
        blue = blue * 0.68f,
    )

@Composable
fun EazySimonPad(
    baseColor: Color,
    imageUrl: String?,
    lit: Boolean,
    pressed: Boolean,
    enabled: Boolean,
    boardReady: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val active = lit || pressed || isPressed
    val pressDepth by animateDpAsState(if (active) 6.dp else 0.dp, label = "simonPressDepth")
    val shadowDepth by animateDpAsState(if (active) 1.dp else 6.dp, label = "simonShadowDepth")
    val shape = RoundedCornerShape(14.dp)
    val canTap = enabled && boardReady

    Box(modifier = modifier, contentAlignment = Alignment.TopCenter) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .offset(y = shadowDepth)
                    .clip(shape)
                    .background(baseColor.simonShadow().copy(alpha = 0.55f)),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .offset(y = pressDepth)
                    .graphicsLayer {
                        alpha = if (boardReady) 1f else 0.55f
                    }
                    .clip(shape)
                    .background(
                        Brush.verticalGradient(
                            colors =
                                listOf(
                                    baseColor.simonHighlight(),
                                    baseColor,
                                    baseColor.simonShadow(),
                                ),
                        ),
                    )
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        enabled = canTap,
                        onClick = onClick,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors =
                                    listOf(
                                        Color.White.copy(alpha = 0.22f),
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.14f),
                                    ),
                            ),
                        ),
            )
            if (!imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(10.dp),
                    contentScale = ContentScale.Fit,
                )
            }
        }
    }
}
