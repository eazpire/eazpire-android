package com.eazpire.creator.ui.header

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.i18n.LocalTranslationStore
import com.eazpire.creator.i18n.TranslationStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Wallet / store-credit pill — 1:1 with web `.eaz-wallet-pill`.
 */
@Composable
fun HeaderWalletPill(
    tokenStore: SecureTokenStore?,
    onClick: () -> Unit,
    translationStore: TranslationStore? = null,
    modifier: Modifier = Modifier
) {
    val store = translationStore ?: LocalTranslationStore.current
    val ownerId = tokenStore?.getOwnerId().orEmpty()
    if (ownerId.isBlank()) return

    val jwt = tokenStore?.getJwt()
    val walletApi = remember(jwt, ownerId) { CreatorApi(jwt = jwt) }
    val loadingWallet = remember(store) {
        store?.t("eaz.wallet.loading", "…") ?: "…"
    }
    val walletAria = remember(store) {
        store?.t("eaz.wallet.open_vouchers", "Open gift cards and wallet")
            ?: "Open gift cards and wallet"
    }
    var walletText by remember(ownerId, loadingWallet) { mutableStateOf(loadingWallet) }

    LaunchedEffect(ownerId, loadingWallet) {
        walletText = loadingWallet
        try {
            val cur = try {
                Currency.getInstance(Locale.getDefault()).currencyCode
            } catch (_: Exception) {
                "EUR"
            }
            val r = withContext(Dispatchers.IO) {
                walletApi.getCustomerWalletTotal(ownerId, cur)
            }
            if (r.optBoolean("ok", false)) {
                val amt = r.optDouble("total_amount", 0.0)
                val c = r.optString("currency", cur)
                walletText = formatWalletAmount(amt, c)
            } else {
                walletText = "—"
            }
        } catch (_: Exception) {
            walletText = "—"
        }
    }

    Surface(
        onClick = onClick,
        modifier = modifier
            .semantics { contentDescription = walletAria }
            .heightIn(min = 40.dp)
            .widthIn(max = 110.dp),
        shape = RoundedCornerShape(50),
        color = EazColors.Orange.copy(alpha = 0.08f),
        border = BorderStroke(2.dp, EazColors.Orange),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 10.dp)
                .heightIn(min = 40.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = walletText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = Color(0xFF111111)
            )
        }
    }
}

/** Wie eaz-wallet.js fmtMoney (Intl currency). */
internal fun formatWalletAmount(amount: Double, currencyCode: String): String {
    return try {
        val nf = NumberFormat.getCurrencyInstance(Locale.getDefault())
        nf.currency = Currency.getInstance(currencyCode)
        nf.maximumFractionDigits = 2
        nf.format(amount)
    } catch (_: Exception) {
        String.format(Locale.getDefault(), "%.2f %s", amount, currencyCode)
    }
}
