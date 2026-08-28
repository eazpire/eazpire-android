package com.eazpire.creator.ui.header

import androidx.compose.foundation.Image
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.eazpire.creator.R

/** Matches theme `eaz-redesign-base.css` mobile topbar logo (41px height, width auto). */
private val HeaderLogoHeight = 41.dp
private val HeaderLogoMaxWidth = 220.dp

/**
 * Shop header wordmark — same transparent PNG as
 * `theme/assets/eazpire-shop-header-logo.png`.
 * Local drawable so the new mark is not overridden by the older Shopify Files slot.
 */
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
        Image(
            painter = painterResource(R.drawable.eaz_android_app_logo),
            contentDescription = "eazpire",
            modifier = Modifier
                .height(HeaderLogoHeight)
                .widthIn(max = HeaderLogoMaxWidth),
            contentScale = ContentScale.Fit,
        )
    }
}
