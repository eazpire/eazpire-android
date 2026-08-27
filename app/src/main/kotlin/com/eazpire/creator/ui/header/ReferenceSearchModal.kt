package com.eazpire.creator.ui.header

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.i18n.LocalTranslationStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private data class RefSearchListItem(
    val id: String,
    val status: String,
    val title: String,
    val thumbUrl: String,
    val openedAt: Long?,
)

private data class RefMatchItem(
    val designId: String,
    val title: String,
    val previewUrl: String,
    val score: Double,
    val handle: String,
)

private data class RefCreationItem(
    val slot: Int,
    val designId: String,
    val previewUrl: String,
    val jobId: String,
    val status: String,
    val saveStatus: String,
    val saveError: String,
)

data class RefSearchCreateProductRequest(
    val designUrl: String,
    val designId: String? = null,
)

/**
 * Native Reference Search modal (web `#eaz-reference-search-modal`).
 * History + upload + product/design/creation tabs with save / create-product handoff.
 */
@Composable
fun ReferenceSearchModal(
    visible: Boolean,
    ownerId: String,
    creatorApi: CreatorApi,
    onDismiss: () -> Unit,
    onNavigateToUrl: (String) -> Unit = {},
    onCreateProduct: (RefSearchCreateProductRequest) -> Unit = {},
) {
    if (!visible) return

    val store = LocalTranslationStore.current
    fun t(key: String, fallback: String) = store?.t(key, fallback) ?: fallback

    val title = t("eaz.reference_search.title", "Reference Search")
    val newSearchLabel = t("eaz.reference_search.new_search", "New Search")
    val welcome = t(
        "eaz.reference_search.welcome",
        "Upload an image to find similar products and designs — and generate new creations inspired by it.",
    )
    val analyzing = t("eaz.reference_search.analyzing", "Analyzing your image…")
    val tabProducts = t("eaz.reference_search.tab_products", "Products")
    val tabDesigns = t("eaz.reference_search.tab_designs", "Designs")
    val tabCreations = t("eaz.reference_search.tab_creations", "New Creations")
    val subtabAvailable = t("eaz.reference_search.subtab_available", "Available")
    val subtabSaved = t("eaz.reference_search.subtab_saved", "Saved")
    val emptyProducts = t("eaz.reference_search.empty_products", "No matching products yet.")
    val emptyDesigns = t("eaz.reference_search.empty_designs", "No matching designs yet.")
    val emptyAvailable = t("eaz.reference_search.empty_available", "No available creations yet.")
    val emptySaved = t("eaz.reference_search.empty_saved", "Saved creations will appear here.")
    val emptyCreations = t("eaz.reference_search.empty_creations", "New creations will appear here.")
    val loginRequired = t("eaz.reference_search.login_required", "Please log in to use Reference Search.")
    val dailyLimit = t("eaz.reference_search.daily_limit", "Daily search limit reached. Try again tomorrow.")
    val deleteLabel = t("eaz.reference_search.delete", "Delete")
    val saveLabel = t("eaz.reference_search.save", "Save")
    val savingLabel = t("eaz.reference_search.saving", "Saving…")
    val saveFailedLabel = t("eaz.reference_search.save_failed", "Save failed. Try again.")
    val removeLabel = t("eaz.reference_search.remove", "Remove")
    val createProductLabel = t("eaz.reference_search.create_product", "Create Product")
    val previewTitle = t("eaz.reference_search.preview_title", "Creation preview")

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var loadingList by remember { mutableStateOf(true) }
    var listError by remember { mutableStateOf<String?>(null) }
    var searches by remember { mutableStateOf<List<RefSearchListItem>>(emptyList()) }
    var dailyHint by remember { mutableStateOf("") }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var detailJson by remember { mutableStateOf<JSONObject?>(null) }
    var detailLoading by remember { mutableStateOf(false) }
    var uploading by remember { mutableStateOf(false) }
    var tab by remember { mutableIntStateOf(0) }
    var creationsSubtab by remember { mutableIntStateOf(0) } // 0 available, 1 saved
    var banner by remember { mutableStateOf<String?>(null) }
    var previewCreation by remember { mutableStateOf<RefCreationItem?>(null) }
    var previewBusy by remember { mutableStateOf(false) }
    var menuSpec by remember { mutableStateOf<Triple<String, String?, Boolean>?>(null) }

    fun refreshList() {
        if (ownerId.isBlank()) {
            loadingList = false
            listError = loginRequired
            return
        }
        scope.launch {
            loadingList = true
            listError = null
            try {
                val listRes = creatorApi.referenceSearchList(ownerId)
                val dailyRes = creatorApi.referenceSearchDaily(ownerId)
                val arr = listRes.optJSONArray("searches")
                val items = mutableListOf<RefSearchListItem>()
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        items.add(
                            RefSearchListItem(
                                id = o.optString("id"),
                                status = o.optString("status"),
                                title = o.optString("title").ifBlank { "Search" },
                                thumbUrl = o.optString("thumb_url").ifBlank { o.optString("upload_url") },
                                openedAt = o.optLong("opened_at").takeIf { it > 0L },
                            ),
                        )
                    }
                }
                searches = items
                val daily = dailyRes.optJSONObject("daily")
                if (daily != null) {
                    val remaining = daily.optInt("remaining", daily.optInt("left", -1))
                    val max = daily.optInt("limit", daily.optInt("max", 10))
                    if (remaining >= 0) {
                        dailyHint = t("eaz.reference_search.daily_hint", "{{ remaining }} of {{ max }} searches left today")
                            .replace("{{ remaining }}", remaining.toString())
                            .replace("{{ max }}", max.toString())
                    }
                }
            } catch (e: Exception) {
                listError = e.message ?: "Error"
            } finally {
                loadingList = false
            }
        }
    }

    fun loadDetail(id: String) {
        selectedId = id
        detailLoading = true
        detailJson = null
        tab = 0
        scope.launch {
            try {
                creatorApi.referenceSearchOpen(ownerId, id)
                val res = creatorApi.referenceSearchGet(ownerId, id)
                detailJson = res
            } catch (e: Exception) {
                banner = e.message
            } finally {
                detailLoading = false
            }
        }
    }

    LaunchedEffect(visible, ownerId) {
        if (visible) {
            selectedId = null
            detailJson = null
            banner = null
            refreshList()
        }
    }

    // Poll while selected search is in progress OR any creation is saving
    LaunchedEffect(selectedId, detailJson?.optJSONObject("search")?.optString("status"), detailJson?.optJSONArray("creations")?.toString()) {
        val id = selectedId ?: return@LaunchedEffect
        val status = detailJson?.optJSONObject("search")?.optString("status").orEmpty()
        val creations = parseCreations(detailJson?.optJSONArray("creations"))
        val anySaving = creations.any { it.saveStatus == "saving" }
        if (status in setOf("queued", "analyzing", "matching", "generating") || anySaving) {
            delay(2500)
            try {
                val res = creatorApi.referenceSearchGet(ownerId, id)
                detailJson = res
                if (res.optJSONObject("search")?.optString("status") == "done") {
                    refreshList()
                }
            } catch (_: Exception) {
            }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null || ownerId.isBlank()) return@rememberLauncherForActivityResult
        scope.launch {
            uploading = true
            banner = null
            try {
                val bytes = withContext(Dispatchers.IO) { readUriBytes(context, uri) }
                if (bytes == null || bytes.isEmpty()) {
                    banner = t("creator.shop_create_product.read_image_failed", "Could not read the image.")
                    return@launch
                }
                val name = uri.lastPathSegment?.substringAfterLast('/') ?: "reference.jpg"
                val res = creatorApi.referenceSearchStartMultipart(ownerId, bytes, name)
                if (!res.optBoolean("ok", false)) {
                    val err = res.optString("error")
                    banner = if (err == "daily_limit") dailyLimit else res.optString("message").ifBlank { err.ifBlank { "Error" } }
                    return@launch
                }
                val newId = res.optString("search_id").ifBlank { res.optString("id") }
                refreshList()
                if (newId.isNotBlank()) loadDetail(newId)
            } catch (e: Exception) {
                banner = e.message
            } finally {
                uploading = false
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (selectedId != null) {
                    IconButton(onClick = {
                        selectedId = null
                        detailJson = null
                        refreshList()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = t("creator.common.back", "Back"))
                    }
                }
                Text(
                    text = title,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = EazColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = t("creator.common.close", "Close"))
                }
            }
            HorizontalDivider(color = EazColors.TopbarBorder)

            if (banner != null) {
                Text(
                    text = banner!!,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFFF7ED))
                        .padding(12.dp),
                    color = EazColors.TextSecondary,
                    fontSize = 13.sp,
                )
            }

            when {
                selectedId == null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                    ) {
                        Text(welcome, color = EazColors.TextSecondary, fontSize = 14.sp)
                        if (dailyHint.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(dailyHint, color = EazColors.TextSecondary, fontSize = 12.sp)
                        }
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { picker.launch("image/*") },
                            enabled = !uploading && ownerId.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = EazColors.Orange),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (uploading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(newSearchLabel)
                        }
                        Spacer(Modifier.height(16.dp))
                        when {
                            loadingList -> {
                                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = EazColors.Orange)
                                }
                            }
                            listError != null -> {
                                Text(listError!!, color = EazColors.TextSecondary)
                            }
                            searches.isEmpty() -> {
                                Text(
                                    t("eaz.reference_search.empty_creations", "Your search history will appear here."),
                                    color = EazColors.TextSecondary,
                                    fontSize = 13.sp,
                                )
                            }
                            else -> {
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    items(searches, key = { it.id }) { item ->
                                        RefSearchHistoryRow(
                                            item = item,
                                            onOpen = { loadDetail(item.id) },
                                            onDelete = {
                                                scope.launch {
                                                    creatorApi.referenceSearchDelete(ownerId, item.id)
                                                    if (selectedId == item.id) {
                                                        selectedId = null
                                                        detailJson = null
                                                    }
                                                    refreshList()
                                                }
                                            },
                                            deleteLabel = deleteLabel,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                detailLoading && detailJson == null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = EazColors.Orange)
                    }
                }
                else -> {
                    val searchObj = detailJson?.optJSONObject("search")
                    val status = searchObj?.optString("status").orEmpty()
                    val searchTitle = searchObj?.optString("title")
                        ?.ifBlank { detailJson?.optString("title") }
                        ?.ifBlank { "Search" }
                        ?: "Search"
                    val products = parseMatches(detailJson?.optJSONArray("products"))
                    val designs = parseMatches(detailJson?.optJSONArray("designs"))
                    val allCreations = parseCreations(detailJson?.optJSONArray("creations"))
                    val available = allCreations.filter {
                        it.saveStatus == "available" || it.saveStatus == "save_failed"
                    }
                    val saved = allCreations.filter {
                        it.saveStatus == "saving" || it.saveStatus == "saved"
                    }
                    val creationCount = available.size + saved.size
                    val matchCta = when {
                        saved.isNotEmpty() || available.any { it.previewUrl.isNotBlank() } ->
                            t("eaz.reference_search.match_cta_ready", "Nothing a match? We created designs for you.")
                        status in setOf("queued", "analyzing", "matching", "generating") ->
                            t("eaz.reference_search.match_cta_generating", "Nothing a match? We are creating designs for you.")
                        else -> null
                    }

                    Column(Modifier.fillMaxSize()) {
                        Text(
                            text = searchTitle,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                        )
                        if (status in setOf("queued", "analyzing", "matching", "generating")) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = EazColors.Orange,
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(analyzing, fontSize = 13.sp, color = EazColors.TextSecondary)
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            RefTabChip("$tabProducts (${products.size})", tab == 0) { tab = 0 }
                            RefTabChip("$tabDesigns (${designs.size})", tab == 1) { tab = 1 }
                            RefTabChip("$tabCreations ($creationCount)", tab == 2) { tab = 2 }
                        }
                        HorizontalDivider(color = EazColors.TopbarBorder, modifier = Modifier.padding(top = 8.dp))

                        when (tab) {
                            2 -> {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    RefTabChip("$subtabAvailable (${available.size})", creationsSubtab == 0) {
                                        creationsSubtab = 0
                                    }
                                    RefTabChip("$subtabSaved (${saved.size})", creationsSubtab == 1) {
                                        creationsSubtab = 1
                                    }
                                }
                                val creationItems = if (creationsSubtab == 0) available else saved
                                val empty = when {
                                    allCreations.isEmpty() -> emptyCreations
                                    creationsSubtab == 0 -> emptyAvailable
                                    else -> emptySaved
                                }
                                if (creationItems.isEmpty()) {
                                    Text(
                                        empty,
                                        modifier = Modifier.padding(24.dp),
                                        color = EazColors.TextSecondary,
                                        fontSize = 14.sp,
                                    )
                                } else {
                                    LazyVerticalGrid(
                                        columns = GridCells.Fixed(2),
                                        contentPadding = PaddingValues(12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxSize(),
                                    ) {
                                        items(creationItems, key = { "c${it.slot}-${it.saveStatus}" }) { c ->
                                            Box {
                                            Column(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .border(1.dp, EazColors.TopbarBorder, RoundedCornerShape(10.dp))
                                                    .clickable { previewCreation = c },
                                            ) {
                                                AsyncImage(
                                                    model = ImageRequest.Builder(LocalContext.current)
                                                        .data(c.previewUrl)
                                                        .crossfade(true)
                                                        .build(),
                                                    contentDescription = "Creation ${c.slot}",
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .aspectRatio(1f)
                                                        .background(Color(0xFFF5F5F5)),
                                                )
                                                Text(
                                                    text = when (c.saveStatus) {
                                                        "saving" -> savingLabel
                                                        "save_failed" -> saveFailedLabel
                                                        "saved" -> subtabSaved
                                                        else -> "Creation ${c.slot}"
                                                    },
                                                    modifier = Modifier.padding(8.dp),
                                                    fontSize = 12.sp,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                    color = EazColors.TextPrimary,
                                                )
                                            }
                                            if (c.previewUrl.isNotBlank()) {
                                                IconButton(
                                                    onClick = {
                                                        menuSpec = Triple(
                                                            c.previewUrl,
                                                            c.designId.takeIf { it.isNotBlank() },
                                                            c.saveStatus == "available" || c.saveStatus == "save_failed"
                                                        )
                                                    },
                                                    modifier = Modifier.align(Alignment.TopEnd).size(32.dp)
                                                ) {
                                                    Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color.White)
                                                }
                                            }
                                            }
                                        }
                                    }
                                }
                            }
                            else -> {
                                val items = if (tab == 1) designs else products
                                val empty = if (tab == 1) emptyDesigns else emptyProducts
                                if (items.isEmpty()) {
                                    Column(Modifier.padding(24.dp)) {
                                        Text(
                                            empty,
                                            color = EazColors.TextSecondary,
                                            fontSize = 14.sp,
                                        )
                                        if (matchCta != null) {
                                            TextButton(onClick = { tab = 2 }) { Text(matchCta) }
                                        }
                                    }
                                } else {
                                    LazyVerticalGrid(
                                        columns = GridCells.Fixed(2),
                                        contentPadding = PaddingValues(12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxSize(),
                                    ) {
                                        items(items, key = { it.designId + it.previewUrl }) { m ->
                                            Box {
                                            Column(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .border(1.dp, EazColors.TopbarBorder, RoundedCornerShape(10.dp))
                                                    .clickable {
                                                        if (tab == 1 && m.previewUrl.isNotBlank()) {
                                                            onCreateProduct(
                                                                RefSearchCreateProductRequest(
                                                                    designUrl = m.previewUrl,
                                                                    designId = m.designId.takeIf { it.isNotBlank() },
                                                                ),
                                                            )
                                                            onDismiss()
                                                        } else if (m.handle.isNotBlank()) {
                                                            onNavigateToUrl("/products/${m.handle}")
                                                        }
                                                    },
                                            ) {
                                                AsyncImage(
                                                    model = ImageRequest.Builder(LocalContext.current)
                                                        .data(m.previewUrl)
                                                        .crossfade(true)
                                                        .build(),
                                                    contentDescription = m.title,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .aspectRatio(1f)
                                                        .background(Color(0xFFF5F5F5)),
                                                )
                                                Text(
                                                    text = m.title.ifBlank { "Design" },
                                                    modifier = Modifier.padding(8.dp),
                                                    fontSize = 12.sp,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                    color = EazColors.TextPrimary,
                                                )
                                            }
                                            if (tab == 1 && m.previewUrl.isNotBlank()) {
                                                IconButton(
                                                    onClick = { menuSpec = Triple(m.previewUrl, m.designId.takeIf { it.isNotBlank() }, false) },
                                                    modifier = Modifier.align(Alignment.TopEnd).size(32.dp)
                                                ) {
                                                    Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color.White)
                                                }
                                            }
                                            }
                                        }
                                        if (matchCta != null) {
                                            item(span = { GridItemSpan(2) }, key = "match-cta") {
                                                TextButton(onClick = { tab = 2 }) { Text(matchCta) }
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

        val preview = previewCreation
        if (preview != null) {
            Dialog(onDismissRequest = { if (!previewBusy) previewCreation = null }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White)
                        .padding(16.dp),
                ) {
                    Text(previewTitle, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Spacer(Modifier.height(12.dp))
                    AsyncImage(
                        model = preview.previewUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFF5F5F5)),
                    )
                    Spacer(Modifier.height(12.dp))
                    if (previewBusy) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = EazColors.Orange,
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(savingLabel, fontSize = 13.sp, color = EazColors.TextSecondary)
                        }
                    } else {
                        val sid = selectedId
                        if (preview.saveStatus == "available" || preview.saveStatus == "save_failed") {
                            Button(
                                onClick = {
                                    if (sid.isNullOrBlank()) return@Button
                                    scope.launch {
                                        previewBusy = true
                                        try {
                                            val res = creatorApi.referenceSearchCreationSave(ownerId, sid, preview.slot)
                                            if (!res.optBoolean("ok", false)) {
                                                banner = res.optString("message").ifBlank {
                                                    res.optString("error").ifBlank { saveFailedLabel }
                                                }
                                            } else {
                                                creationsSubtab = 1
                                                val refreshed = creatorApi.referenceSearchGet(ownerId, sid)
                                                detailJson = refreshed
                                            }
                                        } catch (e: Exception) {
                                            banner = e.message ?: saveFailedLabel
                                        } finally {
                                            previewBusy = false
                                            previewCreation = null
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = EazColors.Orange),
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(saveLabel) }
                        }
                        if (preview.saveStatus == "saved") {
                            Button(
                                onClick = {
                                    if (preview.previewUrl.isBlank()) return@Button
                                    onCreateProduct(
                                        RefSearchCreateProductRequest(
                                            designUrl = preview.previewUrl,
                                            designId = preview.designId.takeIf { it.isNotBlank() },
                                        ),
                                    )
                                    previewCreation = null
                                    onDismiss()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = EazColors.Orange),
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(createProductLabel) }
                        }
                        Spacer(Modifier.height(8.dp))
                        TextButton(
                            onClick = {
                                if (sid.isNullOrBlank()) return@TextButton
                                scope.launch {
                                    previewBusy = true
                                    try {
                                        creatorApi.referenceSearchCreationRemove(ownerId, sid, preview.slot)
                                        val refreshed = creatorApi.referenceSearchGet(ownerId, sid)
                                        detailJson = refreshed
                                    } catch (e: Exception) {
                                        banner = e.message
                                    } finally {
                                        previewBusy = false
                                        previewCreation = null
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(removeLabel, color = Color(0xFFB91C1C)) }
                        TextButton(
                            onClick = { previewCreation = null },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(t("eaz.reference_search.cancel", "Cancel")) }
                    }
                }
            }
        }
    }

    val menu = menuSpec
    if (menu != null) {
        com.eazpire.creator.ui.designrequest.DesignActionMenu(
            visible = true,
            imageUrl = menu.first,
            designId = menu.second,
            canOpenStudio = false,
            canDelete = menu.third,
            onDismiss = { menuSpec = null },
            onDelete = null,
            onUseOnProduct = { url, id ->
                onCreateProduct(RefSearchCreateProductRequest(designUrl = url, designId = id))
                onDismiss()
            },
        )
    }
}

@Composable
private fun RefTabChip(label: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(
            contentColor = if (selected) EazColors.Orange else EazColors.TextSecondary,
        ),
    ) {
        Text(
            text = label,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun RefSearchHistoryRow(
    item: RefSearchListItem,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    deleteLabel: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, EazColors.TopbarBorder, RoundedCornerShape(10.dp))
            .clickable(onClick = onOpen)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = item.thumbUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFF5F5F5)),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.title,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                item.status,
                fontSize = 12.sp,
                color = EazColors.TextSecondary,
            )
        }
        if (item.openedAt == null && item.status == "done") {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(EazColors.Orange),
            )
            Spacer(Modifier.width(8.dp))
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = deleteLabel, tint = EazColors.TextSecondary)
        }
    }
}

private fun parseMatches(arr: org.json.JSONArray?): List<RefMatchItem> {
    if (arr == null) return emptyList()
    val out = mutableListOf<RefMatchItem>()
    for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        out.add(
            RefMatchItem(
                designId = o.optString("design_id").ifBlank { "m$i" },
                title = o.optString("title"),
                previewUrl = o.optString("preview_url").ifBlank {
                    o.optString("listing_image_url").ifBlank { o.optString("image_url") }
                },
                score = o.optDouble("score", 0.0),
                handle = o.optString("shopify_handle"),
            ),
        )
    }
    return out
}

private fun parseCreations(arr: org.json.JSONArray?): List<RefCreationItem> {
    if (arr == null) return emptyList()
    val out = mutableListOf<RefCreationItem>()
    for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        val saveStatus = o.optString("save_status").ifBlank { "available" }
        if (saveStatus == "removed") continue
        val preview = o.optString("preview_url")
        val status = o.optString("status")
        if (preview.isBlank() && status != "done") continue
        out.add(
            RefCreationItem(
                slot = o.optInt("slot", i + 1),
                designId = o.optString("design_id"),
                previewUrl = preview,
                jobId = o.optString("job_id"),
                status = status,
                saveStatus = saveStatus,
                saveError = o.optString("save_error"),
            ),
        )
    }
    return out
}

private fun readUriBytes(context: Context, uri: Uri): ByteArray? {
    return try {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    } catch (_: Exception) {
        null
    }
}
