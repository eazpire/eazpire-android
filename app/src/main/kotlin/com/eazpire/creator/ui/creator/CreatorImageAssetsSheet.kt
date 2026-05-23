package com.eazpire.creator.ui.creator

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.i18n.TranslationStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class CreatorImageAssetRow(
    val id: String,
    val imageUrl: String,
    val r2Key: String,
    val imageType: String
)

@Composable
fun CreatorImageAssetsSheet(
    ownerId: String,
    creatorName: String,
    imageCategory: String,
    api: CreatorApi,
    translationStore: TranslationStore,
    onDismiss: () -> Unit,
    onSelect: (CreatorImageAssetRow) -> Unit
) {
    val tr = { k: String, d: String -> translationStore.t(k, d) }
    var loading by remember { mutableStateOf(true) }
    var items by remember { mutableStateOf<List<CreatorImageAssetRow>>(emptyList()) }

    LaunchedEffect(ownerId, creatorName, imageCategory) {
        loading = true
        try {
            val r = withContext(Dispatchers.IO) {
                api.listCreatorImageAssets(ownerId, creatorName, imageCategory)
            }
            val arr = r.optJSONArray("items") ?: JSONArray()
            val list = mutableListOf<CreatorImageAssetRow>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("id", "")
                if (id.isBlank()) continue
                val url = o.optString("image_url", "")
                if (url.isBlank()) continue
                list.add(
                    CreatorImageAssetRow(
                        id = id,
                        imageUrl = url,
                        r2Key = o.optString("r2_key", ""),
                        imageType = o.optString("image_type", "custom")
                    )
                )
            }
            items = list
        } catch (_: Exception) {
            items = emptyList()
        }
        loading = false
    }

    val isAvatar = imageCategory == "avatar"
    val columns = if (isAvatar) GridCells.Adaptive(minSize = 100.dp) else GridCells.Fixed(1)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF111827)
        ) {
            Column(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F172A))
                        .padding(8.dp)
                ) {
                    Text(
                        tr("creator.detail_modal.assets_title", "Your images"),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        modifier = Modifier.align(Alignment.Center)
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterEnd)) {
                        Icon(Icons.Default.Close, null, tint = Color.White)
                    }
                }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    when {
                        loading -> CircularProgressIndicator(
                            color = EazColors.Orange,
                            modifier = Modifier.align(Alignment.Center)
                        )
                        items.isEmpty() -> Text(
                            tr("creator.detail_modal.assets_empty", "No saved images yet. Upload one first."),
                            color = Color.White.copy(alpha = 0.65f),
                            modifier = Modifier.align(Alignment.Center)
                        )
                        else -> LazyVerticalGrid(
                            columns = columns,
                            contentPadding = PaddingValues(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(items, key = { it.id }) { row ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(if (isAvatar) 1f else 21f / 9f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color.White.copy(alpha = 0.06f))
                                        .clickable { onSelect(row) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    AsyncImage(
                                        model = row.imageUrl,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(4.dp),
                                        contentScale = ContentScale.Fit
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
