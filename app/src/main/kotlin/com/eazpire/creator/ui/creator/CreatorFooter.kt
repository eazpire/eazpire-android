package com.eazpire.creator.ui.creator

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.eazpire.creator.EazColors
import com.eazpire.creator.ui.components.GlassCircularFlag
import com.eazpire.creator.i18n.TranslationStore
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.locale.LocaleStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val CREATOR_BASE_URL = "https://www.eazpire.com"

/** Footer 1:1 wie Web .creator-global-footer */
@Composable
fun CreatorFooter(
    localeStore: LocaleStore,
    tokenStore: SecureTokenStore,
    translationStore: TranslationStore? = null,
    onLanguageClick: () -> Unit = {},
    onTermsClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val langCode by localeStore.languageCode.collectAsState(initial = "en")
    var eazBalance by remember { mutableStateOf("…") }
    val api = remember { CreatorApi(jwt = tokenStore.getJwt()) }
    val ownerId = remember(tokenStore) { tokenStore.getOwnerId() ?: "" }

    LaunchedEffect(ownerId) {
        if (ownerId.isNotBlank()) {
            try {
                val r = withContext(Dispatchers.IO) { api.getBalance(ownerId) }
                if (r.optBoolean("ok", false)) {
                    val bal = r.optDouble("balance_eaz", r.optDouble("balance", 0.0))
                    eazBalance = "%.2f".format(bal)
                } else {
                    eazBalance = "0.00"
                }
            } catch (_: Exception) {
                eazBalance = "0.00"
            }
        } else {
            eazBalance = "0.00"
        }
    }

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
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)
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
        // Right: LANG (flag only, clickable) + coin + balance (wie .creator-global-footer__balance)
        Row(
            modifier = Modifier.padding(start = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
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
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data("https://pub-2ffb11d4a361463498b9a842a87a870c.r2.dev/brand/coin/eaz-coin-logo.png")
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
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
                    text = "EAZ",
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
