package com.eazpire.creator.ui.eazc

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.brand.BrandAssetSlots
import com.eazpire.creator.brand.EazCoinImage
import com.eazpire.creator.i18n.TranslationStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Shared EAZC badge: coin + amount + unit + right-aligned info.
 * Info opens [EazcEarnInfoModal]. Amount click is optional (Creator → Balance & Payouts).
 */
@Composable
fun EazcBadge(
    tokenStore: SecureTokenStore?,
    translationStore: TranslationStore,
    onInfoClick: () -> Unit,
    modifier: Modifier = Modifier,
    onBalanceClick: (() -> Unit)? = null,
    compact: Boolean = false,
    dark: Boolean = true,
) {
    val ownerId = tokenStore?.getOwnerId().orEmpty()
    val jwt = tokenStore?.getJwt()
    val api = remember(jwt) { CreatorApi(jwt = jwt) }
    var amountText by remember(ownerId) { mutableStateOf("…") }
    val balanceAria = translationStore.t(
        "creator.sales.eazc_header_balance",
        "Available and pending EAZC"
    )
    val infoAria = translationStore.t(
        "creator.eazc_earn.info_aria",
        "How you earn EAZC from sales"
    )

    LaunchedEffect(ownerId) {
        if (ownerId.isBlank()) {
            amountText = "0"
            return@LaunchedEffect
        }
        amountText = "…"
        try {
            val data = withContext(Dispatchers.IO) { api.getBalance(ownerId) }
            amountText = formatHeaderEazcAmount(headerEazcFromBalance(data))
        } catch (_: Exception) {
            amountText = "0"
        }
    }

    val shape = RoundedCornerShape(if (compact || !dark) 50.dp else 10.dp)
    val bg = if (dark) {
        Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.02f))
        )
    } else {
        Brush.verticalGradient(
            listOf(Color(0xFF123056), EazColors.EazcNavy)
        )
    }
    val borderColor = if (dark) {
        Color.White.copy(alpha = 0.16f)
    } else {
        EazColors.Orange.copy(alpha = 0.45f)
    }

    Row(
        modifier = modifier
            .heightIn(min = if (compact) 40.dp else 36.dp)
            .background(bg, shape)
            .border(1.dp, borderColor, shape)
            .semantics { contentDescription = balanceAria }
            .padding(start = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier
                .then(
                    if (onBalanceClick != null) {
                        Modifier.clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { onBalanceClick() }
                    } else {
                        Modifier
                    }
                )
                .padding(vertical = 6.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            EazCoinImage(slot = BrandAssetSlots.EAZC_COIN_LOGO, size = 16.dp)
            Text(
                text = amountText,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = EazColors.Orange
            )
            if (!compact) {
                Text(
                    text = "EAZC",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.78f)
                )
            }
        }
        IconButton(
            onClick = onInfoClick,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = infoAria,
                tint = EazColors.Orange,
                modifier = Modifier
                    .size(18.dp)
                    .background(EazColors.Orange.copy(alpha = 0.14f), CircleShape)
                    .padding(2.dp)
            )
        }
    }
}
