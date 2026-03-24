package com.eazpire.creator.ui.vouchers

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.api.ShopifyProductsApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency
import java.util.Locale

private const val MIN_POSTCARD_BALANCE = 50.0

/** Same pattern as theme `gift-card-detail.js` email validation. */
private val GIFT_CARD_EMAIL_REGEX = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

/** Match selection `product_ids` to catalog rows (REST numeric vs GraphQL gid). */
private fun normalizeShopifyProductId(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    val s = raw.trim()
    val m = Regex("Product/(\\d+)", RegexOption.IGNORE_CASE).find(s)
    if (m != null) return m.groupValues[1]
    return s
}

private enum class DeliveryMode { SELF_PRINT, EMAIL, POSTCARD }

private data class ShopifyVariantLine(
    val id: String,
    val title: String,
    val price: Double,
    val image: String?
)

private data class ProductDetailTarget(
    val productId: String,
    val handle: String,
    val fromPicker: Boolean
)

private data class ShopifyProductRow(
    val id: String,
    val title: String,
    val handle: String,
    val image: String?,
    val price: Double,
    val currency: String,
    val variantId: String?,
    val variants: List<ShopifyVariantLine> = emptyList()
)

private fun ShopifyProductRow.displayPrice(variantId: String?): Double {
    val v = variants.find { it.id == variantId } ?: variants.firstOrNull()
    return v?.price ?: price
}

private fun ShopifyProductRow.displayImage(variantId: String?): String? {
    val v = variants.find { it.id == variantId } ?: variants.firstOrNull()
    return v?.image?.takeIf { !it.isNullOrBlank() } ?: image
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GiftCardDetailNativeModal(
    giftCardId: String?,
    title: String,
    onDismiss: () -> Unit,
    customerId: String,
    shop: String,
    api: CreatorApi,
    t: (String, String) -> String
) {
    if (giftCardId.isNullOrBlank()) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val shopifyDetailApi = remember { ShopifyProductsApi() }
    val recommendedScroll = rememberScrollState()

    var loading by remember(giftCardId) { mutableStateOf(true) }
    var loadError by remember(giftCardId) { mutableStateOf<String?>(null) }
    var giftCard by remember(giftCardId) { mutableStateOf<JSONObject?>(null) }
    var isBuyer by remember(giftCardId) { mutableStateOf(false) }

    var productIds by remember(giftCardId) { mutableStateOf<Set<String>>(emptySet()) }
    var catalogProducts by remember(giftCardId) { mutableStateOf<List<ShopifyProductRow>>(emptyList()) }

    var templateExists by remember(giftCardId) { mutableStateOf(false) }
    var templateSent by remember(giftCardId) { mutableStateOf(false) }
    var deliveryMode by remember(giftCardId) { mutableStateOf(DeliveryMode.EMAIL) }
    var senderName by remember(giftCardId) { mutableStateOf("") }
    var recipientEmail by remember(giftCardId) { mutableStateOf("") }
    var message by remember(giftCardId) { mutableStateOf("") }
    var imageUrl by remember(giftCardId) { mutableStateOf("") }
    var imagePrompt by remember(giftCardId) { mutableStateOf("") }
    var postcardFirst by remember(giftCardId) { mutableStateOf("") }
    var postcardLast by remember(giftCardId) { mutableStateOf("") }
    var postcardLine1 by remember(giftCardId) { mutableStateOf("") }
    var postcardLine2 by remember(giftCardId) { mutableStateOf("") }
    var postcardZip by remember(giftCardId) { mutableStateOf("") }
    var postcardCity by remember(giftCardId) { mutableStateOf("") }
    var postcardCountry by remember(giftCardId) { mutableStateOf("") }
    var postcardState by remember(giftCardId) { mutableStateOf("") }
    var postcardSize by remember(giftCardId) { mutableStateOf("6x4") }

    var textGenLabel by remember(giftCardId) { mutableStateOf("") }
    var imageGenLabel by remember(giftCardId) { mutableStateOf("") }

    var showFullCode by remember(giftCardId) { mutableStateOf(false) }
    var showProductPicker by remember(giftCardId) { mutableStateOf(false) }
    var pickerSearch by remember(giftCardId) { mutableStateOf("") }
    var pickerSelection by remember(giftCardId) { mutableStateOf<Set<String>>(emptySet()) }

    var variantByProductId by remember(giftCardId) { mutableStateOf(mapOf<String, String>()) }
    var productDetailTarget by remember(giftCardId) { mutableStateOf<ProductDetailTarget?>(null) }
    var detailLoading by remember(giftCardId) { mutableStateOf(false) }
    var detailLoadError by remember(giftCardId) { mutableStateOf<String?>(null) }
    var detailData by remember(giftCardId) { mutableStateOf<ShopifyProductsApi.ProductDetail?>(null) }
    var selectedVariantIdInModal by remember(giftCardId) { mutableStateOf<Long?>(null) }

    var saving by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }
            if (bytes == null || bytes.isEmpty()) {
                snackbarHostState.showSnackbar(t("creator.gift_cards.image_read_error", "Could not read image"))
                return@launch
            }
            saving = true
            try {
                val res = api.generateGiftCardImageMultipart(
                    prompt = imagePrompt.ifBlank { "gift card image" },
                    giftCardId = giftCardId,
                    customerId = customerId,
                    imageBytes = bytes,
                    fileName = "upload.jpg"
                )
                if (res.optBoolean("ok", false)) {
                    imageUrl = res.optString("image_url", imageUrl)
                    snackbarHostState.showSnackbar(t("creator.gift_cards.image_generated", "Image ready"))
                } else {
                    snackbarHostState.showSnackbar(res.optString("error", res.optString("message", "Error")))
                }
            } catch (e: Exception) {
                snackbarHostState.showSnackbar(e.message ?: "Error")
            } finally {
                saving = false
            }
        }
    }

    fun shopDomain(): String =
        if (shop.contains('.')) shop else "$shop.myshopify.com"

    fun fmtMoney(amount: Double, currency: String): String = try {
        NumberFormat.getCurrencyInstance(Locale.getDefault()).apply {
            maximumFractionDigits = 2
            this.currency = Currency.getInstance(currency)
        }.format(amount)
    } catch (_: Exception) {
        "%.2f %s".format(Locale.US, amount, currency)
    }

    fun fmtDate(iso: String?): String {
        if (iso.isNullOrBlank()) return "—"
        return try {
            val instant = Instant.parse(iso.replace(" ", "T"))
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                .withZone(ZoneId.systemDefault())
                .format(instant)
        } catch (_: Exception) {
            iso
        }
    }

    LaunchedEffect(giftCardId) {
        loading = true
        loadError = null
        try {
            val gRes = withContext(Dispatchers.IO) { api.getGiftCard(giftCardId, shop) }
            if (!gRes.optBoolean("ok", false)) {
                loadError = gRes.optString("message", gRes.optString("error", "Error"))
                loading = false
                return@LaunchedEffect
            }
            val gc = gRes.optJSONObject("gift_card") ?: JSONObject()
            giftCard = gc

            val bRes = withContext(Dispatchers.IO) {
                api.checkGiftCardBuyer(giftCardId, customerId, shop)
            }
            isBuyer = bRes.optBoolean("is_buyer", false)

            val selRes = withContext(Dispatchers.IO) {
                api.getGiftCardSelection(giftCardId, customerId)
            }
            val ids = mutableListOf<String>()
            if (selRes.optBoolean("ok", false)) {
                val arr = selRes.optJSONArray("product_ids") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val raw = arr.opt(i)?.toString() ?: continue
                    val n = normalizeShopifyProductId(raw)
                    if (n.isNotBlank()) ids.add(n)
                }
            }
            productIds = ids.toSet()

            val prodRes = withContext(Dispatchers.IO) { api.getShopifyProducts(shop) }
            if (prodRes.optBoolean("ok", false)) {
                val arr = prodRes.optJSONArray("products") ?: JSONArray()
                val rows = mutableListOf<ShopifyProductRow>()
                for (i in 0 until arr.length()) {
                    val p = arr.optJSONObject(i) ?: continue
                    val varArr = p.optJSONArray("variants")
                    val vlines = mutableListOf<ShopifyVariantLine>()
                    if (varArr != null) {
                        for (j in 0 until varArr.length()) {
                            val v = varArr.optJSONObject(j) ?: continue
                            val vid = normalizeShopifyProductId(
                                v.optString("id", "").ifBlank { v.optLong("id").toString() }
                            )
                            if (vid.isBlank()) continue
                            val avail = v.optBoolean("available", true)
                            if (!avail) continue
                            vlines.add(
                                ShopifyVariantLine(
                                    id = vid,
                                    title = v.optString("title", "Default"),
                                    price = v.optString("price", "0").toDoubleOrNull() ?: 0.0,
                                    image = v.optString("image").takeIf { it.isNotBlank() }
                                )
                            )
                        }
                    }
                    rows.add(
                        ShopifyProductRow(
                            id = normalizeShopifyProductId(
                                p.optString("id", "").ifBlank { p.optLong("id").toString() }
                            ),
                            title = p.optString("title", ""),
                            handle = p.optString("handle", ""),
                            image = p.optString("image").takeIf { it.isNotBlank() },
                            price = p.optDouble("price", 0.0),
                            currency = p.optString("currency", "EUR"),
                            variantId = p.optString("variantId").takeIf { it.isNotBlank() },
                            variants = vlines
                        )
                    )
                }
                catalogProducts = rows
                variantByProductId = productIds.mapNotNull { pid ->
                    val row = rows.find { it.id == pid } ?: return@mapNotNull null
                    val vid = row.variantId?.takeIf { it.isNotBlank() }
                        ?: row.variants.firstOrNull()?.id
                    if (vid.isNullOrBlank()) null else pid to vid
                }.toMap()
            }

            if (isBuyer) {
                val tRes = withContext(Dispatchers.IO) { api.getGiftCardEmailTemplate(giftCardId) }
                if (tRes.optBoolean("ok", false) && tRes.optBoolean("exists", false)) {
                    val tpl = tRes.optJSONObject("template") ?: JSONObject()
                    templateExists = true
                    templateSent = tpl.optBoolean("is_sent", false)
                    senderName = tpl.optString("sender_name", "")
                    recipientEmail = tpl.optString("recipient_email", "")
                    message = tpl.optString("message", "")
                    imageUrl = tpl.optString("image_url", "")
                    imagePrompt = tpl.optString("image_generation_prompt", "")
                    postcardSize = tpl.optString("postcard_size", "6x4").ifBlank { "6x4" }
                    val dm = tpl.optString("delivery_mode", "email").lowercase(Locale.US)
                    deliveryMode = when (dm) {
                        "self_print" -> DeliveryMode.SELF_PRINT
                        "postcard" -> DeliveryMode.POSTCARD
                        else -> DeliveryMode.EMAIL
                    }
                    val pr = tpl.optJSONObject("postcard_recipient")
                    if (pr != null) {
                        postcardFirst = pr.optString("first_name", "")
                        postcardLast = pr.optString("last_name", "")
                        postcardLine1 = pr.optString("address_line1", "")
                        postcardLine2 = pr.optString("address_line2", "")
                        postcardZip = pr.optString("postal_or_zip", "")
                        postcardCity = pr.optString("city", "")
                        postcardCountry = pr.optString("country", "")
                        postcardState = pr.optString("province_or_state", "")
                    }
                }

                val tc = withContext(Dispatchers.IO) {
                    api.getGiftCardGenerationCount(giftCardId, shop, "text")
                }
                if (tc.optBoolean("ok", false)) {
                    textGenLabel = if (tc.optBoolean("limit_reached", false)) {
                        t("creator.gift_cards.limit_reached", "Limit reached (5/5)")
                    } else {
                        "${tc.optInt("count", 0)} / ${tc.optInt("limit", 5)}"
                    }
                }
                val ic = withContext(Dispatchers.IO) {
                    api.getGiftCardGenerationCount(giftCardId, shop, "image")
                }
                if (ic.optBoolean("ok", false)) {
                    imageGenLabel = if (ic.optBoolean("limit_reached", false)) {
                        t("creator.gift_cards.limit_reached", "Limit reached (5/5)")
                    } else {
                        "${ic.optInt("count", 0)} / ${ic.optInt("limit", 5)}"
                    }
                }
            }
        } catch (e: Exception) {
            loadError = e.message ?: "Error"
        } finally {
            loading = false
        }
    }

    LaunchedEffect(productDetailTarget) {
        val target = productDetailTarget
        if (target == null) {
            detailData = null
            detailLoadError = null
            selectedVariantIdInModal = null
            return@LaunchedEffect
        }
        detailLoading = true
        detailLoadError = null
        detailData = null
        val loaded = withContext(Dispatchers.IO) {
            shopifyDetailApi.getProductByHandle(target.handle)
        }
        detailLoading = false
        if (loaded == null) {
            detailLoadError = t("creator.gift_cards.product_load_error", "Could not load product details.")
            return@LaunchedEffect
        }
        detailData = loaded
        val existing = variantByProductId[target.productId]?.toLongOrNull()
        val firstOk = loaded.variants.firstOrNull { it.available }?.id
            ?: loaded.variants.firstOrNull()?.id
        selectedVariantIdInModal = when {
            existing != null && loaded.variants.any { it.id == existing } -> existing
            else -> firstOk
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text(title, maxLines = 1) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                )
            }
        ) { padding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                when {
                    loading -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    loadError != null -> {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(loadError!!, color = EazColors.TextSecondary)
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = onDismiss) {
                                Text(t("creator.common.close", "Close"))
                            }
                        }
                    }
                    giftCard != null -> {
                        val gc = giftCard!!
                        val balance = gc.optDouble("balance", 0.0)
                        val currency = gc.optString("currency", "EUR")
                        val masked = gc.optString("masked_code").ifBlank {
                            "****${gc.optString("last_characters", "")}"
                        }
                        val fullCode = gc.optString("code").ifBlank { null } ?: masked
                        val postcardEligible = balance >= MIN_POSTCARD_BALANCE

                        Column(
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp)
                        ) {
                            Text(
                                fmtMoney(balance, currency),
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (showFullCode) fullCode else masked,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { showFullCode = !showFullCode }) {
                                    Icon(
                                        if (showFullCode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = null
                                    )
                                }
                            }
                            gc.optString("expires_on").takeIf { it.isNotBlank() }?.let {
                                Text(
                                    "${t("creator.gift_cards.expires", "Expires")}: ${fmtDate(it)}",
                                    color = EazColors.TextSecondary,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }

                            Spacer(Modifier.height(24.dp))
                            Text(
                                t("creator.gift_cards.recommended_products", "Recommended products"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(8.dp))
                            val selectedRows = catalogProducts.filter { it.id in productIds }
                            if (selectedRows.isEmpty()) {
                                Text(
                                    t("creator.gift_cards.no_products_selected", "No products selected yet."),
                                    color = EazColors.TextSecondary
                                )
                            } else {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        Modifier.horizontalScroll(recommendedScroll),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        selectedRows.forEach { p ->
                                            val vid = variantByProductId[p.id]
                                            Box(Modifier.width(152.dp)) {
                                                ProductTile(
                                                    p = p,
                                                    selectedVariantId = vid,
                                                    onOpen = {
                                                        productDetailTarget = ProductDetailTarget(
                                                            productId = p.id,
                                                            handle = p.handle,
                                                            fromPicker = false
                                                        )
                                                    },
                                                    fmtMoney = { a, c -> fmtMoney(a, c) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            if (isBuyer && !templateSent) {
                                OutlinedButton(
                                    onClick = {
                                        pickerSelection = productIds
                                        showProductPicker = true
                                    },
                                    modifier = Modifier.padding(top = 8.dp)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = null)
                                    Spacer(Modifier.size(8.dp))
                                    Text(t("creator.gift_cards.edit_product_selection", "Edit product selection"))
                                }
                            }

                            if (isBuyer) {
                                Spacer(Modifier.height(24.dp))
                                Text(
                                    t("creator.gift_cards.send_gift_card", "Send Gift Card"),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (templateSent) {
                                    Text(
                                        t("creator.gift_cards.email_sent_notice", "This gift card was already sent."),
                                        color = EazColors.TextSecondary,
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                } else {
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        t("creator.gift_cards.delivery_mode_label", "Delivery mode"),
                                        fontWeight = FontWeight.Medium
                                    )
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.padding(top = 8.dp)
                                    ) {
                                        FilterChip(
                                            selected = deliveryMode == DeliveryMode.SELF_PRINT,
                                            onClick = { deliveryMode = DeliveryMode.SELF_PRINT },
                                            label = { Text(t("creator.gift_cards.delivery_mode_self_print", "Self print")) }
                                        )
                                        FilterChip(
                                            selected = deliveryMode == DeliveryMode.EMAIL,
                                            onClick = { deliveryMode = DeliveryMode.EMAIL },
                                            label = { Text(t("creator.gift_cards.delivery_mode_email", "Email")) }
                                        )
                                        FilterChip(
                                            selected = deliveryMode == DeliveryMode.POSTCARD,
                                            onClick = {
                                                if (postcardEligible) deliveryMode = DeliveryMode.POSTCARD
                                            },
                                            enabled = postcardEligible,
                                            label = { Text(t("creator.gift_cards.delivery_mode_postcard", "Postcard")) }
                                        )
                                    }
                                    if (!postcardEligible) {
                                        Text(
                                            t(
                                                "creator.gift_cards.postcard_unavailable_under_50",
                                                "Postcard delivery is available from EUR 50 gift card value."
                                            ),
                                            fontSize = 12.sp,
                                            color = EazColors.TextSecondary,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }

                                    Spacer(Modifier.height(12.dp))
                                    OutlinedTextField(
                                        value = senderName,
                                        onValueChange = { senderName = it },
                                        label = { Text(t("creator.gift_cards.sender_name_label", "Sender name")) },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                    if (deliveryMode == DeliveryMode.EMAIL) {
                                        Spacer(Modifier.height(8.dp))
                                        OutlinedTextField(
                                            value = recipientEmail,
                                            onValueChange = { recipientEmail = it },
                                            label = { Text(t("creator.gift_cards.recipient_email_label", "Recipient email")) },
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true
                                        )
                                    }
                                    if (deliveryMode == DeliveryMode.POSTCARD) {
                                        Spacer(Modifier.height(8.dp))
                                        PostcardFields(
                                            first = postcardFirst,
                                            last = postcardLast,
                                            line1 = postcardLine1,
                                            line2 = postcardLine2,
                                            zip = postcardZip,
                                            city = postcardCity,
                                            country = postcardCountry,
                                            state = postcardState,
                                            size = postcardSize,
                                            onChange = { f, l, a1, a2, z, c, co, st, sz ->
                                                postcardFirst = f
                                                postcardLast = l
                                                postcardLine1 = a1
                                                postcardLine2 = a2
                                                postcardZip = z
                                                postcardCity = c
                                                postcardCountry = co
                                                postcardState = st
                                                postcardSize = sz
                                            },
                                            t = t
                                        )
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = message,
                                        onValueChange = { message = it },
                                        label = { Text(t("creator.gift_cards.message_label", "Message")) },
                                        modifier = Modifier.fillMaxWidth(),
                                        minLines = 4
                                    )
                                    if (textGenLabel.isNotBlank()) {
                                        Text(textGenLabel, fontSize = 12.sp, color = EazColors.TextSecondary)
                                    }
                                    Row(Modifier.padding(top = 4.dp)) {
                                        OutlinedButton(
                                            onClick = {
                                                scope.launch {
                                                    saving = true
                                                    try {
                                                        val res = api.generateGiftCardText(
                                                            prompt = message.ifBlank {
                                                                t(
                                                                    "creator.gift_cards.default_text_prompt",
                                                                    "Write a warm gift card message."
                                                                )
                                                            },
                                                            giftCardId = giftCardId,
                                                            senderName = senderName,
                                                            recipientName = recipientEmail
                                                        )
                                                        if (res.optBoolean("ok", false)) {
                                                            message = res.optString("text", message)
                                                        } else {
                                                            snackbarHostState.showSnackbar(
                                                                res.optString("error", res.optString("message", "Error"))
                                                            )
                                                        }
                                                    } catch (e: Exception) {
                                                        snackbarHostState.showSnackbar(e.message ?: "Error")
                                                    } finally {
                                                        saving = false
                                                    }
                                                }
                                            },
                                            enabled = !saving
                                        ) {
                                            Text(t("creator.gift_cards.generate_message", "Generate message"))
                                        }
                                    }

                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        t("creator.gift_cards.image_optional", "Image (optional)"),
                                        fontWeight = FontWeight.Medium
                                    )
                                    OutlinedTextField(
                                        value = imagePrompt,
                                        onValueChange = { imagePrompt = it },
                                        label = { Text(t("creator.gift_cards.image_prompt", "Image prompt")) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    if (imageGenLabel.isNotBlank()) {
                                        Text(imageGenLabel, fontSize = 12.sp, color = EazColors.TextSecondary)
                                    }
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.padding(top = 8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                scope.launch {
                                                    saving = true
                                                    try {
                                                        val res = api.generateGiftCardImageJson(
                                                            prompt = imagePrompt.ifBlank { "gift card" },
                                                            giftCardId = giftCardId,
                                                            customerId = customerId,
                                                            imageUrl = imageUrl.takeIf { it.isNotBlank() }
                                                        )
                                                        if (res.optBoolean("ok", false)) {
                                                            imageUrl = res.optString("image_url", imageUrl)
                                                        } else {
                                                            snackbarHostState.showSnackbar(
                                                                res.optString("error", res.optString("message", "Error"))
                                                            )
                                                        }
                                                    } catch (e: Exception) {
                                                        snackbarHostState.showSnackbar(e.message ?: "Error")
                                                    } finally {
                                                        saving = false
                                                    }
                                                }
                                            },
                                            enabled = !saving
                                        ) {
                                            Text(t("creator.gift_cards.generate_image", "Generate image"))
                                        }
                                        OutlinedButton(onClick = { imagePicker.launch("image/*") }) {
                                            Text(t("creator.gift_cards.upload_image", "Upload image"))
                                        }
                                    }
                                    if (imageUrl.isNotBlank()) {
                                        AsyncImage(
                                            model = imageUrl,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(180.dp)
                                                .clip(RoundedCornerShape(8.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                    }

                                    Spacer(Modifier.height(16.dp))
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                if (deliveryMode == DeliveryMode.EMAIL) {
                                                    val em = recipientEmail.trim()
                                                    if (em.isBlank()) {
                                                        snackbarHostState.showSnackbar(
                                                            t("creator.gift_cards.enter_recipient_email", "Please enter the recipient email.")
                                                        )
                                                        return@launch
                                                    }
                                                    if (!GIFT_CARD_EMAIL_REGEX.matches(em)) {
                                                        snackbarHostState.showSnackbar(
                                                            t("creator.gift_cards.enter_valid_email", "Please enter a valid email address.")
                                                        )
                                                        return@launch
                                                    }
                                                }
                                                if (deliveryMode == DeliveryMode.POSTCARD) {
                                                    val ok = postcardFirst.isNotBlank() && postcardLast.isNotBlank() &&
                                                        postcardLine1.isNotBlank() && postcardZip.isNotBlank() &&
                                                        postcardCity.isNotBlank() && postcardCountry.isNotBlank()
                                                    if (!ok) {
                                                        snackbarHostState.showSnackbar(
                                                            t("creator.gift_cards.postcard_incomplete", "Complete postcard address")
                                                        )
                                                        return@launch
                                                    }
                                                }
                                                saving = true
                                                try {
                                                    val body = JSONObject()
                                                        .put("gift_card_id", giftCardId)
                                                        .put("sender_name", senderName)
                                                        .put(
                                                            "recipient_email",
                                                            if (deliveryMode == DeliveryMode.EMAIL) {
                                                                recipientEmail.trim()
                                                            } else {
                                                                JSONObject.NULL
                                                            }
                                                        )
                                                        .put("message", message)
                                                        .put("image_url", imageUrl.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
                                                        .put("image_generation_prompt", imagePrompt.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
                                                        .put(
                                                            "delivery_mode",
                                                            when (deliveryMode) {
                                                                DeliveryMode.SELF_PRINT -> "self_print"
                                                                DeliveryMode.EMAIL -> "email"
                                                                DeliveryMode.POSTCARD -> "postcard"
                                                            }
                                                        )
                                                        .put("shop", shopDomain())
                                                    if (deliveryMode == DeliveryMode.POSTCARD) {
                                                        body.put(
                                                            "postcard_recipient",
                                                            JSONObject()
                                                                .put("first_name", postcardFirst)
                                                                .put("last_name", postcardLast)
                                                                .put("address_line1", postcardLine1)
                                                                .put("address_line2", postcardLine2)
                                                                .put("postal_or_zip", postcardZip)
                                                                .put("city", postcardCity)
                                                                .put("country", postcardCountry)
                                                                .put("province_or_state", postcardState)
                                                        )
                                                        body.put("postcard_size", postcardSize)
                                                    } else {
                                                        body.put("postcard_recipient", JSONObject.NULL)
                                                        body.put("postcard_size", JSONObject.NULL)
                                                    }
                                                    val res = api.saveGiftCardEmailTemplate(body)
                                                    if (res.optBoolean("ok", false)) {
                                                        snackbarHostState.showSnackbar(
                                                            t("creator.gift_cards.email_template_saved", "Email template saved")
                                                        )
                                                        templateExists = true
                                                    } else {
                                                        snackbarHostState.showSnackbar(
                                                            res.optString("error", res.optString("message", "Error"))
                                                        )
                                                    }
                                                } catch (e: Exception) {
                                                    snackbarHostState.showSnackbar(e.message ?: "Error")
                                                } finally {
                                                    saving = false
                                                }
                                            }
                                        },
                                        enabled = !saving && !templateSent,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(t("creator.gift_cards.save_template", "Save"))
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    if (deliveryMode != DeliveryMode.SELF_PRINT) {
                                        Button(
                                            onClick = {
                                                scope.launch {
                                                    sending = true
                                                    try {
                                                        val res = when (deliveryMode) {
                                                            DeliveryMode.POSTCARD -> api.sendGiftCardPostcard(giftCardId, shop)
                                                            else -> api.sendGiftCardEmail(giftCardId, shop)
                                                        }
                                                        if (res.optBoolean("ok", false)) {
                                                            snackbarHostState.showSnackbar(
                                                                if (deliveryMode == DeliveryMode.POSTCARD) {
                                                                    t("creator.gift_cards.postcard_sent", "Postcard sent successfully!")
                                                                } else {
                                                                    t("creator.gift_cards.email_sent", "Email successfully sent")
                                                                }
                                                            )
                                                            templateSent = true
                                                        } else {
                                                            snackbarHostState.showSnackbar(
                                                                res.optString("error", res.optString("message", "Error"))
                                                            )
                                                        }
                                                    } catch (e: Exception) {
                                                        snackbarHostState.showSnackbar(e.message ?: "Error")
                                                    } finally {
                                                        sending = false
                                                    }
                                                }
                                            },
                                            enabled = !sending && templateExists && !templateSent,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                if (deliveryMode == DeliveryMode.POSTCARD) {
                                                    t("creator.gift_cards.send_postcard", "Send postcard")
                                                } else {
                                                    t("creator.gift_cards.send_email", "Send email")
                                                }
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
    }

    if (showProductPicker) {
        val filtered = remember(pickerSearch, catalogProducts) {
            val q = pickerSearch.trim().lowercase(Locale.getDefault())
            catalogProducts.filter {
                q.isEmpty() || it.title.lowercase(Locale.getDefault()).contains(q)
            }
        }
        GiftCardProductPickerOverlay(
            onDismiss = { showProductPicker = false },
            title = t("creator.gift_cards.select_products", "Select products"),
            searchValue = pickerSearch,
            onSearchChange = { pickerSearch = it },
            searchLabel = t("creator.common.search", "Search"),
            grid = {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp, max = 440.dp)
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    items(filtered, key = { it.id }) { p ->
                        val sel = p.id in pickerSelection
                        val disabled = !sel && pickerSelection.size >= 20
                        ProductPickerGridItem(
                            p = p,
                            selected = sel,
                            disabled = disabled,
                            onOpenDetail = {
                                if (!disabled) {
                                    productDetailTarget = ProductDetailTarget(
                                        productId = p.id,
                                        handle = p.handle,
                                        fromPicker = true
                                    )
                                }
                            },
                            fmtMoney = { a, c -> fmtMoney(a, c) }
                        )
                    }
                }
            },
            footer = {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { showProductPicker = false }) {
                        Text(t("creator.common.cancel", "Cancel"))
                    }
                    Spacer(Modifier.size(8.dp))
                    TextButton(
                        onClick = {
                            scope.launch {
                                saving = true
                                try {
                                    val res = api.saveGiftCardSelection(giftCardId, pickerSelection.toList())
                                    if (res.optBoolean("ok", false)) {
                                        productIds = pickerSelection
                                        showProductPicker = false
                                        snackbarHostState.showSnackbar(
                                            t("creator.gift_cards.selection_saved", "Selection saved")
                                        )
                                    } else {
                                        snackbarHostState.showSnackbar(
                                            res.optString("error", res.optString("message", "Error"))
                                        )
                                    }
                                } catch (e: Exception) {
                                    snackbarHostState.showSnackbar(e.message ?: "Error")
                                } finally {
                                    saving = false
                                }
                            }
                        },
                        enabled = !saving
                    ) {
                        Text(t("creator.common.save", "Save"))
                    }
                }
            }
        )
    }

    val detailTarget = productDetailTarget
    if (detailTarget != null) {
        val row = catalogProducts.find { it.id == detailTarget.productId }
        val currency = row?.currency ?: "EUR"
        GiftCardProductVariantOverlay(
            productTitle = row?.title ?: detailTarget.handle,
            loading = detailLoading,
            loadError = detailLoadError,
            detail = detailData,
            selectedVariantId = selectedVariantIdInModal,
            onSelectVariant = { selectedVariantIdInModal = it },
            onConfirm = {
                val vid = selectedVariantIdInModal ?: return@GiftCardProductVariantOverlay
                variantByProductId = variantByProductId + (detailTarget.productId to vid.toString())
                if (detailTarget.fromPicker) {
                    if (pickerSelection.size < 20) {
                        pickerSelection = pickerSelection + detailTarget.productId
                    }
                }
                productDetailTarget = null
            },
            onDismiss = {
                productDetailTarget = null
                detailLoadError = null
            },
            confirmLabel = t("creator.common.save", "Save"),
            dismissLabel = t("creator.common.close", "Close"),
            cancelLabel = t("creator.common.cancel", "Cancel"),
            variantSectionLabel = t("creator.gift_cards.variant_options", "Options"),
            noVariantsText = t("creator.gift_cards.no_variants", "No variants available."),
            fmtMoney = { a, c -> fmtMoney(a, c) },
            currencyCode = currency
        )
    }
}

@Composable
private fun ProductPickerGridItem(
    p: ShopifyProductRow,
    selected: Boolean,
    disabled: Boolean,
    onOpenDetail: () -> Unit,
    fmtMoney: (Double, String) -> String
) {
    val vid = p.variantId ?: p.variants.firstOrNull()?.id
    val price = p.displayPrice(vid)
    Column(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    disabled -> Color(0xFFF3F4F6)
                    selected -> Color(0xFFFFF7ED)
                    else -> Color(0xFFF9FAFB)
                }
            )
            .clickable(enabled = !disabled, onClick = onOpenDetail)
            .padding(8.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(100.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFE5E7EB))
        ) {
            val img = p.displayImage(vid)
            if (img != null) {
                AsyncImage(
                    model = img,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        }
        Column(Modifier.padding(top = 6.dp)) {
            Text(p.title, fontSize = 12.sp, maxLines = 2, fontWeight = FontWeight.Medium)
            Text(fmtMoney(price, p.currency), fontSize = 11.sp, color = EazColors.TextSecondary)
        }
    }
}

@Composable
private fun ProductTile(
    p: ShopifyProductRow,
    selectedVariantId: String?,
    onOpen: () -> Unit,
    fmtMoney: (Double, String) -> String
) {
    val price = p.displayPrice(selectedVariantId)
    Column(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFF9FAFB))
            .clickable(onClick = onOpen)
            .padding(8.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFFE5E7EB)),
            contentAlignment = Alignment.Center
        ) {
            val img = p.displayImage(selectedVariantId)
            if (img != null) {
                AsyncImage(
                    model = img,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        }
        Text(p.title, fontSize = 12.sp, maxLines = 2, fontWeight = FontWeight.Medium)
        Text(fmtMoney(price, p.currency), fontSize = 11.sp, color = EazColors.TextSecondary)
    }
}

@Composable
private fun PostcardFields(
    first: String,
    last: String,
    line1: String,
    line2: String,
    zip: String,
    city: String,
    country: String,
    state: String,
    size: String,
    onChange: (String, String, String, String, String, String, String, String, String) -> Unit,
    t: (String, String) -> String
) {
    Column {
        OutlinedTextField(
            value = first,
            onValueChange = { onChange(it, last, line1, line2, zip, city, country, state, size) },
            label = { Text(t("creator.gift_cards.postcard_first_name", "First name")) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = last,
            onValueChange = { onChange(first, it, line1, line2, zip, city, country, state, size) },
            label = { Text(t("creator.gift_cards.postcard_last_name", "Last name")) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = line1,
            onValueChange = { onChange(first, last, it, line2, zip, city, country, state, size) },
            label = { Text(t("creator.gift_cards.postcard_address_line_1", "Address line 1")) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = line2,
            onValueChange = { onChange(first, last, line1, it, zip, city, country, state, size) },
            label = { Text(t("creator.gift_cards.postcard_address_line_2", "Address line 2 (optional)")) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = zip,
                onValueChange = { onChange(first, last, line1, line2, it, city, country, state, size) },
                label = { Text(t("creator.gift_cards.postcard_postal_code", "Postal code")) },
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = city,
                onValueChange = { onChange(first, last, line1, line2, zip, it, country, state, size) },
                label = { Text(t("creator.gift_cards.postcard_city", "City")) },
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = country,
            onValueChange = { onChange(first, last, line1, line2, zip, city, it, state, size) },
            label = { Text(t("creator.gift_cards.postcard_country", "Country")) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = state,
            onValueChange = { onChange(first, last, line1, line2, zip, city, country, it, size) },
            label = { Text(t("creator.gift_cards.postcard_state", "State / Province (optional)")) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = size,
            onValueChange = { onChange(first, last, line1, line2, zip, city, country, state, it) },
            label = { Text(t("creator.gift_cards.postcard_size", "Postcard size")) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
    }
}
