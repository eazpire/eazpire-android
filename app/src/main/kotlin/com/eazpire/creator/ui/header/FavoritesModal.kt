package com.eazpire.creator.ui.header

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.List
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.favorites.FavoritesRefreshTrigger
import androidx.compose.runtime.rememberCoroutineScope
import org.json.JSONObject

private fun normalizeImageUrl(url: String?): String? {
    if (url.isNullOrBlank()) return null
    val s = url.trim()
    return when {
        s.startsWith("//") -> "https:$s"
        s.startsWith("/") -> "https://www.eazpire.com$s"
        else -> s
    }
}

/** JSONObject.optString returns the literal "null" for JSON null values — treat as absent. */
private fun jsonOptString(obj: JSONObject, key: String): String? {
    if (!obj.has(key) || obj.isNull(key)) return null
    val v = obj.optString(key, "").trim()
    if (v.isBlank() || v.equals("null", ignoreCase = true)) return null
    return v
}

private fun displayVariantTitle(raw: String?): String? {
    val v = raw?.trim().orEmpty()
    if (v.isBlank() || v.equals("null", ignoreCase = true)) return null
    return v
}

/** Favorite item – pool uses product_id|variant_id, list items use id */
data class FavoriteItem(
    val id: String,
    val itemId: Long,
    val productId: String,
    val productHandle: String? = null,
    val variantId: String?,
    val productTitle: String,
    val productImage: String?,
    val variantTitle: String?
)

data class FavoriteListInfo(val id: Long, val name: String, val description: String, val itemsCount: Int)

private data class ShopPickerProduct(
    val id: String,
    val handle: String,
    val title: String,
    val image: String?,
    val price: Double,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesModal(
    visible: Boolean,
    customerId: String?,
    api: CreatorApi,
    tokenStore: SecureTokenStore?,
    onDismiss: () -> Unit,
    onCountChange: (Int) -> Unit = {},
    onProductClick: ((String) -> Unit)? = null,
    onEditFavorite: ((FavoriteEditContext) -> Unit)? = null,
) {
    if (!visible) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var drawerOpen by remember { mutableStateOf(false) }
    var activeView by remember { mutableStateOf("pool") }
    var poolItems by remember { mutableStateOf<List<FavoriteItem>>(emptyList()) }
    var lists by remember { mutableStateOf<List<FavoriteListInfo>>(emptyList()) }
    var listItems by remember { mutableStateOf<List<FavoriteItem>>(emptyList()) }
    var listName by remember { mutableStateOf("") }
    var listShareToken by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var showCreateListModal by remember { mutableStateOf(false) }
    var showSaveAsListModal by remember { mutableStateOf(false) }
    var showEditListModal by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var newListName by remember { mutableStateOf("") }
    var newListDescription by remember { mutableStateOf("") }
    var editListId by remember { mutableStateOf(0L) }
    var editListName by remember { mutableStateOf("") }
    var editListDescription by remember { mutableStateOf("") }
    var handleByProductId by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var showProductPicker by remember { mutableStateOf(false) }
    var shopProducts by remember { mutableStateOf<List<ShopPickerProduct>>(emptyList()) }
    var shopProductsLoading by remember { mutableStateOf(false) }

    fun mapFavoriteItem(obj: JSONObject, isPool: Boolean): FavoriteItem {
        val img = jsonOptString(obj, "product_image")
        val productId = jsonOptString(obj, "product_id") ?: ""
        val variantId = jsonOptString(obj, "variant_id")
        val handleFromApi = jsonOptString(obj, "product_handle")
        val handle = handleFromApi ?: handleByProductId[productId]
        return FavoriteItem(
            id = if (isPool) {
                "pool|${productId.ifBlank { "0" }}|${variantId.orEmpty()}"
            } else {
                val listRowId = obj.optLong("id", 0L)
                if (listRowId > 0L) "list|$listRowId" else (jsonOptString(obj, "id") ?: "list|${obj.hashCode()}")
            },
            itemId = if (isPool) 0L else obj.optLong("id", 0L),
            productId = productId,
            productHandle = handle,
            variantId = variantId,
            productTitle = jsonOptString(obj, "product_title") ?: "Product",
            productImage = normalizeImageUrl(img),
            variantTitle = displayVariantTitle(jsonOptString(obj, "variant_title"))
        )
    }

    fun loadProductHandles() {
        scope.launch {
            try {
                val resp = api.getShopifyProducts(shop = "eazpire.myshopify.com", ownerId = customerId)
                if (!resp.optBoolean("ok", false)) return@launch
                val arr = resp.optJSONArray("products") ?: return@launch
                val map = mutableMapOf<String, String>()
                val list = mutableListOf<ShopPickerProduct>()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = jsonOptString(o, "id") ?: continue
                    val handle = jsonOptString(o, "handle") ?: continue
                    map[id] = handle
                    list.add(
                        ShopPickerProduct(
                            id = id,
                            handle = handle,
                            title = jsonOptString(o, "title") ?: handle,
                            image = normalizeImageUrl(jsonOptString(o, "image")),
                            price = o.optDouble("price", 0.0)
                        )
                    )
                }
                handleByProductId = map
                shopProducts = list
                poolItems = poolItems.map { it.copy(productHandle = it.productHandle ?: map[it.productId]) }
                listItems = listItems.map { it.copy(productHandle = it.productHandle ?: map[it.productId]) }
            } catch (_: Exception) {}
        }
    }

    fun loadPool() {
        scope.launch {
            if (customerId.isNullOrBlank()) return@launch
            try {
                val resp = api.getFavorites(customerId)
                if (resp.optBoolean("ok", false)) {
                    val arr = resp.optJSONArray("items") ?: org.json.JSONArray()
                    poolItems = (0 until arr.length()).map { i ->
                        mapFavoriteItem(arr.optJSONObject(i) ?: JSONObject(), isPool = true)
                    }
                    onCountChange(poolItems.size)
                }
            } catch (_: Exception) {}
        }
    }

    fun loadLists() {
        scope.launch {
            if (customerId.isNullOrBlank()) return@launch
            try {
                val resp = api.getFavoriteLists(customerId)
                if (resp.optBoolean("ok", false)) {
                    val arr = resp.optJSONArray("lists") ?: org.json.JSONArray()
                    lists = (0 until arr.length()).map { i ->
                        val obj = arr.optJSONObject(i) ?: JSONObject()
                        FavoriteListInfo(
                            id = obj.optLong("id", 0L),
                            name = obj.optString("name", ""),
                            description = obj.optString("description", ""),
                            itemsCount = obj.optInt("items_count", 0)
                        )
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun loadListItems(listId: Long) {
        scope.launch {
            if (customerId.isNullOrBlank()) return@launch
            loading = true
            listShareToken = null
            try {
                val resp = api.getFavoriteListItems(customerId!!, listId)
                if (resp.optBoolean("ok", false)) {
                    val listObj = resp.optJSONObject("list")
                    listName = listObj?.optString("name", "") ?: ""
                    val arr = resp.optJSONArray("items") ?: org.json.JSONArray()
                    listItems = (0 until arr.length()).map { i ->
                        mapFavoriteItem(arr.optJSONObject(i) ?: JSONObject(), isPool = false)
                    }
                    val token = listObj?.optString("share_token", null).takeIf { it.isNullOrBlank().not() }
                    if (token != null) {
                        listShareToken = token
                    } else {
                        val ensureResp = api.ensureFavoriteListShareToken(customerId, listId)
                        if (ensureResp.optBoolean("ok", false)) {
                            listShareToken = ensureResp.optString("share_token", null).takeIf { it.isNullOrBlank().not() }
                        }
                    }
                }
            } catch (_: Exception) {}
            loading = false
        }
    }

    LaunchedEffect(visible, customerId) {
        if (!visible || customerId.isNullOrBlank()) {
            poolItems = emptyList()
            lists = emptyList()
            listItems = emptyList()
            loading = false
            return@LaunchedEffect
        }
        loading = true
        try {
            loadProductHandles()
            loadPool()
            loadLists()
            if (activeView != "pool" && activeView.toLongOrNull() != null) {
                loadListItems(activeView.toLong())
            }
        } finally {
            loading = false
        }
    }

    LaunchedEffect(activeView) {
        if (customerId.isNullOrBlank()) return@LaunchedEffect
        if (activeView == "pool") {
            loadPool()
        } else {
            activeView.toLongOrNull()?.let { loadListItems(it) }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        modifier = Modifier.fillMaxHeight(0.9f)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { drawerOpen = !drawerOpen }) {
                        Icon(Icons.Default.Menu, contentDescription = "Lists", tint = EazColors.TextPrimary)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (activeView == "pool") "Unassigned" else listName.ifBlank { "List" },
                            style = MaterialTheme.typography.titleLarge,
                            color = EazColors.TextPrimary
                        )
                        val count = if (activeView == "pool") poolItems.size else listItems.size
                        Text(
                            "$count item${if (count != 1) "s" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = EazColors.TextSecondary
                        )
                    }
                    if (activeView == "pool" && poolItems.isNotEmpty()) {
                        TextButton(onClick = { showSaveAsListModal = true }) {
                            Icon(Icons.Outlined.List, null, Modifier.size(18.dp), tint = EazColors.Orange)
                            Spacer(Modifier.width(4.dp))
                            Text("Save", color = EazColors.Orange, style = MaterialTheme.typography.labelMedium)
                        }
                        TextButton(onClick = { showClearConfirm = true }) {
                            Icon(Icons.Default.Delete, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(4.dp))
                            Text("Clear", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    if (activeView != "pool" && listItems.isNotEmpty()) {
                        listShareToken?.let { token ->
                            val shareUrl = "https://www.eazpire.com/pages/my-favorites?share_token=${java.net.URLEncoder.encode(token, "UTF-8")}"
                            IconButton(onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                cm?.setPrimaryClip(ClipData.newPlainText("Share link", shareUrl))
                            }) {
                                Icon(Icons.Outlined.ContentCopy, "Copy", tint = EazColors.Orange)
                            }
                            IconButton(onClick = {
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, shareUrl)
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Share"))
                            }) {
                                Icon(Icons.Default.Share, "Share", tint = EazColors.Orange)
                            }
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = EazColors.TextPrimary)
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when {
                        customerId.isNullOrBlank() -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Log in to save favorites", style = MaterialTheme.typography.bodyMedium, color = EazColors.TextSecondary)
                            }
                        }
                        loading && listItems.isEmpty() && activeView != "pool" -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Loading...", style = MaterialTheme.typography.bodyMedium, color = EazColors.TextSecondary)
                            }
                        }
                        else -> {
                            val items = if (activeView == "pool") poolItems else listItems
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(top = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                item(key = "add_product") {
                                    AddProductPlaceholderCard(
                                        onClick = {
                                            if (shopProducts.isEmpty() && !shopProductsLoading) {
                                                shopProductsLoading = true
                                                loadProductHandles()
                                                shopProductsLoading = false
                                            }
                                            showProductPicker = true
                                        }
                                    )
                                }
                                items(
                                    items,
                                    key = { item ->
                                        when {
                                            item.itemId > 0L -> "fav-list-${item.itemId}"
                                            else -> item.id
                                        }
                                    },
                                ) { item ->
                                    FavoriteGridCard(
                                        item = item,
                                        onClick = {
                                            val handle = item.productHandle?.trim().orEmpty()
                                            if (handle.isNotBlank() && tokenStore != null && !customerId.isNullOrBlank() && onEditFavorite != null) {
                                                onEditFavorite(
                                                    FavoriteEditContext(
                                                        productHandle = handle,
                                                        customerId = customerId,
                                                        api = api,
                                                        productId = item.productId,
                                                        initialVariantId = item.variantId,
                                                        activeView = activeView,
                                                        itemId = item.itemId,
                                                        onSaved = {
                                                            if (activeView == "pool") loadPool() else loadListItems(activeView.toLong())
                                                            loadLists()
                                                        },
                                                        onDismiss = {},
                                                    )
                                                )
                                            } else if (handle.isNotBlank()) {
                                                onProductClick?.invoke(handle)
                                            }
                                        },
                                        onRemove = {
                                            if (activeView == "pool") {
                                                poolItems = poolItems.filter { it.id != item.id }
                                                onCountChange(poolItems.size)
                                            } else {
                                                listItems = listItems.filter { it.id != item.id }
                                            }
                                            scope.launch {
                                                try {
                                                    if (activeView == "pool") {
                                                        api.removeFavorite(customerId!!, item.productId, item.variantId)
                                                        FavoritesRefreshTrigger.trigger()
                                                        loadPool()
                                                    } else {
                                                        api.removeFromFavoriteList(customerId!!, activeView.toLong(), item.itemId)
                                                        loadListItems(activeView.toLong())
                                                        loadLists()
                                                    }
                                                } catch (_: Exception) {
                                                    if (activeView == "pool") loadPool() else loadListItems(activeView.toLong())
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (drawerOpen) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f))
                        .clickable { drawerOpen = false }
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxHeight()
                        .width(280.dp)
                        .background(Color.White)
                ) {
                    FavoritesSidebar(
                        poolCount = poolItems.size,
                        lists = lists,
                        activeView = activeView,
                        onSelect = { activeView = it; drawerOpen = false },
                        onNewList = { showCreateListModal = true },
                        onEditList = { list ->
                            editListId = list.id
                            editListName = list.name
                            editListDescription = list.description
                            showEditListModal = true
                        },
                        onDuplicateList = { listId ->
                            scope.launch {
                                api.duplicateFavoriteList(customerId!!, listId)
                                loadLists()
                            }
                        },
                        onDeleteList = { listId ->
                            scope.launch {
                                api.deleteFavoriteList(customerId!!, listId)
                                loadLists()
                                if (activeView == listId.toString()) {
                                    activeView = "pool"
                                    listItems = emptyList()
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(12.dp)
                    )
                }
            }
        }

        if (showCreateListModal) {
            CreateListModal(
                name = newListName,
                onNameChange = { newListName = it },
                description = newListDescription,
                onDescriptionChange = { newListDescription = it },
                onConfirm = {
                    scope.launch {
                        api.createFavoriteList(customerId!!, newListName.trim(), newListDescription.trim().takeIf { it.isNotBlank() })
                        loadLists()
                        newListName = ""
                        newListDescription = ""
                        showCreateListModal = false
                    }
                },
                onDismiss = { showCreateListModal = false; newListName = ""; newListDescription = "" }
            )
        }
        if (showSaveAsListModal) {
            CreateListModal(
                name = newListName,
                onNameChange = { newListName = it },
                description = newListDescription,
                onDescriptionChange = { newListDescription = it },
                onConfirm = {
                    scope.launch {
                        api.saveFavoritesAsList(customerId!!, newListName.trim(), newListDescription.trim().takeIf { it.isNotBlank() })
                        loadPool()
                        loadLists()
                        newListName = ""
                        newListDescription = ""
                        showSaveAsListModal = false
                        activeView = "pool"
                    }
                },
                onDismiss = { showSaveAsListModal = false; newListName = ""; newListDescription = "" },
                title = "Save as list"
            )
        }
        if (showEditListModal) {
            CreateListModal(
                name = editListName,
                onNameChange = { editListName = it },
                description = editListDescription,
                onDescriptionChange = { editListDescription = it },
                onConfirm = {
                    scope.launch {
                        api.updateFavoriteList(customerId!!, editListId, editListName.trim(), editListDescription.trim().ifBlank { null })
                        loadLists()
                        listName = editListName.trim()
                        showEditListModal = false
                    }
                },
                onDismiss = { showEditListModal = false },
                title = "Edit list"
            )
        }
        if (showProductPicker) {
            FavoriteProductPickerSheet(
                products = shopProducts,
                loading = shopProductsLoading,
                onDismiss = { showProductPicker = false },
                onSelect = { product ->
                    showProductPicker = false
                    scope.launch {
                        try {
                            if (activeView == "pool") {
                                api.addFavorite(
                                    customerId = customerId!!,
                                    productId = product.id,
                                    productTitle = product.title,
                                    productImage = product.image
                                )
                                loadPool()
                            } else {
                                api.addToFavoriteList(
                                    customerId = customerId!!,
                                    listId = activeView.toLong(),
                                    productId = product.id,
                                    productTitle = product.title,
                                    productImage = product.image
                                )
                                loadListItems(activeView.toLong())
                                loadLists()
                            }
                            FavoritesRefreshTrigger.trigger()
                        } catch (_: Exception) {}
                    }
                }
            )
        }

        if (showClearConfirm) {
            androidx.compose.ui.window.Dialog(onDismissRequest = { showClearConfirm = false }) {
                Column(
                    modifier = Modifier
                        .background(Color.White, RoundedCornerShape(16.dp))
                        .padding(24.dp)
                ) {
                    Text("Remove all from Unassigned?", style = MaterialTheme.typography.titleMedium, color = EazColors.TextPrimary)
                    Spacer(Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
                        Spacer(Modifier.width(8.dp))
                        TextButton(
                            onClick = {
                                scope.launch {
                                    api.clearFavorites(customerId!!)
                                    loadPool()
                                    showClearConfirm = false
                                }
                            },
                            colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) { Text("Clear all") }
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoritesSidebar(
    poolCount: Int,
    lists: List<FavoriteListInfo>,
    activeView: String,
    onSelect: (String) -> Unit,
    onNewList: () -> Unit,
    onEditList: (FavoriteListInfo) -> Unit,
    onDuplicateList: (Long) -> Unit,
    onDeleteList: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text("Lists", style = MaterialTheme.typography.labelMedium, color = EazColors.TextSecondary, modifier = Modifier.padding(bottom = 8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(if (activeView == "pool") EazColors.OrangeBg.copy(alpha = 0.4f) else Color.Transparent)
                .clickable { onSelect("pool") }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Favorite, null, tint = EazColors.Orange, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Unassigned", style = MaterialTheme.typography.bodyMedium, color = EazColors.TextPrimary, modifier = Modifier.weight(1f))
            Text("$poolCount", style = MaterialTheme.typography.labelSmall, color = EazColors.TextSecondary)
        }
        Spacer(Modifier.height(8.dp))
        lists.forEach { list ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (activeView == list.id.toString()) EazColors.OrangeBg.copy(alpha = 0.4f) else Color.Transparent)
                    .clickable { onSelect(list.id.toString()) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.List, null, tint = EazColors.TextSecondary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(list.name, style = MaterialTheme.typography.bodyMedium, color = EazColors.TextPrimary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${list.itemsCount}", style = MaterialTheme.typography.labelSmall, color = EazColors.TextSecondary)
                IconButton(onClick = { onEditList(list) }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Edit, null, tint = EazColors.TextSecondary, modifier = Modifier.size(14.dp))
                }
                IconButton(onClick = { onDuplicateList(list.id) }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.ContentCopy, null, tint = EazColors.TextSecondary, modifier = Modifier.size(14.dp))
                }
                IconButton(onClick = { onDeleteList(list.id) }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, EazColors.Orange.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .clickable { onNewList() }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Add, null, tint = EazColors.Orange, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("New list", style = MaterialTheme.typography.bodyMedium, color = EazColors.Orange)
        }
    }
}

@Composable
private fun AddProductPlaceholderCard(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .fillMaxWidth()
            .height(180.dp)
            .border(1.dp, EazColors.Orange.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .background(EazColors.OrangeBg.copy(alpha = 0.2f))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.Add, null, tint = EazColors.Orange, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(8.dp))
            Text("Add Product", style = MaterialTheme.typography.bodyMedium, color = EazColors.Orange)
        }
    }
}

@Composable
private fun FavoriteGridCard(
    item: FavoriteItem,
    onClick: () -> Unit = {},
    onRemove: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, EazColors.Orange.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            .background(Color.White)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    .background(EazColors.OrangeBg.copy(alpha = 0.3f))
                    .clickable(onClick = onClick)
            ) {
                if (!item.productImage.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(item.productImage)
                            .crossfade(true)
                            .build(),
                        contentDescription = item.productTitle,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(Icons.Default.Favorite, null, tint = EazColors.Orange.copy(alpha = 0.4f), modifier = Modifier.size(48.dp).align(Alignment.Center))
                }
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(32.dp)
                        .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(8.dp))
                ) {
                    Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                }
            }
            Column(
                modifier = Modifier
                    .padding(8.dp)
                    .clickable(onClick = onClick)
            ) {
                Text(
                    item.productTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = EazColors.TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                item.variantTitle?.let { vt ->
                    Text(
                        vt,
                        style = MaterialTheme.typography.labelSmall,
                        color = EazColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun FavoriteProductPickerSheet(
    products: List<ShopPickerProduct>,
    loading: Boolean,
    onDismiss: () -> Unit,
    onSelect: (ShopPickerProduct) -> Unit,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Add product", style = MaterialTheme.typography.titleLarge, color = EazColors.TextPrimary)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = EazColors.TextPrimary)
                }
            }
            Spacer(Modifier.height(12.dp))
            if (loading) {
                Text("Loading…", color = EazColors.TextSecondary)
            } else if (products.isEmpty()) {
                Text("No products found", color = EazColors.TextSecondary)
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(products, key = { it.id }) { product ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, EazColors.Orange.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                .clickable { onSelect(product) }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(EazColors.OrangeBg.copy(alpha = 0.3f))
                            ) {
                                if (!product.image.isNullOrBlank()) {
                                    AsyncImage(
                                        model = product.image,
                                        contentDescription = product.title,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(product.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                if (product.price > 0) {
                                    Text("CHF %.2f".format(product.price), style = MaterialTheme.typography.labelSmall, color = EazColors.Orange)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateListModal(
    name: String,
    onNameChange: (String) -> Unit,
    description: String,
    onDescriptionChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    title: String = "New list"
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .background(Color.White, RoundedCornerShape(16.dp))
                .padding(24.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = EazColors.TextPrimary)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("List name") },
                singleLine = true
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = description,
                onValueChange = onDescriptionChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Description") },
                minLines = 2,
                maxLines = 4
            )
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = onConfirm,
                    enabled = name.trim().isNotBlank()
                ) { Text("Save") }
            }
        }
    }
}
