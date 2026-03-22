package com.eazpire.creator.ui.creator

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.i18n.TranslationStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.round

private data class HotspotPoint(
    val id: String,
    val x: Float,
    val y: Float,
    val anchor: String = "center"
)

private data class HotspotItemState(
    val productId: String,
    val hotspots: List<HotspotPoint>
)

private data class HotspotsState(
    val canvasW: Int,
    val canvasH: Int,
    val productIds: List<String>,
    val items: List<HotspotItemState>
)

private data class HeroProductRow(
    val id: String,
    val name: String,
    val imageUrl: String?,
    val number: Int
)

private fun anchorOffsetPx(anchor: String, halfDp: androidx.compose.ui.unit.Dp, density: androidx.compose.ui.unit.Density): Offset {
    val half = with(density) { halfDp.toPx() }
    return when (anchor) {
        "center" -> Offset(half, half)
        "top-left" -> Offset(0f, 0f)
        "top-right" -> Offset(half * 2, 0f)
        "bottom-left" -> Offset(0f, half * 2)
        "bottom-right" -> Offset(half * 2, half * 2)
        else -> Offset(half, half)
    }
}

private fun parseHotspotsFromHero(hero: JSONObject): HotspotsState? {
    val raw = hero.opt("hotspots_json") ?: return null
    val obj = when (raw) {
        is JSONObject -> raw
        is String -> try {
            JSONObject(raw)
        } catch (_: Exception) {
            null
        }
        else -> null
    } ?: return null

    val cw = obj.optJSONObject("canvas")?.optInt("w")
        ?: hero.optInt("width", 1024).takeIf { it > 0 } ?: 1024
    val ch = obj.optJSONObject("canvas")?.optInt("h")
        ?: hero.optInt("height", 2048).takeIf { it > 0 } ?: 2048

    val productIds = mutableListOf<String>()
    val arr = obj.optJSONArray("product_ids")
    if (arr != null) {
        for (i in 0 until arr.length()) {
            val s = arr.optString(i, "").trim()
            if (s.isNotBlank()) productIds.add(s)
        }
    }

    var items = mutableListOf<HotspotItemState>()
    val itemsArr = obj.optJSONArray("items")
    if (itemsArr != null) {
        for (i in 0 until itemsArr.length()) {
            val it = itemsArr.optJSONObject(i) ?: continue
            val pid = it.optString("product_id", "").trim()
            if (pid.isBlank()) continue
            val hs = mutableListOf<HotspotPoint>()
            val hArr = it.optJSONArray("hotspots")
            if (hArr != null) {
                for (j in 0 until hArr.length()) {
                    val h = hArr.optJSONObject(j) ?: continue
                    val id = h.optString("id", "").trim().ifBlank { "hotspot-$pid-$j" }
                    var x = h.optDouble("x", 0.0).toFloat()
                    var y = h.optDouble("y", 0.0).toFloat()
                    val anchor = h.optString("anchor", "center").ifBlank { "center" }
                    hs.add(HotspotPoint(id = id, x = x, y = y, anchor = anchor))
                }
            }
            items.add(HotspotItemState(productId = pid, hotspots = hs))
        }
    }

    if (productIds.isNotEmpty() && items.isEmpty()) {
        items = productIds.map { pid ->
            HotspotItemState(productId = pid, hotspots = emptyList())
        }.toMutableList()
    }

    if (productIds.isEmpty() && items.isNotEmpty()) {
        items.forEach { item ->
            if (!productIds.contains(item.productId)) productIds.add(item.productId)
        }
    }

    val finalProductIds = productIds.ifEmpty {
        items.map { it.productId }.filter { it.isNotBlank() }.distinct()
    }

    return HotspotsState(
        canvasW = cw,
        canvasH = ch,
        productIds = finalProductIds,
        items = ensureDefaultHotspots(HotspotsState(cw, ch, finalProductIds, items))
    )
}

private fun ensureDefaultHotspots(state: HotspotsState): List<HotspotItemState> {
    val byProduct = state.items.associateBy { it.productId }
    return state.productIds.map { pid ->
        val item = byProduct[pid]
        when {
            item != null && item.hotspots.isNotEmpty() -> item
            else -> HotspotItemState(
                productId = pid,
                hotspots = listOf(
                    HotspotPoint(
                        id = "hotspot-${pid}-1",
                        x = 0.5f,
                        y = 0.5f,
                        anchor = "center"
                    )
                )
            )
        }
    }
}

private fun buildInitialHotspots(hero: JSONObject): HotspotsState {
    val parsed = parseHotspotsFromHero(hero)
    if (parsed != null) {
        val items = ensureDefaultHotspots(parsed)
        return parsed.copy(items = items)
    }
    val w = hero.optInt("width", 1024).takeIf { it > 0 } ?: 1024
    val h = hero.optInt("height", 2048).takeIf { it > 0 } ?: 2048
    return HotspotsState(
        canvasW = w,
        canvasH = h,
        productIds = emptyList(),
        items = emptyList()
    )
}

private fun HotspotsState.toJsonObject(): JSONObject {
    val itemsJson = JSONArray()
    for (item in items) {
        val hs = JSONArray()
        for (h in item.hotspots) {
            hs.put(
                JSONObject()
                    .put("id", h.id)
                    .put("x", round(h.x.toDouble() * 1_000_000.0) / 1_000_000.0)
                    .put("y", round(h.y.toDouble() * 1_000_000.0) / 1_000_000.0)
                    .put("anchor", h.anchor.ifBlank { "center" })
            )
        }
        itemsJson.put(
            JSONObject()
                .put("product_id", item.productId)
                .put("hotspots", hs)
        )
    }
    val pids = JSONArray()
    productIds.forEach { pids.put(it) }
    return JSONObject()
        .put("v", 1)
        .put("canvas", JSONObject().put("w", canvasW).put("h", canvasH))
        .put("product_ids", pids)
        .put("items", itemsJson)
}

private fun normalizeDisplayed(
    x: Float,
    y: Float,
    canvasW: Float,
    canvasH: Float
): Pair<Float, Float> {
    var nx = x
    var ny = y
    if ((nx > 1f || ny > 1f) && canvasW > 0f && canvasH > 0f) {
        nx /= canvasW
        ny /= canvasH
    }
    return nx.coerceIn(0f, 1f) to ny.coerceIn(0f, 1f)
}

@Composable
fun HeroImagePreviewModal(
    visible: Boolean,
    heroId: String?,
    ownerId: String,
    jwt: String,
    translationStore: TranslationStore,
    api: CreatorApi,
    onDismiss: () -> Unit,
    onSaved: () -> Unit = {}
) {
    fun t(key: String, en: String) = translationStore.t(key, en)

    if (!visible || heroId.isNullOrBlank()) return

    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    var loading by remember(heroId) { mutableStateOf(true) }
    var actionBusy by remember(heroId) { mutableStateOf(false) }
    var loadError by remember(heroId) { mutableStateOf<String?>(null) }
    var heroJson by remember(heroId) { mutableStateOf<JSONObject?>(null) }
    var hotspotsState by remember(heroId) { mutableStateOf<HotspotsState?>(null) }
    var originalHotspots by remember(heroId) { mutableStateOf<HotspotsState?>(null) }
    var hasChanges by remember(heroId) { mutableStateOf(false) }
    var productRows by remember(heroId) { mutableStateOf<List<HeroProductRow>>(emptyList()) }
    var imageBoxPx by remember(heroId) { mutableStateOf(IntSize.Zero) }

    var showCloseConfirm by remember { mutableStateOf(false) }
    var showPublishConfirm by remember { mutableStateOf(false) }
    var pendingPublish by remember { mutableStateOf<Boolean?>(null) }

    val latestHotspots by rememberUpdatedState(hotspotsState)
    val latestImagePx by rememberUpdatedState(imageBoxPx)

    LaunchedEffect(heroId) {
        loading = true
        loadError = null
        heroJson = null
        hotspotsState = null
        originalHotspots = null
        hasChanges = false
        productRows = emptyList()
        try {
            val res = withContext(Dispatchers.IO) { api.heroGet(ownerId, heroId) }
            if (!res.optBoolean("ok", false)) {
                loadError = res.optString("error", "load_failed")
                loading = false
                return@LaunchedEffect
            }
            val hero = res.optJSONObject("hero_image") ?: run {
                loadError = "missing_hero"
                loading = false
                return@LaunchedEffect
            }
            heroJson = hero
            val initial = buildInitialHotspots(hero)
            hotspotsState = initial
            originalHotspots = HotspotsState(
                canvasW = initial.canvasW,
                canvasH = initial.canvasH,
                productIds = initial.productIds.toList(),
                items = initial.items.map { item ->
                    HotspotItemState(
                        productId = item.productId,
                        hotspots = item.hotspots.map { h -> h.copy() }
                    )
                }
            )
            loading = false
        } catch (e: Exception) {
            loadError = e.message ?: "error"
            loading = false
        }
    }

    LaunchedEffect(heroId, hotspotsState?.productIds) {
        val hs = hotspotsState ?: return@LaunchedEffect
        val ids = hs.productIds
        if (ids.isEmpty()) {
            productRows = emptyList()
            return@LaunchedEffect
        }
        val joined = ids.joinToString(",")
        val resp = withContext(Dispatchers.IO) {
            val first = ids.firstOrNull().orEmpty()
            if (first.startsWith("gid://shopify/Product/")) {
                api.getProductsByShopifyIds(ownerId, joined)
            } else {
                api.getProductsByKeys(ownerId, joined)
            }
        }
        if (!resp.optBoolean("ok", false)) {
            productRows = ids.mapIndexed { idx, id ->
                HeroProductRow(id = id, name = id, imageUrl = null, number = idx + 1)
            }
            return@LaunchedEffect
        }
        val arr = resp.optJSONArray("products") ?: run {
            productRows = ids.mapIndexed { idx, id ->
                HeroProductRow(id = id, name = id, imageUrl = null, number = idx + 1)
            }
            return@LaunchedEffect
        }
        val byId = mutableMapOf<String, JSONObject>()
        for (i in 0 until arr.length()) {
            val p = arr.optJSONObject(i) ?: continue
            val key = if (ids.firstOrNull().orEmpty().startsWith("gid://shopify/Product/")) {
                p.optString("shopify_product_id", "").trim()
            } else {
                p.optString("product_key", "").trim()
            }
            if (key.isBlank()) continue
            byId[key] = p
        }
        productRows = ids.mapIndexed { idx, id ->
            val p = byId[id]
            val name = p?.optString("product_name", "")?.trim()?.takeIf { it.isNotBlank() }
                ?: p?.optString("product_key", "")?.trim()?.takeIf { it.isNotBlank() }
                ?: id
            val img = p?.optString("image_url", "")?.trim()?.takeIf { it.isNotBlank() }
            HeroProductRow(id = id, name = name, imageUrl = img, number = idx + 1)
        }
    }

    fun updateHotspot(productId: String, hotspotId: String, nx: Float, ny: Float) {
        val s = hotspotsState ?: return
        val next = s.copy(
            items = s.items.map { item ->
                if (item.productId != productId) item
                else item.copy(
                    hotspots = item.hotspots.map { h ->
                        if (h.id != hotspotId) h else h.copy(x = nx, y = ny)
                    }
                )
            }
        )
        hotspotsState = next
        val orig = originalHotspots
        hasChanges = orig == null || next.toJsonObject().toString() != orig.toJsonObject().toString()
    }

    fun tryDismiss() {
        if (hasChanges) showCloseConfirm = true
        else onDismiss()
    }

    Dialog(
        onDismissRequest = { tryDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF0D0D12)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { tryDismiss() }) {
                        Text(t("creator.hero_preview.close", "Close"), color = Color.White.copy(alpha = 0.85f))
                    }
                    Text(
                        text = t("creator.hero_preview.title", "Hero image"),
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp
                    )
                    Spacer(modifier = Modifier.size(48.dp))
                }

                val hero = heroJson
                val published = hero?.let { h ->
                    !h.isNull("published_at") && h.opt("published_at") != null
                } ?: false

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    val label = if (published) {
                        t("creator.hero_preview.status_published", "Published")
                    } else {
                        t("creator.hero_preview.status_unpublished", "Not published")
                    }
                    Text(
                        text = label,
                        color = if (published) Color(0xFF4ADE80) else Color(0xFF94A3B8),
                        fontSize = 13.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (published) Color(0x224ADE80) else Color(0x2294A3B8))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                when {
                    loading -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = EazColors.Orange)
                        }
                    }
                    loadError != null -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = loadError ?: "",
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                    hero == null || hotspotsState == null -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = t("creator.hero_preview.empty", "Nothing to show."),
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                    else -> {
                        val hs = hotspotsState!!
                        val imageUrl = firstNonBlank(
                            hero.optString("image_url", ""),
                            hero.optString("thumbnail_url", "")
                        )?.let { normalizeHeroModalUrl(it) }
                        val aw = hero.optInt("width", hs.canvasW).takeIf { it > 0 } ?: hs.canvasW
                        val ah = hero.optInt("height", hs.canvasH).takeIf { it > 0 } ?: hs.canvasH
                        val ratio = aw.toFloat() / ah.toFloat().coerceAtLeast(0.0001f)

                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(ratio)
                                        .clip(RoundedCornerShape(10.dp))
                                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                                        .onSizeChanged { imageBoxPx = it }
                                ) {
                                    if (!imageUrl.isNullOrBlank()) {
                                        val model = ImageRequest.Builder(context)
                                            .data(imageUrl)
                                            .apply {
                                                if (jwt.isNotBlank()) addHeader("Authorization", "Bearer $jwt")
                                            }
                                            .build()
                                        AsyncImage(
                                            model = model,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                    val wPx = imageBoxPx.width.toFloat().coerceAtLeast(1f)
                                    val hPx = imageBoxPx.height.toFloat().coerceAtLeast(1f)
                                    val halfBadge = 16.dp
                                    for (item in hs.items) {
                                        val num = hs.productIds.indexOf(item.productId).let { if (it >= 0) it + 1 else 1 }
                                        for (h in item.hotspots) {
                                            val (nx, ny) = normalizeDisplayed(
                                                h.x, h.y,
                                                hs.canvasW.toFloat(),
                                                hs.canvasH.toFloat()
                                            )
                                            val xIn = nx * wPx
                                            val yIn = ny * hPx
                                            val off = anchorOffsetPx(h.anchor, halfBadge, density)
                                            Box(
                                                modifier = Modifier
                                                    .offset {
                                                        IntOffset(
                                                            (xIn - off.x).toInt(),
                                                            (yIn - off.y).toInt()
                                                        )
                                                    }
                                                    .size(32.dp)
                                                    .pointerInput(item.productId, h.id) {
                                                        detectDragGestures { change, dragAmount ->
                                                            change.consume()
                                                            val s = latestHotspots ?: return@detectDragGestures
                                                            val img = latestImagePx
                                                            val iw = img.width.toFloat().coerceAtLeast(1f)
                                                            val ih = img.height.toFloat().coerceAtLeast(1f)
                                                            val curItem = s.items.find { it.productId == item.productId } ?: return@detectDragGestures
                                                            val curH = curItem.hotspots.find { it.id == h.id } ?: return@detectDragGestures
                                                            val (cnx, cny) = normalizeDisplayed(
                                                                curH.x, curH.y,
                                                                s.canvasW.toFloat(),
                                                                s.canvasH.toFloat()
                                                            )
                                                            val newX = (cnx * iw + dragAmount.x) / iw
                                                            val newY = (cny * ih + dragAmount.y) / ih
                                                            updateHotspot(
                                                                item.productId,
                                                                h.id,
                                                                newX.coerceIn(0f, 1f),
                                                                newY.coerceIn(0f, 1f)
                                                            )
                                                        }
                                                    }
                                                    .clip(CircleShape)
                                                    .background(EazColors.Orange),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = num.toString(),
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            if (productRows.isEmpty() && hs.productIds.isEmpty()) {
                                item {
                                    Text(
                                        text = t("creator.hero_preview.no_products", "No products linked."),
                                        color = Color.White.copy(alpha = 0.5f),
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }
                            } else {
                                items(productRows, key = { it.id }) { row ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color.White.copy(alpha = 0.06f))
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color.White.copy(alpha = 0.08f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (!row.imageUrl.isNullOrBlank()) {
                                                AsyncImage(
                                                    model = ImageRequest.Builder(context).data(row.imageUrl).build(),
                                                    contentDescription = null,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Fit
                                                )
                                            } else {
                                                Text(
                                                    text = row.number.toString(),
                                                    color = Color.White.copy(alpha = 0.6f),
                                                    fontSize = 14.sp
                                                )
                                            }
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = row.name,
                                                color = Color.White,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TextButton(
                                onClick = {
                                    if (actionBusy) return@TextButton
                                    scope.launch {
                                        actionBusy = true
                                        try {
                                            val body = hs.toJsonObject()
                                            val res = withContext(Dispatchers.IO) {
                                                api.heroUpdateHotspots(ownerId, heroId, body)
                                            }
                                            if (res.optBoolean("ok", false)) {
                                                originalHotspots = HotspotsState(
                                                    canvasW = hs.canvasW,
                                                    canvasH = hs.canvasH,
                                                    productIds = hs.productIds.toList(),
                                                    items = hs.items.map { item ->
                                                        HotspotItemState(
                                                            productId = item.productId,
                                                            hotspots = item.hotspots.map { it.copy() }
                                                        )
                                                    }
                                                )
                                                hasChanges = false
                                                onSaved()
                                            }
                                        } finally {
                                            actionBusy = false
                                        }
                                    }
                                },
                                enabled = hasChanges && !actionBusy,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    if (actionBusy) t("creator.hero_preview.saving", "Saving…")
                                    else t("creator.hero_preview.save", "Save hotspots"),
                                    color = if (hasChanges) EazColors.Orange else Color.White.copy(alpha = 0.4f)
                                )
                            }
                            TextButton(
                                onClick = {
                                    pendingPublish = !published
                                    showPublishConfirm = true
                                },
                                enabled = !actionBusy,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    if (published) {
                                        t("creator.hero_preview.unpublish", "Unpublish")
                                    } else {
                                        t("creator.hero_preview.publish", "Publish")
                                    },
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCloseConfirm) {
        AlertDialog(
            onDismissRequest = { showCloseConfirm = false },
            title = { Text(t("creator.hero_preview.unsaved_title", "Unsaved changes")) },
            text = { Text(t("creator.hero_preview.unsaved_message", "You have unsaved hotspot changes. Close anyway?")) },
            confirmButton = {
                TextButton(onClick = {
                    showCloseConfirm = false
                    hasChanges = false
                    onDismiss()
                }) { Text(t("creator.hero_preview.discard", "Discard")) }
            },
            dismissButton = {
                TextButton(onClick = { showCloseConfirm = false }) {
                    Text(t("creator.common.cancel", "Cancel"))
                }
            }
        )
    }

    if (showPublishConfirm && pendingPublish != null) {
        val goPublish = pendingPublish == true
        AlertDialog(
            onDismissRequest = { showPublishConfirm = false },
            title = {
                Text(
                    if (goPublish) t("creator.hero_preview.confirm_publish_title", "Publish hero image?")
                    else t("creator.hero_preview.confirm_unpublish_title", "Unpublish hero image?")
                )
            },
            text = {
                Text(
                    if (goPublish) {
                        t("creator.hero_preview.confirm_publish_message", "The image will be visible on the website.")
                    } else {
                        t("creator.hero_preview.confirm_unpublish_message", "The image will be removed from the website.")
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPublishConfirm = false
                        scope.launch {
                            actionBusy = true
                            try {
                                val res = withContext(Dispatchers.IO) {
                                    if (goPublish) api.heroPublish(ownerId, heroId)
                                    else api.heroUnpublish(ownerId, heroId)
                                }
                                if (res.optBoolean("ok", false)) {
                                    val at = res.opt("published_at")
                                    heroJson = heroJson?.apply {
                                        if (goPublish && at != null) put("published_at", at)
                                        else remove("published_at")
                                    }
                                    onSaved()
                                }
                            } finally {
                                actionBusy = false
                            }
                        }
                    }
                ) {
                    Text(if (goPublish) t("creator.hero_preview.publish", "Publish") else t("creator.hero_preview.unpublish", "Unpublish"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPublishConfirm = false }) {
                    Text(t("creator.common.cancel", "Cancel"))
                }
            }
        )
    }
}

private fun firstNonBlank(vararg values: String?): String? =
    values.firstOrNull { !it.isNullOrBlank() }?.trim()

private fun normalizeHeroModalUrl(raw: String): String {
    val value = raw.trim()
    if (value.isBlank()) return value
    return when {
        value.startsWith("https://", ignoreCase = true) -> value
        value.startsWith("http://", ignoreCase = true) -> "https://${value.removePrefix("http://")}"
        value.startsWith("//") -> "https:$value"
        value.startsWith("/") -> "https://www.eazpire.com$value"
        else -> value
    }
}
