package com.eazpire.creator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.eazpire.creator.EazColors

/** CDN for flag images – same as web creator footer. */
const val FLAG_CDN = "https://flagcdn.com/w80"

/** Emoji fallback when flag image fails. */
private val FLAG_EMOJI = mapOf(
    "ALL" to "🌍", "DE" to "🇩🇪", "AT" to "🇦🇹", "CH" to "🇨🇭", "NL" to "🇳🇱",
    "SE" to "🇸🇪", "US" to "🇺🇸", "FR" to "🇫🇷", "ES" to "🇪🇸", "IT" to "🇮🇹",
    "PL" to "🇵🇱", "GB" to "🇬🇧", "TR" to "🇹🇷"
)

private fun flagEmojiFor(code: String): String = FLAG_EMOJI[code.uppercase()] ?: "🏳️"

private fun flagImageUrl(code: String): String {
    val c = code.uppercase().take(2)
    val imgCode = if (c == "ALL" || c.isEmpty()) "un" else c.lowercase()
    return "$FLAG_CDN/$imgCode.png"
}

/**
 * Circular flag with glass 3D style – matches creator-desktop-footer__language-flag.
 * Use globally: header (location, language), Community tab.
 */
@Composable
fun GlassCircularFlag(
    countryCode: String,
    size: Dp = 24.dp,
    modifier: Modifier = Modifier
) {
    val emojiFallback = flagEmojiFor(countryCode)
    val url = flagImageUrl(countryCode)
    val shadowSize = (size.value * 0.5f).dp.coerceAtLeast(4.dp)

    Box(
        modifier = modifier
            .size(size)
            .shadow(
                elevation = shadowSize,
                shape = CircleShape,
                ambientColor = Color.Black.copy(alpha = 0.42f),
                spotColor = Color.Black.copy(alpha = 0.42f)
            )
            .clip(CircleShape)
            .background(Color.Transparent)
    ) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(url)
                .crossfade(true)
                .build(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            error = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(EazColors.TopbarBorder.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(emojiFallback, style = MaterialTheme.typography.bodyMedium)
                }
            }
        )
        // Glossy top highlight (::before in web)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.56f),
                            Color.White.copy(alpha = 0.08f),
                            Color.Transparent
                        )
                    )
                )
        )
        // Outer ring (0 0 0 1px rgba(255,255,255,0.18))
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
        )
    }
}
