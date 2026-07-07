package com.eazpire.creator.ui.header

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.eazpire.creator.R
import com.eazpire.creator.brand.BrandAssetSlots
import com.eazpire.creator.brand.BrandSlotImage

/** Matches theme `eaz-redesign-base.css` mobile topbar logo (41px height, width auto). */
private val HeaderLogoHeight = 41.dp
private val HeaderLogoMaxWidth = 220.dp

/** Same wide asset as web shop/creator headers (not the square app-icon drawable). */
private const val HeaderLogoFallbackUrl =
    "https://cdn.shopify.com/s/files/1/0739/5203/5098/files/eazpire-creator-logo.png?v=1763666950"

@Composable
fun HeaderLogo(
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                ) else Modifier
            )
            .padding(horizontal = 4.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        BrandSlotImage(
            slot = BrandAssetSlots.CREATOR_APP_HEADER_LOGO,
            fallbackResId = R.drawable.eaz_android_app_logo,
            fallbackUrl = HeaderLogoFallbackUrl,
            contentDescription = "eazpire",
            modifier = Modifier
                .height(HeaderLogoHeight)
                .widthIn(max = HeaderLogoMaxWidth),
            contentScale = ContentScale.Fit,
        )
    }
}
