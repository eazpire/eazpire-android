package com.eazpire.creator.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.CreatorApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

private data class EazyQiItem(
    val id: String,
    val thumbUrl: String,
    val imageUrl: String,
)

private fun parseQiItems(res: JSONObject): Pair<List<EazyQiItem>, Int> {
    val arr = res.optJSONArray("items") ?: res.optJSONArray("designs")
    val out = mutableListOf<EazyQiItem>()
    if (arr != null) {
        for (i in 0 until arr.length()) {
            val row = arr.optJSONObject(i) ?: continue
            val image = row.optString("image_url").trim()
            val thumb = row.optString("thumb_url").trim().ifBlank { image }
            if (image.isBlank() && thumb.isBlank()) continue
            out.add(
                EazyQiItem(
                    id = row.optString("id").ifBlank { "$i" },
                    thumbUrl = thumb.ifBlank { image },
                    imageUrl = image.ifBlank { thumb },
                )
            )
        }
    }
    return out to res.optInt("total", out.size)
}

@Composable
fun EazyQuickInspirationsPanel(
    api: CreatorApi,
    ownerId: String?,
    t: (String, String) -> String,
    modifier: Modifier = Modifier,
) {
    val palette = LocalEazyModalPalette.current
    val ioScope = rememberCoroutineScope()
    var tab by remember { mutableStateOf("public") }
    var origin by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<EazyQiItem>>(emptyList()) }
    var total by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var previewUrl by remember { mutableStateOf<String?>(null) }

    fun emptyMessage(forTab: String): String = if (forTab == "yours") {
        t("eazy_chat.qi_empty_yours", "You have no Quick Inspirations yet.")
    } else {
        t("eazy_chat.qi_empty", "No quick inspirations yet.")
    }

    suspend fun fetchPage(append: Boolean) {
        if (tab == "yours" && ownerId.isNullOrBlank()) {
            items = emptyList()
            total = 0
            error = t("eazy_chat.qi_empty_yours_login", "Sign in to see your Quick Inspirations.")
            loading = false
            return
        }
        loading = true
        if (!append) error = null
        try {
            val res = api.listQuickInspirations(
                ownerId = ownerId,
                mine = tab == "yours",
                excludeMine = tab == "public" && !ownerId.isNullOrBlank(),
                search = query,
                origin = origin.ifBlank { null },
                limit = 36,
                offset = if (append) items.size else 0,
            )
            val parsed = parseQiItems(res)
            items = if (append) items + parsed.first else parsed.first
            total = parsed.second
            if (items.isEmpty()) error = emptyMessage(tab)
        } catch (_: Exception) {
            if (!append) {
                items = emptyList()
                total = 0
                error = t("eazy_chat.qi_error", "Could not load inspirations.")
            }
        } finally {
            loading = false
        }
    }

    LaunchedEffect(search) {
        delay(280)
        query = search.trim()
    }

    LaunchedEffect(tab, origin, query, ownerId) {
        fetchPage(append = false)
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EazyQiChip(
                label = t("eazy_chat.qi_tab_public", "Public"),
                active = tab == "public",
                palette = palette,
                onClick = { tab = "public" },
            )
            EazyQiChip(
                label = t("eazy_chat.qi_tab_yours", "Yours"),
                active = tab == "yours",
                palette = palette,
                onClick = { tab = "yours" },
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            EazyQiChip(t("eazy_chat.qi_origin_all", "All"), origin.isBlank(), palette) { origin = "" }
            EazyQiChip(t("eazy_chat.qi_origin_user", "Community"), origin == "user", palette) { origin = "user" }
            EazyQiChip(t("eazy_chat.qi_origin_research", "Research"), origin == "research", palette) { origin = "research" }
            EazyQiChip(t("eazy_chat.qi_origin_trend", "Trends"), origin == "trend_radar", palette) { origin = "trend_radar" }
        }
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            singleLine = true,
            placeholder = { Text(t("eazy_chat.qi_search_placeholder", "Search inspirations…")) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = palette.accent,
                unfocusedBorderColor = palette.muted.copy(alpha = 0.35f),
                focusedTextColor = palette.text,
                unfocusedTextColor = palette.text,
                cursorColor = palette.accent,
                focusedPlaceholderColor = palette.muted,
                unfocusedPlaceholderColor = palette.muted,
            ),
            shape = RoundedCornerShape(10.dp),
        )

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                loading && items.isEmpty() -> {
                    CircularProgressIndicator(
                        color = palette.accent,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                error != null && items.isEmpty() -> {
                    Text(
                        text = error ?: "",
                        color = palette.muted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                    )
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 110.dp),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(items, key = { it.id }) { item ->
                            AsyncImage(
                                model = item.thumbUrl,
                                contentDescription = t("eazy_chat.qi_preview_open", "Open preview"),
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(110.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.Black.copy(alpha = 0.2f))
                                    .clickable { previewUrl = item.imageUrl },
                            )
                        }
                        if (items.isNotEmpty() && items.size < total) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                TextButton(
                                    onClick = { ioScope.launch { fetchPage(append = true) } },
                                    enabled = !loading,
                                ) {
                                    Text(t("eazy_chat.qi_load_more", "Load more"), color = palette.accent)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val preview = previewUrl
    if (preview != null) {
        Dialog(
            onDismissRequest = { previewUrl = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.86f))
                    .clickable { previewUrl = null },
            ) {
                AsyncImage(
                    model = preview,
                    contentDescription = t("eazy_chat.qi_preview_aria", "Inspiration preview"),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .clickable(enabled = false) {},
                )
                IconButton(
                    onClick = { previewUrl = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .background(Color.Black.copy(alpha = 0.55f), CircleShape),
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = t("eazy_chat.qi_preview_close", "Close preview"),
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun EazyQiChip(
    label: String,
    active: Boolean,
    palette: EazyModalPalette,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        color = if (active) Color.White else palette.text,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (active) EazColors.Orange else palette.muted.copy(alpha = 0.12f))
            .border(
                width = 1.dp,
                color = if (active) EazColors.Orange else palette.muted.copy(alpha = 0.28f),
                shape = RoundedCornerShape(999.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}
