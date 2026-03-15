package com.eazpire.creator.ui.footer

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.locale.LocaleStore
import com.eazpire.creator.ui.header.AVAILABLE_LANGUAGES
import com.eazpire.creator.ui.header.LanguageChildren
import com.eazpire.creator.ui.header.LanguageModal
import com.eazpire.creator.ui.header.LocaleModalItem
import com.eazpire.creator.util.DebugLog
import kotlinx.coroutines.launch

private val FooterBgDark = Color(0xFF080512)
private val FooterBgDarkTop = Color(0x99080512)
private val FooterText = Color.White
private val FooterSep = Color(0xB8FFFFFF)
private val FooterBrand = EazColors.Orange
private val FooterBtnBg = Color(0x14FFFFFF)
private val FooterBtnBorder = Color(0x1FFFFFFF)
private val FooterBalanceBg = Color(0x14FFFFFF)
private val FooterBalanceBorder = Color(0x1AFFFFFF)

private const val TERMS_URL = "https://allyoucanpink.com/policies/terms-of-service"
private const val EAZ_COIN_URL = "https://pub-2ffb11d4a361463498b9a842a87a870c.r2.dev/brand/coin/eaz-coin-logo.png"

/**
 * Global footer – matches web creator-global-footer (creator-mobile-overview.liquid).
 * Left: © year eazpire * Terms & Policies
 * Right: Language button + EAZ Balance (when logged in)
 */
@Composable
fun GlobalFooter(
    localeStore: LocaleStore,
    tokenStore: SecureTokenStore,
    onBalanceClick: () -> Unit = {},
    onLanguageChange: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val languageCode by localeStore.languageCode.collectAsState(initial = localeStore.getLanguageCodeSync())
    val ownerId = tokenStore.getOwnerId() ?: ""
    val jwt = tokenStore.getJwt()
    val api = remember(jwt) { CreatorApi(jwt = jwt) }
    val isLoggedIn = !ownerId.isBlank()

    var balanceEaz by remember { mutableStateOf<Double?>(null) }
    var languageStandard by remember { mutableStateOf(AVAILABLE_LANGUAGES) }
    var languageChildren by remember { mutableStateOf<Map<String, LanguageChildren>>(emptyMap()) }
    var showLanguageModal by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        try {
            val resp = api.getLanguages()
            if (resp.standard.isNotEmpty()) {
                languageStandard = resp.standard.map { LocaleModalItem(it.code, it.label, it.flagCode) }
                languageChildren = resp.children.mapValues { (_, v) ->
                    LanguageChildren(
                        dialects = v.dialects.map { LocaleModalItem(it.code, it.label, it.flagCode) },
                        scripts = v.scripts.map { LocaleModalItem(it.code, it.label, it.flagCode) }
                    )
                }.mapKeys { it.key.lowercase() }
            }
        } catch (_: Exception) { /* keep fallback */ }
    }

    LaunchedEffect(ownerId) {
        if (ownerId.isBlank()) return@LaunchedEffect
        try {
            val resp = api.getBalance(ownerId)
            if (resp.optBoolean("ok", false)) {
                balanceEaz = resp.optDouble("balance_eaz", 0.0)
            }
        } catch (e: Exception) {
            DebugLog.click("Footer balance error: ${e.message}")
        }
    }

    val year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        FooterBgDarkTop,
                        FooterBgDark.copy(alpha = 0.6f),
                        FooterBgDark
                    )
                )
            )
            .padding(horizontal = 20.dp)
            .padding(vertical = 12.dp)
            .padding(bottom = 24.dp) // safe area
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Copyright + Terms
            Row(
                modifier = Modifier.weight(1f, fill = false),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "© $year ",
                    color = FooterText,
                    fontSize = 13.sp
                )
                Text(
                    text = "eazpire",
                    color = FooterBrand,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "*",
                    color = FooterSep,
                    fontSize = 13.sp
                )
                Text(
                    text = "Terms & Policies",
                    color = FooterText,
                    fontSize = 13.sp,
                    modifier = Modifier.clickable {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(TERMS_URL)))
                        } catch (_: Exception) { }
                    }
                )
            }

            // Right: Language + Balance
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Language button
                Box(
                    modifier = Modifier
                        .background(FooterBtnBg, RoundedCornerShape(8.dp))
                        .clickable { showLanguageModal = true }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = languageCode.uppercase().take(2),
                        color = FooterText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // EAZ Balance (only when logged in)
                if (isLoggedIn) {
                    Box(
                        modifier = Modifier
                            .background(FooterBalanceBg, RoundedCornerShape(9.dp))
                            .clickable(onClick = onBalanceClick)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            AsyncImage(
                                model = EAZ_COIN_URL,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = when (val b = balanceEaz) {
                                    null -> "…"
                                    else -> "%.0f".format(b)
                                },
                                color = FooterBrand,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "EAZ",
                                color = FooterSep,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }

    if (showLanguageModal) {
        LanguageModal(
            title = "Select language",
            standardLanguages = languageStandard,
            languageChildren = languageChildren,
            selectedCode = languageCode,
            onDismiss = { showLanguageModal = false },
            onSelect = { code ->
                scope.launch {
                    localeStore.setLanguageOverride(code)
                    onLanguageChange(code)
                }
            },
            searchPlaceholder = "Search language..."
        )
    }
}
