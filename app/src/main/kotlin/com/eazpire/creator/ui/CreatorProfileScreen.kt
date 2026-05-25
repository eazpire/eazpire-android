package com.eazpire.creator.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import coil.compose.AsyncImage
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.i18n.LocalTranslationStore
import com.eazpire.creator.locale.LocaleStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class CreatorProfilePreview(
    val name: String,
    val avatarUrl: String? = null,
    val ratingAvg: Double? = null,
    val ratingCount: Int? = null,
    val productCount: Int = 0
)

data class CreatorShopProduct(
    val handle: String,
    val title: String,
    val imageUrl: String?,
    val price: String?
)

@Composable
fun CreatorProfileScreen(
    creatorName: String,
    api: CreatorApi,
    onBack: () -> Unit,
    onProductClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val store = LocalTranslationStore.current
    val tr = store?.translations?.collectAsState(initial = emptyMap())?.value
    val t = store?.let { { k: String, d: String -> it.t(k, d) } } ?: { _: String, d: String -> d }

    val context = LocalContext.current
    val localeStore = remember { LocaleStore(context) }
    val countryCode by localeStore.countryCode.collectAsState(initial = localeStore.getCountryCodeSync())
    val catalogRegion by localeStore.regionCode.collectAsState(initial = localeStore.getRegionCodeSync())

    var loading by remember(creatorName) { mutableStateOf(true) }
    var error by remember(creatorName) { mutableStateOf<String?>(null) }
    var profile by remember(creatorName) { mutableStateOf<CreatorProfilePreview?>(null) }
    var products by remember(creatorName) { mutableStateOf<List<CreatorShopProduct>>(emptyList()) }

    LaunchedEffect(creatorName, countryCode, catalogRegion) {
        loading = true
        error = null
        try {
            val profileJson = withContext(Dispatchers.IO) {
                api.getCreatorProfile(
                    creatorName = creatorName,
                    creatorSlug = creatorName,
                    region = catalogRegion
                )
            }
            if (!profileJson.optBoolean("ok", false)) {
                error = profileJson.optString("error", "profile_error")
                loading = false
                return@LaunchedEffect
            }
            val ratingObj = profileJson.optJSONObject("rating")
            val avatarObj = profileJson.optJSONObject("avatar")
            val resolvedName = profileJson.optString("creator_name", creatorName).ifBlank { creatorName }
            val ownerId = profileJson.optString("owner_id", "").trim().ifBlank { null }

            val productsJson = withContext(Dispatchers.IO) {
                api.getCreatorShopProducts(
                    creatorName = resolvedName,
                    creatorSlug = creatorName,
                    ownerId = ownerId,
                    country = countryCode,
                    region = catalogRegion
                )
            }
            val list = mutableListOf<CreatorShopProduct>()
            if (productsJson.optBoolean("ok", false)) {
                val arr = productsJson.optJSONArray("products") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val handle = o.optString("handle", "").trim()
                    if (handle.isEmpty()) continue
                    list.add(
                        CreatorShopProduct(
                            handle = handle,
                            title = o.optString("title", handle),
                            imageUrl = run {
                                val preview = o.optString("preview_image_url", "").trim()
                                if (preview.isNotBlank()) preview
                                else o.optJSONArray("images")
                                    ?.optJSONObject(0)
                                    ?.optString("src", "")
                                    ?.trim()
                                    ?.takeIf { it.isNotBlank() }
                            },
                            price = o.optString("price", "").trim().ifBlank { null }
                        )
                    )
                }
            }
            products = list
            profile = CreatorProfilePreview(
                name = resolvedName,
                avatarUrl = avatarObj?.optString("image_url", "")?.trim()?.ifBlank { null },
                ratingAvg = ratingObj?.optDouble("avg")?.takeIf { it > 0.0 }
                    ?: ratingObj?.optDouble("rating")?.takeIf { it > 0.0 },
                ratingCount = ratingObj?.optInt("count")?.takeIf { it > 0 }
                    ?: ratingObj?.optInt("rating_count")?.takeIf { it > 0 },
                productCount = list.size
            )
        } catch (e: Exception) {
            error = e.message ?: "error"
        } finally {
            loading = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
    ) {
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = EazColors.Orange)
            }
            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    t("eaz.creator_profile.error_profile", "Could not load this creator profile."),
                    color = MaterialTheme.colorScheme.error
                )
            }
            else -> {
                val p = profile
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        CreatorAvatarCircle(
                            name = p?.name ?: creatorName,
                            avatarUrl = p?.avatarUrl,
                            size = 72.dp
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = p?.name ?: creatorName,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = EazColors.TextPrimary
                            )
                            if (p?.ratingAvg != null && p.ratingCount != null) {
                                Spacer(Modifier.height(6.dp))
                                CreatorRatingRow(avg = p.ratingAvg, count = p.ratingCount)
                            }
                            if ((p?.productCount ?: 0) > 0) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    t("eaz.creator_profile.products_count", "{{ count }} products")
                                        .replace("{{ count }}", p!!.productCount.toString()),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = EazColors.TextSecondary
                                )
                            }
                        }
                    }
                    if (products.isEmpty()) {
                        Text(
                            t("eaz.creator_profile.empty_products", "No products from this creator yet."),
                            modifier = Modifier.padding(16.dp),
                            color = EazColors.TextSecondary
                        )
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(products, key = { it.handle }) { item ->
                                Column(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color.White)
                                        .clickable { onProductClick(item.handle) }
                                        .padding(8.dp)
                                ) {
                                    AsyncImage(
                                        model = item.imageUrl,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFFEFEFEF)),
                                        contentScale = ContentScale.Crop
                                    )
                                    Text(
                                        text = item.title,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(top = 6.dp)
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

@Composable
fun CreatorAvatarCircle(
    name: String,
    avatarUrl: String?,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val clickMod = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(EazColors.Orange)
            .then(clickMod),
        contentAlignment = Alignment.Center
    ) {
        if (!avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = name.take(1).uppercase(),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineSmall
            )
        }
    }
}

@Composable
fun CreatorRatingRow(
    avg: Double,
    count: Int,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Star, contentDescription = null, tint = EazColors.Orange, modifier = Modifier.size(16.dp))
        Text(
            text = String.format("%.1f", avg),
            fontWeight = FontWeight.SemiBold,
            color = EazColors.TextPrimary,
            modifier = Modifier.padding(start = 4.dp)
        )
        Text(
            text = "($count)",
            color = EazColors.TextSecondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}
