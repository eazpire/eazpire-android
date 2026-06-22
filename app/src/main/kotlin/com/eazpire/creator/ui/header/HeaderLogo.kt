package com.eazpire.creator.ui.header

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.eazpire.creator.R
import com.eazpire.creator.brand.BrandAssetSlots
import com.eazpire.creator.brand.BrandSlotImage

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
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        BrandSlotImage(
            slot = BrandAssetSlots.CREATOR_APP_HEADER_LOGO,
            fallbackResId = R.drawable.eaz_android_app_logo,
            contentDescription = "eazpire",
            modifier = Modifier.size(36.dp),
            contentScale = ContentScale.Fit,
        )
    }
}
