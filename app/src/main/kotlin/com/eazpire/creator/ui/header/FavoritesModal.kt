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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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

/** Favorite item – pool uses product_id|variant_id, list items use id */
data class FavoriteItem(
    val id: String,
    val itemId: Long,
    val productId: String,
    val variantId: String?,
    val productTitle: String,
    val productImage: String?,
    val variantTitle: String?
)

data class FavoriteListInfo(val id: Long, val name: String, val itemsCount: Int)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesModal(
    visible: Boolean,
    customerId: String?,
    api: CreatorApi,
    onDismiss: () -> Unit,
    onCountChange: (Int) -> Unit = {}
) {
    if (!visible) return

    val context = LocalContext.current
    val scope = remember { CoroutineScope(Dispatchers.Main) }
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
    var editListId by remember { mutableStateOf(0L) }
    var editListName by remember { mutableStateOf("") }

    fun loadPool() {
        scope.launch {
            if (customerId.isNullOrBlank()) return@launch
            try {
                val resp = api.getFavorites(customerId)
                if (resp.optBoolean("ok", false)) {
                    val arr = resp.optJSONArray("items") ?: org.json.JSONArray()
                    poolItems = (0 until arr.length()).map { i ->
                        val obj = arr.optJSONObject(i) ?: JSONObject()
                        val img = obj.optString("product_image", null).takeIf { it.isNullOrBlank().not() }
                        FavoriteItem(
                            id = "${obj.optString("product_id")}|${obj.optString("variant_id", "")}",
                            itemId = 0L,
                            productId = obj.optString("product_id", ""),
                            variantId = obj.optString("variant_id", null).takeIf { it.isNullOrBlank().not() },
                            productTitle = obj.optString("product_title", "Product"),
                            productImage = normalizeImageUrl(img),
                            variantTitle = obj.optString("variant_title", null).takeIf { it.isNullOrBlank().not() }
                        )
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
                        val obj = arr.optJSONObject(i) ?: JSONObject()
                        val img = obj.optString("product_image", null).takeIf { it.isNullOrBlank().not() }
                        FavoriteItem(
                            id = obj.optString("id", ""),
                            itemId = obj.optLong("id", 0L),
                            productId = obj.optString("product_id", ""),
                            variantId = obj.optString("variant_id", null).takeIf { it.isNullOrBlank().not() },
                            productTitle = obj.optString("product_title", "Product"),
                            productImage = normalizeImageUrl(img),
                            variantTitle = obj.optString("variant_title", null).takeIf { it.isNullOrBlank().not() }
                        )
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
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            item {
                                AddProductPlaceholderCard(
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.eazpire.com"))
                                        context.startActivity(Intent.createChooser(intent, "Open shop"))
                                    }
                                )
                            }
                            items(items) { item ->
                                FavoriteGridCard(
                                    item = item,
                                    onRemove = {
                                        scope.launch {
                                            if (activeView == "pool") {
                                                api.removeFavorite(customerId!!, item.productId, item.variantId)
                                                loadPool()
                                            } else {
                                                api.removeFromFavoriteList(customerId!!, activeView.toLong(), item.itemId)
                                                loadListItems(activeView.toLong())
                                                loadLists()
                                            }
                                        }
                                    }
                                )
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
                onConfirm = {
                    scope.launch {
                        api.createFavoriteList(customerId!!, newListName.trim())
                        loadLists()
                        newListName = ""
                        showCreateListModal = false
                    }
                },
                onDismiss = { showCreateListModal = false; newListName = "" }
            )
        }
        if (showSaveAsListModal) {
            CreateListModal(
                name = newListName,
                onNameChange = { newListName = it },
                onConfirm = {
                    scope.launch {
                        api.saveFavoritesAsList(customerId!!, newListName.trim())
                        loadPool()
                        loadLists()
                        newListName = ""
                        showSaveAsListModal = false
                        activeView = "pool"
                    }
                },
                onDismiss = { showSaveAsListModal = false; newListName = "" },
                title = "Save as list"
            )
        }
        if (showEditListModal) {
            CreateListModal(
                name = editListName,
                onNameChange = { editListName = it },
                onConfirm = {
                    scope.launch {
                        api.updateFavoriteList(customerId!!, editListId, editListName.trim())
                        loadLists()
                        listName = editListName.trim()
                        showEditListModal = false
                    }
                },
                onDismiss = { showEditListModal = false },
                title = "Edit list"
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
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    item.productTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = EazColors.TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!item.variantTitle.isNullOrBlank()) {
                    Text(
                        item.variantTitle!!,
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
private fun CreateListModal(
    name: String,
    onNameChange: (String) -> Unit,
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
