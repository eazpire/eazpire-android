package com.eazpire.creator.ui.creator

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eazpire.creator.brand.BrandAssetSlots
import com.eazpire.creator.brand.EazCoinImage
import com.eazpire.creator.EazColors
import com.eazpire.creator.billing.EazBalanceCache
import com.eazpire.creator.billing.EazBalanceRefreshBus
import com.eazpire.creator.ui.components.GlassCircularFlag
import com.eazpire.creator.i18n.TranslationStore
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.locale.LocaleStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val CREATOR_BASE_URL = "https://www.eazpire.com"

private fun isStarterFooterMode(data: JSONObject): Boolean {
    if (!data.optBoolean("ok", false)) return false
    if (data.optBoolean("is_creator", false)) return false
    if (data.optBoolean("eaz_wallet_active", false)) return false
    if (!data.optBoolean("trial_mode", false)) return false
    if (data.optInt("display_level", 1) != 1) return false
    val xpLevel = data.optInt("xp_level", data.optInt("xp_derived_level", 1))
    if (xpLevel != 1) return false
    return data.has("trial_generate_cap") && data.has("trial_upload_cap")
}

private fun formatEazBalance(bal: Double): String =
    if (bal % 1.0 == 0.0) "%.0f".format(bal) else "%.1f".format(bal)

/** Footer 1:1 wie Web .creator-global-footer */
@Composable
fun CreatorFooter(
    localeStore: LocaleStore,
    tokenStore: SecureTokenStore,
    translationStore: TranslationStore? = null,
    onLanguageClick: () -> Unit = {},
    onTermsClick: (() -> Unit)? = null,
    onBalanceClick: (starterMode: Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val langCode by localeStore.languageCode.collectAsState(initial = "en")
    val balanceRefreshTick by EazBalanceRefreshBus.tick.collectAsState()
    var eazBalance by remember { mutableStateOf("…") }
    var starterMode by remember { mutableStateOf(false) }
    var generateCount by remember { mutableStateOf("0/5") }
    var uploadCount by remember { mutableStateOf("0/20") }
    val api = remember { CreatorApi(jwt = tokenStore.getJwt()) }
    val ownerId = remember(tokenStore) { tokenStore.getOwnerId() ?: "" }

    LaunchedEffect(ownerId, balanceRefreshTick) {
        if (ownerId.isNotBlank()) {
            try {
                val r = withContext(Dispatchers.IO) { api.getBalance(ownerId) }
                if (r.optBoolean("ok", false)) {
                    starterMode = isStarterFooterMode(r)
                    if (starterMode) {
                        val gu = r.optInt("trial_generate_used", 0)
                        val gc = r.optInt("trial_generate_cap", 5)
                        val uu = r.optInt("trial_upload_used", 0)
                        val uc = r.optInt("trial_upload_cap", 20)
                        generateCount = "$gu/$gc"
                        uploadCount = "$uu/$uc"
                    } else {
                        val bal = r.optDouble(
                            "balance_total",
                            r.optDouble("balance_eaz", r.optDouble("balance", 0.0))
                        )
                        EazBalanceCache.write(bal)
                        eazBalance = formatEazBalance(bal)
                    }
                } else {
                    starterMode = false
                    eazBalance = "0.00"
                }
            } catch (_: Exception) {
                starterMode = false
                eazBalance = "0.00"
            }
        } else {
            starterMode = false
            eazBalance = "0.00"
        }
    }

    val generateTitle = translationStore?.t(
        "creator.settings.eaz_starter_generate_short",
        "Starter generations used"
    ) ?: "Starter generations used"
    val uploadTitle = translationStore?.t(
        "creator.settings.eaz_starter_upload_short",
        "Starter uploads used"
    ) ?: "Starter uploads used"

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color(0xEB080512)
                    )
                )
            )
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: © YEAR eazpire • Terms
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "© ${java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)} ",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = Color.White
            )
            Text(
                text = "eazpire",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = EazColors.Orange
            )
            Text(
                text = " • ",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = Color.White.copy(alpha = 0.72f)
            )
            Text(
                text = "Terms & Policies",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = Color.White,
                modifier = Modifier.clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    if (onTermsClick != null) {
                        onTermsClick()
                    } else {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("$CREATOR_BASE_URL/policies/terms-of-service")))
                        } catch (_: Exception) {}
                    }
                }
            )
        }
        // Right: LANG (flag only, clickable) + starter slots or EAZ balance
        Row(
            modifier = Modifier.padding(start = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val flagCode = localeStore.getFlagCountryForLanguage(langCode)
            Box(
                modifier = Modifier
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { onLanguageClick() }
            ) {
                GlassCircularFlag(countryCode = flagCode, size = 24.dp)
            }
            Row(
                modifier = Modifier
                    .background(
                        Color.White.copy(alpha = 0.08f),
                        RoundedCornerShape(9.dp)
                    )
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { onBalanceClick(starterMode) }
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (starterMode) {
                    FooterStarterSlot(
                        icon = Icons.Default.AutoAwesome,
                        count = generateCount,
                        contentDescription = generateTitle
                    )
                    FooterStarterSlot(
                        icon = Icons.Default.Upload,
                        count = uploadCount,
                        contentDescription = uploadTitle
                    )
                } else {
                    EazCoinImage(
                        slot = BrandAssetSlots.EAZV_COIN_LOGO,
                        size = 14.dp,
                        contentDescription = null,
                    )
                    Text(
                        text = eazBalance,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = EazColors.Orange
                    )
                    Text(
                        text = "EAZV",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = Color.White.copy(alpha = 0.72f)
                    )
                }
            }
        }
    }
}

@Composable
private fun FooterStarterSlot(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    count: String,
    contentDescription: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = EazColors.Orange,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = count,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            ),
            color = EazColors.Orange
        )
    }
}
