package com.eazpire.creator.ui.vouchers

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.CreatorApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency
import java.util.Locale

private val BorderDefault = Color(0xFFE5E7EB)
private val TextSubdued = Color(0xFF6B7280)
private val TextStrong = Color(0xFF111111)
private val DangerText = Color(0xFFDC2626)
private val DangerBorder = Color(0xFFFECACA)
private val BadgeActive = Color(0xFF10B981)
private val BadgeShared = Color(0xFF3B82F6)
private val RedeemedBadgeBg = Color(0xFF6B7280)

private const val PROMO_SHARE_BASE = "https://www.eazpire.com/pages/promo-code"
private const val PROMO_CREATE_FALLBACK_URL = "https://www.eazpire.com/pages/my-gift-cards"

private fun buildPromoShareUrl(token: String): String = "$PROMO_SHARE_BASE?token=$token"

private fun fmtMoney(amount: Double, currency: String): String {
    return try {
        NumberFormat.getCurrencyInstance(Locale.getDefault()).apply {
            maximumFractionDigits = 2
            this.currency = Currency.getInstance(currency)
        }.format(amount)
    } catch (_: Exception) {
        "%.2f %s".format(Locale.getDefault(), amount, currency)
    }
}

private fun fmtPromoDate(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    return try {
        val instant = Instant.parse(iso.replace(" ", "T"))
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            .withZone(ZoneId.systemDefault())
            .format(instant)
    } catch (_: Exception) {
        iso
    }
}

@Composable
fun PromoSlotCardWeb(
    promo: JSONObject,
    t: (String, String) -> String,
    ownerId: String,
    api: CreatorApi,
    onReload: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showRevokeDialog by remember { mutableStateOf(false) }

    val cur = promo.optString("value_currency", "EUR")
    val valStr = fmtMoney(promo.optDouble("value_amount", 0.0), cur)
    val minOrder = fmtMoney(promo.optDouble("minimum_order_amount", 0.0), cur)
    val code = promo.optString("discount_code", "")
    val token = promo.optString("share_token", "")
    val isShared = promo.optString("shared_via").isNotBlank()
    val expires = promo.optString("expires_at", "")
    val created = promo.optString("created_at", "")
    val promoId = promo.optString("id", "")

    val expiresPart = if (expires.isNotBlank()) " · ${fmtPromoDate(expires)}" else ""

    fun copyLink() {
        val url = buildPromoShareUrl(token)
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("promo", url))
        Toast.makeText(
            context,
            t("creator.voucher_page.promo_copy_success", "Copied!"),
            Toast.LENGTH_SHORT
        ).show()
    }

    fun shareLink() {
        val url = buildPromoShareUrl(token)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
        }
        context.startActivity(Intent.createChooser(send, t("creator.voucher_page.promo_share", "Share")))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, BorderDefault, RoundedCornerShape(12.dp))
            .background(Color.White)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(EazColors.Orange, Color(0xFF6366F1))
                    )
                )
                .padding(horizontal = 16.dp, vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    valStr,
                    fontSize = 28.sp,
                    lineHeight = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    textAlign = TextAlign.Center
                )
                Text(
                    code,
                    fontSize = 12.8.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.04.sp,
                    color = Color.Black,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = if (isShared) {
                    t("creator.voucher_page.promo_status_shared", "Shared")
                } else {
                    t("creator.voucher_page.promo_status_active", "Active")
                },
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (isShared) BadgeShared else BadgeActive)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
            Text(
                t("creator.voucher_page.promo_min_order_line", "Min. {amount}{expires}")
                    .replace("{amount}", minOrder)
                    .replace("{expires}", expiresPart),
                fontSize = 12.8.sp,
                color = TextSubdued,
                modifier = Modifier.padding(top = 8.dp)
            )
            if (created.isNotBlank()) {
                Text(
                    fmtPromoDate(created),
                    fontSize = 12.8.sp,
                    color = TextSubdued,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { copyLink() },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    border = BorderStroke(1.dp, BorderDefault),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.White,
                        contentColor = TextStrong
                    )
                ) {
                    Text(
                        t("creator.voucher_page.promo_copy_link", "Copy link"),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                OutlinedButton(
                    onClick = { shareLink() },
                    modifier = Modifier.size(44.dp),
                    contentPadding = PaddingValues(0.dp),
                    border = BorderStroke(1.dp, BorderDefault),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextStrong)
                ) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = t("creator.voucher_page.promo_share", "Share"),
                        modifier = Modifier.size(18.dp)
                    )
                }
                OutlinedButton(
                    onClick = { showRevokeDialog = true },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    border = BorderStroke(1.dp, DangerBorder),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color(0xFFFEF2F2),
                        contentColor = DangerText
                    )
                ) {
                    Text(
                        t("creator.voucher_page.promo_revoke", "Revoke"),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

    if (showRevokeDialog) {
        AlertDialog(
            onDismissRequest = { showRevokeDialog = false },
            title = { Text(t("creator.voucher_page.promo_revoke", "Revoke")) },
            text = {
                Text(t("creator.voucher_page.promo_revoke_confirm", "Really revoke this promo code?"))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRevokeDialog = false
                        scope.launch {
                            try {
                                val r = withContext(Dispatchers.IO) {
                                    api.revokePromoCode(ownerId, promoId)
                                }
                                if (r.optBoolean("ok", false)) {
                                    onReload()
                                } else {
                                    Toast.makeText(
                                        context,
                                        r.optString("message", "Error"),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, e.message ?: "Error", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                ) {
                    Text(t("creator.common.yes", "Yes"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRevokeDialog = false }) {
                    Text(t("creator.common.cancel", "Cancel"))
                }
            }
        )
    }
}

@Composable
fun PromoEmptySlotWeb(
    t: (String, String) -> String
) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, BorderDefault.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.96f))
            .clickable {
                runCatching { uriHandler.openUri(PROMO_CREATE_FALLBACK_URL) }
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF5F5F5))
                .padding(vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "+",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = TextSubdued
            )
        }
        Text(
            t("creator.voucher_page.promo_create_slot", "Create Promo Code"),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            fontSize = 13.6.sp,
            color = TextSubdued,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun PromoRedeemedRowWeb(p: JSONObject, t: (String, String) -> String) {
    val cur = p.optString("value_currency", "EUR")
    val valStr = fmtMoney(p.optDouble("value_amount", 0.0), cur)
    val code = p.optString("discount_code", "")
    val dateStr = fmtPromoDate(p.optString("redeemed_at"))
    val isCreator = p.optString("share_token").isNotBlank()
    val label = if (isCreator) {
        t("creator.voucher_page.promo_redeemed_by_other", "Redeemed by other")
    } else {
        t("creator.voucher_page.promo_redeemed_by_you", "Redeemed by you")
    }

    Row(
        Modifier
            .fillMaxWidth()
            .border(1.dp, BorderDefault, RoundedCornerShape(8.dp))
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(valStr, fontWeight = FontWeight.SemiBold, fontSize = 17.6.sp, color = TextStrong)
            Text(
                code,
                fontSize = 12.8.sp,
                fontFamily = FontFamily.Monospace,
                color = TextSubdued,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                label,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier
                    .background(RedeemedBadgeBg, RoundedCornerShape(999.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
            if (dateStr.isNotBlank()) {
                Text(
                    dateStr,
                    fontSize = 12.8.sp,
                    color = TextSubdued,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}
