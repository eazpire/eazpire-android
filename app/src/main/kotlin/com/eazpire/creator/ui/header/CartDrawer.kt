package com.eazpire.creator.ui.header

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.eazpire.creator.R
import com.eazpire.creator.EazColors
import com.eazpire.creator.i18n.LocalTranslationStore
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.api.ShopifyStorefrontCartApi
import com.eazpire.creator.cart.AppCartStore
import com.eazpire.creator.cart.CartPromoReminderScheduler
import com.eazpire.creator.cart.StorefrontCartStore
import com.eazpire.creator.locale.LocaleStore
import com.eazpire.creator.util.DebugLog
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

@Composable
fun CartDrawer(
    visible: Boolean,
    tokenStore: com.eazpire.creator.auth.SecureTokenStore?,
    onDismiss: () -> Unit,
    onCheckout: (checkoutUrl: String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!visible) return

    val context = LocalContext.current
    val store = LocalTranslationStore.current
    val tr = store?.translations?.collectAsState(initial = emptyMap())?.value
    val t = store?.let { { k: String, d: String -> it.t(k, d) } } ?: { _: String, d: String -> d }
    val cartStore = remember { StorefrontCartStore(context) }
    val api = remember { ShopifyStorefrontCartApi() }
    val accessToken = tokenStore?.getAccessToken()

    var cart by remember { mutableStateOf<ShopifyStorefrontCartApi.CartResult?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var promoByHandle by remember { mutableStateOf<Map<String, PromoResolveRow>>(emptyMap()) }

    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        loading = true
        error = null
        val cartId = cartStore.cartId
        if (cartId != null) {
            cart = withContext(Dispatchers.IO) { api.getCart(cartId) }
            if (cart == null) {
                cartStore.clear()
                AppCartStore.clear()
            } else {
                AppCartStore.setCount(cart!!.itemCount)
            }
        } else {
            cart = null
            AppCartStore.clear()
        }
        loading = false
    }

    LaunchedEffect(cart) {
        val c = cart
        if (c == null || c.lines.isEmpty()) {
            promoByHandle = emptyMap()
            CartPromoReminderScheduler.cancel(context)
            return@LaunchedEffect
        }
        val arr = JSONArray()
        for (line in c.lines) {
            if (line.productHandle.isBlank()) continue
            val jo = JSONObject().put("handle", line.productHandle).put("price", line.priceAmount.toDoubleOrNull() ?: 0.0)
            line.compareAtAmount?.toDoubleOrNull()?.let { jo.put("compare_at_price", it) }
            arr.put(jo)
        }
        if (arr.length() == 0) {
            promoByHandle = emptyMap()
            CartPromoReminderScheduler.cancel(context)
            return@LaunchedEffect
        }
        val country = LocaleStore(context).getCountryCodeSync()
        val resp = withContext(Dispatchers.IO) {
            CreatorApi().resolvePromoCart(country, arr)
        }
        if (resp.optBoolean("ok")) {
            promoByHandle = parsePromoResolve(resp)
            val earliest = promoByHandle.values
                .mapNotNull { it.displayEndsAt }
                .filter { it > System.currentTimeMillis() }
                .minOrNull()
            if (earliest != null) {
                CartPromoReminderScheduler.schedule(context, earliest)
            } else {
                CartPromoReminderScheduler.cancel(context)
            }
        } else {
            promoByHandle = emptyMap()
            CartPromoReminderScheduler.cancel(context)
        }
    }

    Dialog(
        onDismissRequest = {
            DebugLog.click("CartDrawer dismiss (backdrop)")
            onDismiss()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val drawerWidthPx = with(density) { (maxWidth * 0.85f).toPx() }
            var isEntered by remember { mutableStateOf(false) }
            var isExiting by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) { isEntered = true }

            val offsetXPx by animateFloatAsState(
                targetValue = when {
                    !isEntered -> drawerWidthPx
                    isExiting -> drawerWidthPx
                    else -> 0f
                },
                animationSpec = tween(300, easing = FastOutSlowInEasing)
            )

            LaunchedEffect(isExiting, offsetXPx) {
                if (isExiting && offsetXPx >= drawerWidthPx - 1f) {
                    onDismiss()
                }
            }

            fun dismissWithAnimation() {
                isExiting = true
            }

            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f))
                        .clickable {
                            DebugLog.click("CartDrawer backdrop")
                            dismissWithAnimation()
                        }
                )
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0.85f)
                        .align(Alignment.CenterEnd)
                        .offset { IntOffset(offsetXPx.roundToInt(), 0) }
                        .background(Color.White)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = t("cart.title", "Cart"),
                            style = MaterialTheme.typography.titleLarge,
                            color = EazColors.TextPrimary
                        )
                        IconButton(onClick = {
                            DebugLog.click("CartDrawer close")
                            dismissWithAnimation()
                        }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = t("common.close", "Close"),
                                tint = EazColors.TextPrimary
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        when {
                            loading -> {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = EazColors.Orange)
                                }
                            }
                            error != null -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = error ?: "Fehler",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = EazColors.TextSecondary
                                    )
                                }
                            }
                            cart == null || cart!!.lines.isEmpty() -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.ShoppingCart,
                                            contentDescription = null,
                                            tint = EazColors.TextSecondary,
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Text(
                                            text = t("cart.empty", "Your cart is empty"),
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = EazColors.TextSecondary
                                        )
                                    }
                                }
                            }
                            else -> {
                                val c = cart!!
                                val nearestDeadline = promoByHandle.values
                                    .filter { it.promoSlotApplies }
                                    .mapNotNull { it.displayEndsAt }
                                    .filter { it > System.currentTimeMillis() }
                                    .minOrNull()
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    if (nearestDeadline != null) {
                                        item(key = "mascot") {
                                            CartPromoMascotBanner(deadlineMs = nearestDeadline, t = t)
                                        }
                                    }
                                    items(c.lines, key = { it.id }) { line ->
                                        CartLineItem(
                                            line = line,
                                            promo = promoByHandle[line.productHandle]
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (cart != null && !cart!!.lines.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White)
                                .padding(16.dp)
                        ) {
                            val lines = cart!!.lines
                            val totalText = lines
                                .sumOf { line ->
                                    val p = promoByHandle[line.productHandle]
                                    val unit = when {
                                        p != null && p.promoSlotApplies && p.afterPrice != null -> p.afterPrice!!
                                        else -> line.priceAmount.toDoubleOrNull() ?: 0.0
                                    }
                                    unit * line.quantity
                                }
                                .let { "%.2f %s".format(it, lines.firstOrNull()?.currencyCode ?: "CHF") }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${t("cart.total", "Total")}: $totalText",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = EazColors.TextPrimary
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(EazColors.Orange)
                                        .clickable {
                                            val url = cart!!.checkoutUrl
                                            if (url.isNotBlank()) onCheckout(url)
                                        }
                                        .padding(horizontal = 20.dp, vertical = 12.dp)
                                ) {
                                    Text(
                                        text = t("cart.checkout", "Checkout"),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class PromoResolveRow(
    val displayEndsAt: Long?,
    val promoSlotApplies: Boolean,
    val beforePrice: Double?,
    val afterPrice: Double?,
    val nextWindow: Long?
)

private fun parsePromoResolve(json: JSONObject): Map<String, PromoResolveRow> {
    val arr = json.optJSONArray("items") ?: return emptyMap()
    val map = mutableMapOf<String, PromoResolveRow>()
    for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        val h = o.optString("handle", "").trim()
        if (h.isEmpty()) continue
        val pe = if (o.has("promotion_ends_at") && !o.isNull("promotion_ends_at")) o.optLong("promotion_ends_at") else null
        val nw = if (o.has("promo_next_window_starts_at") && !o.isNull("promo_next_window_starts_at")) {
            o.optLong("promo_next_window_starts_at")
        } else {
            null
        }
        map[h] = PromoResolveRow(
            displayEndsAt = pe?.takeIf { it > 0L },
            promoSlotApplies = o.optBoolean("promo_slot_applies"),
            beforePrice = if (o.has("before_price") && !o.isNull("before_price")) o.optDouble("before_price") else null,
            afterPrice = if (o.has("price") && !o.isNull("price")) o.optDouble("price") else null,
            nextWindow = nw?.takeIf { it > 0L }
        )
    }
    return map
}

@Composable
private fun CartPromoMascotBanner(
    deadlineMs: Long,
    t: (String, String) -> String
) {
    var now by remember(deadlineMs) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(deadlineMs) {
        while (now < deadlineMs) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }
    val left = (deadlineMs - now).coerceAtLeast(0L)
    val sec = left / 1000L
    val m = (sec % 3600) / 60
    val h = sec / 3600
    val timeLeft = if (h > 0L) "${h}h ${m}m" else "${m}m"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(EazColors.Orange.copy(alpha = 0.12f))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.drawable.ic_eazy_mascot),
            contentDescription = null,
            modifier = Modifier.size(40.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = t("eaz.cart.promo_mascot_title", "Promo price"),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = EazColors.Orange
            )
            Text(
                text = run {
                    val raw = t("eaz.cart.promo_mascot_body", "Ends in %s — checkout to keep this price.")
                    if (raw.contains("%s")) raw.replace("%s", timeLeft) else "$raw $timeLeft"
                },
                style = MaterialTheme.typography.bodySmall,
                color = EazColors.TextSecondary
            )
        }
    }
}

@Composable
private fun CartLineItem(
    line: ShopifyStorefrontCartApi.CartLine,
    promo: PromoResolveRow?
) {
    val showPromo = promo?.promoSlotApplies == true &&
        promo.beforePrice != null &&
        promo.afterPrice != null &&
        promo.beforePrice!! > promo.afterPrice!! + 1e-6
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFF5F5F5))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(line.imageUrl ?: "https://via.placeholder.com/80")
                .crossfade(true)
                .build(),
            contentDescription = null,
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(6.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = line.productTitle,
                style = MaterialTheme.typography.bodyMedium,
                color = EazColors.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = line.title,
                style = MaterialTheme.typography.bodySmall,
                color = EazColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            if (showPromo) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "%.2f %s".format(promo!!.afterPrice!!, line.currencyCode),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = EazColors.Orange
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "%.2f".format(promo.beforePrice!!),
                        style = MaterialTheme.typography.labelSmall,
                        color = EazColors.TextSecondary,
                        textDecoration = TextDecoration.LineThrough
                    )
                    Text(
                        text = " × ${line.quantity}",
                        style = MaterialTheme.typography.labelSmall,
                        color = EazColors.TextSecondary
                    )
                }
            } else {
                Text(
                    text = "${line.priceAmount} ${line.currencyCode} × ${line.quantity}",
                    style = MaterialTheme.typography.labelSmall,
                    color = EazColors.TextSecondary
                )
            }
        }
    }
}
