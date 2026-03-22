package com.eazpire.creator.ui.vouchers

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.eazpire.creator.EazColors
import org.json.JSONObject
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency
import java.util.Locale

private val GiftCardBorder = Color(0xFFE5E7EB)
private val WebTextStrong = Color(0xFF111111)
private val WebSubdued = Color(0xFF6B7280)
private val BadgeGreen = Color(0xFF10B981)
private val BadgeGray = Color(0xFF6B7280)
private val BadgeBlue = Color(0xFF3B82F6)

private const val GIFT_DETAIL_BASE = "https://www.eazpire.com/pages/gift-card-detail?id="

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

private fun fmtDateIso(iso: String?, longStyle: Boolean = false): String {
    if (iso.isNullOrBlank()) return "-"
    return try {
        val instant = Instant.parse(iso.replace(" ", "T"))
        val z = ZoneId.systemDefault()
        val fmt = if (longStyle) {
            DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withZone(z)
        } else {
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withZone(z)
        }
        fmt.format(instant)
    } catch (_: Exception) {
        iso
    }
}

private fun firstNonEmpty(j: JSONObject, vararg keys: String): String {
    for (k in keys) {
        val v = j.optString(k, "")
        if (v.isNotBlank()) return v
    }
    return ""
}

@Composable
fun WebStyleStoreCreditCard(
    row: JSONObject,
    currencyFallback: String,
    t: (String, String) -> String
) {
    val amount = row.optDouble("amount", 0.0)
    val cur = row.optString("payoutCurrency", currencyFallback)
    val amountStr = fmtMoney(amount, cur)
    val createdAt = firstNonEmpty(row, "createdAt", "created_at", "paidOutAt", "paid_out_at")
    val statusRaw = firstNonEmpty(row, "status", "state", "payoutStatus", "payout_status")
    val referenceRaw = firstNonEmpty(row, "id", "payoutId", "payout_id", "reference", "txId", "transaction_id")
    val typeRaw = firstNonEmpty(row, "payoutType", "payout_type", "method", "sourceType", "source_type")
    val noteRaw = firstNonEmpty(row, "description", "reason", "note", "memo")
    val status = statusRaw.replace(Regex("[_-]+"), " ").ifBlank { "-" }
    val reference = referenceRaw.ifBlank { "-" }
    val payoutType = typeRaw.replace(Regex("[_-]+"), " ").ifBlank { "shop credit" }
    val bookedAt = if (createdAt.isNotBlank()) fmtDateIso(createdAt) else "-"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, GiftCardBorder, RoundedCornerShape(12.dp))
            .background(Color.White)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(
                    Brush.linearGradient(
                        colors = listOf(EazColors.Orange, Color(0xFFFB923C))
                    )
                )
        ) {
            Text(
                amountStr,
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center)
            )
            Text(
                t("creator.voucher_page.subtab_store_credit", "Store Credit"),
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(Color(0xB3000000), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
        Column(Modifier.padding(12.dp)) {
            Text(
                t("creator.voucher_page.subtab_store_credit", "Store Credit"),
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = WebTextStrong
            )
            Spacer(Modifier.padding(vertical = 4.dp))
            ValueRow(t("creator.voucher_page.store_credit_booked_label", "Booked"), bookedAt)
            ValueRow(t("creator.voucher_page.store_credit_status_label", "Status"), status)
            ValueRow(t("creator.voucher_page.store_credit_type_label", "Type"), payoutType)
            ValueRow(t("creator.voucher_page.store_credit_reference_label", "Reference"), reference, mono = true)
            if (noteRaw.isNotBlank()) {
                Text(
                    "${t("creator.voucher_page.store_credit_details_label", "Details")}: $noteRaw",
                    fontSize = 12.sp,
                    color = WebSubdued,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            Spacer(Modifier.padding(top = 8.dp))
            Text(
                t("creator.voucher_page.store_credit_badge_booked", "Booked"),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(BadgeGreen)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun ValueRow(label: String, value: String, mono: Boolean = false) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 12.sp, color = WebSubdued, modifier = Modifier.weight(0.45f))
        Text(
            value,
            fontSize = 12.sp,
            color = WebTextStrong,
            fontWeight = FontWeight.SemiBold,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
            modifier = Modifier.weight(0.55f)
        )
    }
}

@Composable
fun WebStyleGiftCard(
    gc: JSONObject,
    t: (String, String) -> String,
    onOpenDetail: ((String) -> Unit)? = null
) {
    val uriHandler = LocalUriHandler.current
    val balance = gc.optDouble("balance", 0.0)
    val initialValue = gc.optDouble("initial_value", 0.0)
    val used = initialValue - balance
    val cur = gc.optString("currency", "EUR")
    val formattedBalance = fmtMoney(balance, cur)
    val formattedInitial = fmtMoney(initialValue, cur)
    val formattedUsed = fmtMoney(used, cur)
    val et = gc.optJSONObject("email_template")
    val id = gc.optString("id", "")
    val url = "${GIFT_DETAIL_BASE}$id"

    val badgePair = remember(gc) {
        when {
            et != null && et.optString("status") == "sent" -> {
                if (et.optString("opened_at").isNotBlank()) {
                    val d = fmtDateIso(et.optString("opened_at"))
                    "received" to t("creator.voucher_page.badge_received_on", "Received {date}").replace("{date}", d)
                } else {
                    val d = fmtDateIso(et.optString("sent_at"))
                    "sent" to t("creator.voucher_page.badge_sent_on", "Sent {date}").replace("{date}", d)
                }
            }
            !gc.optBoolean("enabled", true) -> "disabled" to t("creator.gift_cards.disabled", "Disabled")
            balance == 0.0 -> "redeemed" to t("creator.gift_cards.redeemed", "Redeemed")
            else -> "enabled" to t("creator.gift_cards.active", "Active")
        }
    }

    val isReceivedGift = et != null && et.optString("status") == "sent" && et.optString("sender_name").isNotBlank()
    val giftImageUrl = if (isReceivedGift) et.optString("image_url").takeIf { it.isNotBlank() } else null
    val senderName = if (isReceivedGift) et.optString("sender_name") else ""
    val messagePreview = if (isReceivedGift && et.optString("message").isNotBlank()) {
        val m = et.optString("message")
        if (m.length > 100) m.take(100) + "…" else m
    } else null

    val purchaseBadge = remember(gc) {
        when {
            gc.optBoolean("is_buyer", false) && gc.optString("purchase_date").isNotBlank() ->
                "${t("creator.gift_cards.purchased", "Purchased")} ${fmtDateIso(gc.optString("purchase_date"))}"
            gc.optBoolean("is_recipient", false) && gc.optString("activation_date").isNotBlank() ->
                "${t("creator.gift_cards.received", "Received")} ${fmtDateIso(gc.optString("activation_date"))}"
            else -> null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, GiftCardBorder, RoundedCornerShape(12.dp))
            .background(Color.White)
            .clickable {
                if (onOpenDetail != null && id.isNotBlank()) onOpenDetail(id)
                else runCatching { uriHandler.openUri(url) }
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(
                    Brush.linearGradient(
                        colors = listOf(EazColors.Orange, Color(0xFFEF4444))
                    )
                )
        ) {
            if (giftImageUrl != null) {
                AsyncImage(
                    model = giftImageUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                    contentScale = ContentScale.Crop
                )
                Text(
                    formattedBalance,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .background(Color(0xB3000000), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            } else {
                Text(
                    formattedBalance,
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            purchaseBadge?.let { pb ->
                Text(
                    pb,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(Color(0xE60F172A), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
        Column(Modifier.padding(12.dp)) {
            Text(
                t("creator.gift_cards.gift_card", "Gift Card"),
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (isReceivedGift && senderName.isNotBlank()) {
                Row(Modifier.padding(top = 4.dp)) {
                    Text(
                        "${t("creator.gift_cards.from_label", "From")}: ",
                        fontSize = 12.sp,
                        color = WebSubdued
                    )
                    Text(senderName, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Text(
                gc.optString("masked_code").ifBlank { "****${gc.optString("last_characters", "")}" },
                fontSize = 12.sp,
                color = WebSubdued,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 4.dp)
            )
            messagePreview?.let {
                Text(
                    it,
                    fontSize = 12.sp,
                    color = WebSubdued,
                    fontStyle = FontStyle.Italic,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Column(
                Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth()
            ) {
                ValueRow(t("creator.gift_cards.value_label", "Value"), formattedInitial)
                ValueRow(t("creator.gift_cards.balance_label", "Balance"), formattedBalance)
                if (used > 0) {
                    ValueRow(t("creator.gift_cards.used_label", "Used"), formattedUsed)
                }
            }
            gc.optString("expires_on").takeIf { it.isNotBlank() }?.let { exp ->
                Text(
                    "${t("creator.gift_cards.expires", "Expires")}: ${fmtDateIso(exp, longStyle = true)}",
                    fontSize = 12.sp,
                    color = WebSubdued,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            Spacer(Modifier.padding(top = 8.dp))
            val (kind, text) = badgePair
            val bg = when (kind) {
                "received", "enabled" -> BadgeGreen
                "sent" -> BadgeBlue
                "disabled", "redeemed" -> BadgeGray
                else -> BadgeGray
            }
            Text(
                text,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(bg)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}
