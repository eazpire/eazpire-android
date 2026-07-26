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
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.eazpire.creator.ui.modal.EazModalFooterSurface
import com.eazpire.creator.ui.modal.EazModalSheetLayout
import com.eazpire.creator.ui.modal.EazSideDrawer
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.eazpire.creator.R
import com.eazpire.creator.EazColors
import com.eazpire.creator.auth.AuthConfig
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

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
    val creatorApi = remember { CreatorApi(jwt = tokenStore?.getJwt()) }
    val scope = rememberCoroutineScope()
    val accessToken = tokenStore?.getAccessToken()
    val ownerId = tokenStore?.getOwnerId()?.trim()?.takeIf { it.isNotBlank() }

    var cart by remember { mutableStateOf<ShopifyStorefrontCartApi.CartResult?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var lineUpdating by remember { mutableStateOf(false) }
    var updateError by remember { mutableStateOf<String?>(null) }
    var promoByHandle by remember { mutableStateOf<Map<String, PromoResolveRow>>(emptyMap()) }
    var characterDiscountSubtitle by remember { mutableStateOf<String?>(null) }
    var characterDiscountApplying by remember { mutableStateOf(false) }
    var appliedCharacterDiscountCode by remember { mutableStateOf<String?>(null) }
    var characterDiscountError by remember { mutableStateOf<String?>(null) }
    var showGiftDialog by remember { mutableStateOf(false) }
    var giftCardsLoading by remember { mutableStateOf(false) }
    var giftCards by remember { mutableStateOf<List<CartGiftCardRow>>(emptyList()) }
    var giftManualCode by remember { mutableStateOf("") }
    var giftApplying by remember { mutableStateOf(false) }
    var giftError by remember { mutableStateOf<String?>(null) }

    fun applyCartResult(result: ShopifyStorefrontCartApi.CartResult?) {
        cart = result
        if (result == null) {
            cartStore.clear()
            AppCartStore.clear()
        } else {
            cartStore.cartId = result.cartId
            AppCartStore.setCount(result.itemCount)
        }
    }

    suspend fun setLineQuantity(lineId: String, quantity: Int) {
        val cartId = cartStore.cartId ?: return
        lineUpdating = true
        updateError = null
        try {
            val res = withContext(Dispatchers.IO) {
                api.updateLines(cartId, listOf(lineId to quantity))
            }
            if (res.ok) {
                applyCartResult(res.cart)
            } else {
                updateError = res.message?.takeIf { it.isNotBlank() }
                    ?: t("eaz.cart.update_error", "Could not update cart. Please try again.")
            }
        } catch (e: Exception) {
            updateError = e.message ?: t("eaz.cart.update_error", "Could not update cart. Please try again.")
        } finally {
            lineUpdating = false
        }
    }

    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        loading = true
        error = null
        updateError = null
        val cartId = cartStore.cartId
        if (cartId != null) {
            val loaded = withContext(Dispatchers.IO) { api.getCart(cartId) }
            if (loaded == null) {
                cartStore.clear()
                AppCartStore.clear()
                cart = null
                error = t("eaz.cart.load_error", "Could not load cart. Please try again.")
            } else {
                applyCartResult(loaded)
            }
        } else {
            cart = null
            AppCartStore.clear()
        }
        loading = false
    }

    LaunchedEffect(cart, ownerId, visible) {
        characterDiscountSubtitle = null
        characterDiscountError = null
        appliedCharacterDiscountCode = null
        if (!visible || ownerId.isNullOrBlank() || cart == null || cart!!.lines.isEmpty()) return@LaunchedEffect
        val state = withContext(Dispatchers.IO) {
            creatorApi.getArtifactsShopDiscountState(ownerId, AuthConfig.SHOP_DOMAIN)
        }
        if (state.optBoolean("ok", false) && state.optBoolean("active", false)) {
            val pct = state.optInt("next_discount_pct", 0)
            if (pct > 0) {
                val isSecond = state.optString("label", "") == "character_shop_second_order"
                val pctTemplate = t("creator.cart_discount.character_discount_pct", "{pct}% off your order")
                characterDiscountSubtitle = if (isSecond) {
                    t("creator.cart_discount.character_discount_second_order", "Second order today — 50% off")
                } else {
                    pctTemplate.replace("{pct}", pct.toString())
                }
            }
        }
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

    EazSideDrawer(
        onDismissRequest = onDismiss,
        widthFraction = 0.85f,
    ) { dismissAnimated ->
        EazModalSheetLayout(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(),
            header = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = t("cart.title", "Cart"),
                        style = MaterialTheme.typography.titleLarge,
                        color = EazColors.TextPrimary,
                    )
                    IconButton(onClick = {
                        DebugLog.click("CartDrawer close")
                        dismissAnimated()
                    }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = t("common.close", "Close"),
                            tint = EazColors.TextPrimary,
                        )
                    }
                }
            },
            body = {
                Box(modifier = Modifier.fillMaxSize()) {
                    when {
                        loading -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(color = EazColors.Orange)
                            }
                        }
                        error != null -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = error ?: "Fehler",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = EazColors.TextSecondary,
                                )
                            }
                        }
                        cart == null || cart!!.lines.isEmpty() -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Icon(
                                        Icons.Default.ShoppingCart,
                                        contentDescription = null,
                                        tint = EazColors.TextSecondary,
                                        modifier = Modifier.size(48.dp),
                                    )
                                    Text(
                                        text = t("cart.empty", "Your cart is empty"),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = EazColors.TextSecondary,
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
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                updateError?.let { msg ->
                                    item(key = "update-error") {
                                        Text(
                                            text = msg,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFFB91C1C),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color(0xFFFFE4E6))
                                                .padding(12.dp),
                                        )
                                    }
                                }
                                if (nearestDeadline != null) {
                                    item(key = "mascot") {
                                        CartPromoMascotBanner(deadlineMs = nearestDeadline, t = t)
                                    }
                                }
                                items(c.lines, key = { it.id }) { line ->
                                    CartLineItem(
                                        line = line,
                                        promo = promoByHandle[line.productHandle],
                                        updating = lineUpdating,
                                        onDecrease = {
                                            scope.launch {
                                                setLineQuantity(line.id, (line.quantity - 1).coerceAtLeast(0))
                                            }
                                        },
                                        onIncrease = {
                                            scope.launch {
                                                setLineQuantity(line.id, line.quantity + 1)
                                            }
                                        },
                                        onRemove = {
                                            scope.launch {
                                                setLineQuantity(line.id, 0)
                                            }
                                        },
                                        t = t,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            footer = {
                if (cart != null && !cart!!.lines.isEmpty()) {
                    EazModalFooterSurface(color = Color.White) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                                characterDiscountSubtitle?.let { subtitle ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(EazColors.Orange.copy(alpha = 0.1f))
                                            .clickable(enabled = !characterDiscountApplying && appliedCharacterDiscountCode == null) {
                                                val oid = ownerId ?: return@clickable
                                                characterDiscountApplying = true
                                                characterDiscountError = null
                                                scope.launch {
                                                    try {
                                                        val res = withContext(Dispatchers.IO) {
                                                            creatorApi.postArtifactsShopDiscountApply(oid, AuthConfig.SHOP_DOMAIN)
                                                        }
                                                        val code = res.optString("discount_code", "").trim()
                                                        if (res.optBoolean("ok", false) && code.isNotBlank()) {
                                                            appliedCharacterDiscountCode = code
                                                            characterDiscountSubtitle = null
                                                        } else {
                                                            characterDiscountError = t(
                                                                "creator.cart_discount.character_discount_unavailable",
                                                                "No character shop bonus available today",
                                                            )
                                                        }
                                                    } catch (_: Exception) {
                                                        characterDiscountError = t(
                                                            "creator.cart_discount.character_discount_apply_error",
                                                            "Character discount could not be applied. Please try again.",
                                                        )
                                                    } finally {
                                                        characterDiscountApplying = false
                                                    }
                                                }
                                            }
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                t("creator.cart_discount.character_shop_bonus", "Character shop bonus"),
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = EazColors.Orange,
                                            )
                                            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = EazColors.TextSecondary)
                                        }
                                        Text(
                                            if (appliedCharacterDiscountCode != null) {
                                                t("creator.cart_discount.applied", "Applied")
                                            } else {
                                                t("creator.cart_discount.apply", "Apply")
                                            },
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = EazColors.Orange,
                                        )
                                    }
                                }
                                appliedCharacterDiscountCode?.let { code ->
                                    Text(
                                        "${t("creator.cart_discount.code_applied", "Discount code")}: $code",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = EazColors.TextSecondary,
                                    )
                                }
                                characterDiscountError?.let { err ->
                                    Text(err, style = MaterialTheme.typography.bodySmall, color = Color(0xFFDC2626))
                                }
                            if (!ownerId.isNullOrBlank()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFFF5F5F5))
                                        .clickable(enabled = !giftApplying) {
                                            showGiftDialog = true
                                            giftError = null
                                            giftCardsLoading = true
                                            scope.launch {
                                                try {
                                                    val res = withContext(Dispatchers.IO) {
                                                        creatorApi.getCustomerGiftCards(
                                                            ownerId,
                                                            AuthConfig.SHOP_DOMAIN
                                                        )
                                                    }
                                                    val arr = res.optJSONArray("gift_cards") ?: JSONArray()
                                                    val list = mutableListOf<CartGiftCardRow>()
                                                    for (i in 0 until arr.length()) {
                                                        val gc = arr.optJSONObject(i) ?: continue
                                                        if (!gc.optBoolean("enabled", true)) continue
                                                        val balance = gc.optDouble("balance", 0.0)
                                                        if (balance <= 0) continue
                                                        val id = gc.opt("id")?.toString()?.trim().orEmpty()
                                                        if (id.isEmpty()) continue
                                                        list.add(
                                                            CartGiftCardRow(
                                                                id = id,
                                                                label = gc.optString("last_characters", "")
                                                                    .ifBlank { id.takeLast(4) },
                                                                balance = balance,
                                                                currency = gc.optString("currency", "CHF"),
                                                                code = gc.optString("code", "").trim()
                                                                    .takeIf { it.length > 8 }
                                                            )
                                                        )
                                                    }
                                                    giftCards = list
                                                } catch (_: Exception) {
                                                    giftCards = emptyList()
                                                } finally {
                                                    giftCardsLoading = false
                                                }
                                            }
                                        }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Icon(
                                        Icons.Default.CardGiftcard,
                                        contentDescription = null,
                                        tint = EazColors.Orange,
                                        modifier = Modifier.size(22.dp),
                                    )
                                    Text(
                                        t("eaz.cart.apply_gift_card", "Apply gift card / code"),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = EazColors.TextPrimary,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                giftError?.let { err ->
                                    Text(err, style = MaterialTheme.typography.bodySmall, color = Color(0xFFDC2626))
                                }
                            }
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
                                            var url = cart!!.checkoutUrl
                                            val code = appliedCharacterDiscountCode
                                            if (!code.isNullOrBlank() && url.isNotBlank()) {
                                                val redirect = URLEncoder.encode(url, "UTF-8")
                                                url = "https://www.eazpire.com/discount/${URLEncoder.encode(code, "UTF-8")}?redirect=$redirect"
                                            }
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
            },
        )
    }

    suspend fun applyGiftCodeToCheckout(code: String) {
        val c = cart ?: return
        giftApplying = true
        giftError = null
        try {
            val items = c.lines.mapNotNull { line ->
                val vid = line.variantId.filter { it.isDigit() }.toLongOrNull() ?: return@mapNotNull null
                vid to line.quantity
            }
            if (items.isEmpty()) {
                giftError = t("eaz.cart.empty", "The cart is empty.")
                return
            }
            val res = withContext(Dispatchers.IO) {
                creatorApi.applyGiftCardStorefront(
                    shop = AuthConfig.SHOP_DOMAIN,
                    giftCardCodes = listOf(code),
                    cartItems = items
                )
            }
            val checkout = res.optString("checkout_url", "").trim()
            if (res.optBoolean("ok", false) && checkout.isNotBlank()) {
                showGiftDialog = false
                onCheckout(checkout)
            } else {
                giftError = res.optString("message").ifBlank {
                    t(
                        "eaz.cart.gift_card_apply_failed",
                        "Gift card could not be applied. Please try again or enter the code at checkout."
                    )
                }
            }
        } catch (e: Exception) {
            giftError = e.message ?: t(
                "eaz.cart.gift_card_apply_failed",
                "Gift card could not be applied. Please try again or enter the code at checkout."
            )
        } finally {
            giftApplying = false
        }
    }

    if (showGiftDialog) {
        AlertDialog(
            onDismissRequest = { if (!giftApplying) showGiftDialog = false },
            title = {
                Text(t("eaz.cart.apply_gift_card", "Apply gift card / code"))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (giftCardsLoading) {
                        CircularProgressIndicator(
                            color = EazColors.Orange,
                            modifier = Modifier.size(28.dp)
                        )
                    } else if (giftCards.isEmpty()) {
                        Text(
                            t("eaz.cart.no_gift_cards", "No gift cards with balance found."),
                            style = MaterialTheme.typography.bodySmall,
                            color = EazColors.TextSecondary
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(giftCards, key = { it.id }) { gc ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFF5F5F5))
                                        .clickable(enabled = !giftApplying) {
                                            scope.launch {
                                                val code = gc.code ?: run {
                                                    val oid = ownerId ?: return@launch
                                                    val codeRes = withContext(Dispatchers.IO) {
                                                        creatorApi.getGiftCardCode(
                                                            gc.id,
                                                            oid,
                                                            AuthConfig.SHOP_DOMAIN
                                                        )
                                                    }
                                                    if (!codeRes.optBoolean("ok", false)) {
                                                        giftError = codeRes.optString("message").ifBlank {
                                                            t(
                                                                "eaz.cart.gift_card_code_error",
                                                                "This gift card could not be applied."
                                                            )
                                                        }
                                                        return@launch
                                                    }
                                                    codeRes.optString("code", "").trim()
                                                }
                                                if (code.isBlank()) {
                                                    giftError = t(
                                                        "eaz.cart.gift_card_code_error",
                                                        "This gift card could not be applied."
                                                    )
                                                    return@launch
                                                }
                                                applyGiftCodeToCheckout(code)
                                            }
                                        }
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "•••• ${gc.label}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        "%.2f %s".format(gc.balance, gc.currency),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = EazColors.TextSecondary
                                    )
                                }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = giftManualCode,
                        onValueChange = { giftManualCode = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !giftApplying,
                        label = {
                            Text(t("eaz.cart.gift_code_placeholder", "Enter discount or gift code"))
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                val code = giftManualCode.trim()
                                if (code.isNotEmpty() && !giftApplying) {
                                    scope.launch { applyGiftCodeToCheckout(code) }
                                }
                            }
                        )
                    )
                    giftError?.let { err ->
                        Text(err, style = MaterialTheme.typography.bodySmall, color = Color(0xFFDC2626))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val code = giftManualCode.trim()
                        if (code.isEmpty() || giftApplying) return@TextButton
                        scope.launch { applyGiftCodeToCheckout(code) }
                    },
                    enabled = !giftApplying && giftManualCode.isNotBlank()
                ) {
                    Text(
                        if (giftApplying) t("eaz.cart.applying", "Applying…")
                        else t("eaz.cart.apply", "Apply")
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { if (!giftApplying) showGiftDialog = false }) {
                    Text(t("common.close", "Close"))
                }
            }
        )
    }
}

private data class CartGiftCardRow(
    val id: String,
    val label: String,
    val balance: Double,
    val currency: String,
    val code: String?
)

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
    promo: PromoResolveRow?,
    updating: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    onRemove: () -> Unit,
    t: (String, String) -> String,
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
                }
            } else {
                Text(
                    text = "${line.priceAmount} ${line.currencyCode}",
                    style = MaterialTheme.typography.labelSmall,
                    color = EazColors.TextSecondary
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(
                    onClick = onDecrease,
                    enabled = !updating,
                    modifier = Modifier
                        .size(40.dp)
                        .semantics {
                            contentDescription = t("eaz.cart.decrease_qty", "Decrease quantity")
                        },
                ) {
                    Icon(
                        Icons.Default.Remove,
                        contentDescription = null,
                        tint = EazColors.TextPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Text(
                    text = "${line.quantity}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = EazColors.TextPrimary,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                IconButton(
                    onClick = onIncrease,
                    enabled = !updating,
                    modifier = Modifier
                        .size(40.dp)
                        .semantics {
                            contentDescription = t("eaz.cart.increase_qty", "Increase quantity")
                        },
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        tint = EazColors.TextPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = onRemove,
                    enabled = !updating,
                    modifier = Modifier
                        .size(40.dp)
                        .semantics {
                            contentDescription = t("eaz.cart.remove_item", "Remove item")
                        },
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        tint = EazColors.TextSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
