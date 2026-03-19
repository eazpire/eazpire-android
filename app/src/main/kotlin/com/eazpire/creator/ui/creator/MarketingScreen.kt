package com.eazpire.creator.ui.creator

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.i18n.TranslationStore
import com.eazpire.creator.ui.account.AccountHeroImagesTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Marketing sub-tab: Content Creation | Content Publish */
private typealias MarketingSubTab = String

/** Content tab: Hero Images | Videos | Images */
private typealias MarketingContentTab = String

private const val SUBTAB_CONTENT_CREATION = "content-creation"
private const val SUBTAB_CONTENT_PUBLISH = "content-publish"
private const val CONTENT_HERO_IMAGES = "hero-images"
private const val CONTENT_VIDEOS = "videos"
private const val CONTENT_IMAGES = "images"

/**
 * Marketing Screen – 1:1 wie Web (creator-mobile-marketing.liquid, creator-marketing-screen.js)
 * Main tabs: Content Creation | Content Publish
 * Under-tabs: Hero Images | Videos | Images
 */
@Composable
fun MarketingScreen(
    tokenStore: SecureTokenStore,
    translationStore: TranslationStore,
    onHeaderTitleChange: (String) -> Unit,
    sessionKey: Int = 0,
    maxHeight: Dp = Dp.Infinity,
    modifier: Modifier = Modifier
) {
    val boundedHeight = if (maxHeight == Dp.Infinity) 4000.dp else maxHeight
    var currentSubTab by remember { mutableStateOf<MarketingSubTab>(SUBTAB_CONTENT_CREATION) }
    var currentContentTab by remember { mutableStateOf<MarketingContentTab>(CONTENT_HERO_IMAGES) }

    fun updateHeaderTitle() {
        val labels = mapOf(
            CONTENT_HERO_IMAGES to translationStore.t("creator.marketing.hero_images", "Hero Images"),
            CONTENT_VIDEOS to translationStore.t("creator.marketing.videos", "Videos"),
            CONTENT_IMAGES to translationStore.t("creator.marketing.images", "Images")
        )
        onHeaderTitleChange(
            if (currentSubTab == SUBTAB_CONTENT_PUBLISH) {
                translationStore.t("creator.marketing.content_publish", "Content Publish")
            } else {
                labels[currentContentTab] ?: translationStore.t("creator.marketing.content_creation", "Content Creation")
            }
        )
    }

    LaunchedEffect(sessionKey) {
        currentSubTab = SUBTAB_CONTENT_CREATION
        currentContentTab = CONTENT_HERO_IMAGES
        updateHeaderTitle()
    }

    LaunchedEffect(currentSubTab, currentContentTab) {
        updateHeaderTitle()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .heightIn(max = boundedHeight)
    ) {
        // Main tabs: Content Creation | Content Publish (wie .creator-marketing-tabs)
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xBF1C2434))
                    .padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                listOf(
                    SUBTAB_CONTENT_CREATION to translationStore.t("creator.marketing.content_creation", "Content Creation"),
                    SUBTAB_CONTENT_PUBLISH to translationStore.t("creator.marketing.content_publish", "Content Publish")
                ).forEach { (subtab, label) ->
                    val isActive = currentSubTab == subtab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { currentSubTab = subtab }
                    ) {
                        Text(
                            text = label,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isActive) EazColors.Orange else Color.White.copy(alpha = 0.6f),
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(vertical = 12.dp, horizontal = 20.dp)
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.12f))
            )
        }

        // Panel content
        when (currentSubTab) {
            SUBTAB_CONTENT_CREATION -> MarketingContentCreationPanel(
                currentContentTab = currentContentTab,
                onContentTabChange = { currentContentTab = it; updateHeaderTitle() },
                onSwitchToPublish = {
                    currentSubTab = SUBTAB_CONTENT_PUBLISH
                    currentContentTab = CONTENT_HERO_IMAGES
                    updateHeaderTitle()
                },
                tokenStore = tokenStore,
                translationStore = translationStore,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
            SUBTAB_CONTENT_PUBLISH -> MarketingContentPublishPanel(
                currentContentTab = currentContentTab,
                onContentTabChange = { currentContentTab = it; updateHeaderTitle() },
                tokenStore = tokenStore,
                translationStore = translationStore,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
    }
}

@Composable
private fun MarketingContentCreationPanel(
    currentContentTab: MarketingContentTab,
    onContentTabChange: (MarketingContentTab) -> Unit,
    onSwitchToPublish: () -> Unit,
    tokenStore: SecureTokenStore,
    translationStore: TranslationStore,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        MarketingUnderTabs(
            currentContentTab = currentContentTab,
            onContentTabChange = onContentTabChange,
            translationStore = translationStore
        )
        when (currentContentTab) {
            CONTENT_HERO_IMAGES -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                AccountHeroImagesTab(
                    tokenStore = tokenStore,
                    translationStore = translationStore,
                    darkMode = true,
                    onGenerated = onSwitchToPublish,
                    modifier = Modifier.padding(20.dp)
                )
            }
            CONTENT_VIDEOS, CONTENT_IMAGES -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                MarketingComingSoonBlock(
                    label = translationStore.t(
                        when (currentContentTab) {
                            CONTENT_VIDEOS -> "creator.marketing.videos"
                            else -> "creator.marketing.images"
                        },
                        currentContentTab.replaceFirstChar { it.uppercase() }
                    ),
                    translationStore = translationStore
                )
            }
        }
    }
}

@Composable
private fun MarketingContentPublishPanel(
    currentContentTab: MarketingContentTab,
    onContentTabChange: (MarketingContentTab) -> Unit,
    tokenStore: SecureTokenStore,
    translationStore: TranslationStore,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        MarketingUnderTabs(
            currentContentTab = currentContentTab,
            onContentTabChange = onContentTabChange,
            translationStore = translationStore
        )
        when (currentContentTab) {
            CONTENT_HERO_IMAGES -> MarketingHeroImagesGrid(
                tokenStore = tokenStore,
                translationStore = translationStore,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
            CONTENT_VIDEOS, CONTENT_IMAGES -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                MarketingComingSoonBlock(
                    label = translationStore.t(
                        when (currentContentTab) {
                            CONTENT_VIDEOS -> "creator.marketing.videos"
                            else -> "creator.marketing.images"
                        },
                        currentContentTab.replaceFirstChar { it.uppercase() }
                    ),
                    translationStore = translationStore
                )
            }
        }
    }
}

@Composable
private fun MarketingUnderTabs(
    currentContentTab: MarketingContentTab,
    onContentTabChange: (MarketingContentTab) -> Unit,
    translationStore: TranslationStore
) {
    val tabs = listOf(
        CONTENT_HERO_IMAGES to translationStore.t("creator.marketing.hero_images", "Hero Images"),
        CONTENT_VIDEOS to translationStore.t("creator.marketing.videos", "Videos"),
        CONTENT_IMAGES to translationStore.t("creator.marketing.images", "Images")
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 0.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(0.dp)
        ) {
        tabs.forEach { (content, label) ->
            val isActive = currentContentTab == content
            Box(
                modifier = Modifier
                    .clickable { onContentTabChange(content) }
            ) {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isActive) EazColors.Orange else Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 16.dp)
                )
            }
        }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.08f))
        )
    }
}

@Composable
private fun MarketingComingSoonBlock(
    label: String,
    translationStore: TranslationStore
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x991C1F2B))
            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = translationStore.t("creator.common.coming_soon", "Coming soon"),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = EazColors.Orange,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(EazColors.Orange.copy(alpha = 0.15f))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            )
            Text(
                text = label,
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}

data class HeroImageItem(
    val id: String,
    val r2Key: String?,
    val imageUrl: String?,
    val thumbnailUrl: String?,
    val previewUrl: String?,
    val originalUrl: String?,
    val title: String,
    val productKey: String?,
    val region: String
)

private fun firstNonBlank(vararg values: String?): String? =
    values.firstOrNull { !it.isNullOrBlank() }?.trim()

private fun normalizeHeroImageUrl(raw: String?): String? {
    val value = raw?.trim().orEmpty()
    if (value.isBlank()) return null
    return when {
        value.startsWith("https://", ignoreCase = true) -> value
        value.startsWith("http://", ignoreCase = true) -> "https://${value.removePrefix("http://")}"
        value.startsWith("//") -> "https:$value"
        value.startsWith("/") -> "https://www.eazpire.com$value"
        value.startsWith("apps/") -> "https://www.eazpire.com/$value"
        else -> value
    }
}

private fun r2PublicUrl(baseUrl: String, r2Key: String?): String? {
    val key = r2Key?.trim().orEmpty()
    if (key.isBlank()) return null
    val encodedKey = key.split('/').joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
    return "${baseUrl.trimEnd('/')}/file/$encodedKey"
}

@Composable
private fun MarketingHeroImagesGrid(
    tokenStore: SecureTokenStore,
    translationStore: TranslationStore,
    modifier: Modifier = Modifier
) {
    data class RegionTab(val code: String, val label: String)

    val regionTabs = listOf(
        RegionTab("ALL", "Alle"),
        RegionTab("EU", "Europa"),
        RegionTab("US", "USA"),
        RegionTab("GB", "UK"),
        RegionTab("CA", "Kanada"),
        RegionTab("AU", "Australien"),
        RegionTab("CN", "China"),
        RegionTab("OTHER", "Sonstige")
    )

    val context = LocalContext.current
    val jwt = remember(tokenStore) { tokenStore.getJwt().orEmpty() }
    val api = remember { CreatorApi(jwt = tokenStore.getJwt()) }
    val publicBaseUrl = remember(api) {
        try {
            val field = CreatorApi::class.java.getDeclaredField("baseUrl")
            field.isAccessible = true
            (field.get(api) as? String).orEmpty().ifBlank { "https://creator-engine.eazpire.workers.dev" }
        } catch (_: Exception) {
            "https://creator-engine.eazpire.workers.dev"
        }
    }
    val ownerId = remember(tokenStore) { tokenStore.getOwnerId() ?: "" }
    var items by remember { mutableStateOf<List<HeroImageItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var currentRegionFilter by remember { mutableStateOf("ALL") }

    LaunchedEffect(ownerId) {
        if (ownerId.isBlank()) {
            loading = false
            return@LaunchedEffect
        }
        loading = true
        try {
            val resp = withContext(Dispatchers.IO) { api.heroList(ownerId, limit = 100, status = null) }
            if (resp.optBoolean("ok", false)) {
                val arr = resp.optJSONArray("items") ?: org.json.JSONArray()
                items = (0 until arr.length()).mapNotNull { i ->
                    val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                    HeroImageItem(
                        id = obj.optString("id", ""),
                        r2Key = obj.optString("r2_key", "").takeIf { it.isNotBlank() },
                        imageUrl = normalizeHeroImageUrl(firstNonBlank(
                            obj.optString("image_url", ""),
                            obj.optString("preview_url", ""),
                            obj.optString("original_url", ""),
                            obj.optString("thumbnail_url", ""),
                            obj.optString("public_url", ""),
                            obj.optString("url", "")
                        )),
                        thumbnailUrl = normalizeHeroImageUrl(firstNonBlank(
                            obj.optString("thumbnail_url", ""),
                            obj.optString("preview_url", ""),
                            obj.optString("image_url", ""),
                            obj.optString("public_url", "")
                        )),
                        previewUrl = normalizeHeroImageUrl(obj.optString("preview_url", "")),
                        originalUrl = normalizeHeroImageUrl(firstNonBlank(
                            obj.optString("original_url", ""),
                            obj.optString("public_url", "")
                        )),
                        title = obj.optString("title", obj.optString("user_prompt", "Hero #${obj.optString("id", "")}")),
                        productKey = obj.optString("product_key", "").takeIf { it.isNotBlank() },
                        region = obj.optString("region", "OTHER").uppercase()
                    )
                }
            }
        } catch (_: Exception) {}
        loading = false
    }

    val regionCounts = remember(items) {
        val counts = mutableMapOf<String, Int>()
        counts["ALL"] = items.size
        regionTabs.forEach { tab ->
            if (tab.code != "ALL") counts[tab.code] = 0
        }
        items.forEach { item ->
            val code = item.region.uppercase()
            counts[code] = (counts[code] ?: 0) + 1
        }
        counts
    }

    val filteredItems = remember(items, currentRegionFilter) {
        if (currentRegionFilter == "ALL") items
        else items.filter { it.region.uppercase() == currentRegionFilter }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                regionTabs.forEach { tab ->
                    val active = currentRegionFilter == tab.code
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .border(
                                1.dp,
                                if (active) EazColors.Orange else EazColors.Orange.copy(alpha = 0.4f),
                                RoundedCornerShape(999.dp)
                            )
                            .background(
                                if (active) EazColors.Orange.copy(alpha = 0.25f)
                                else EazColors.Orange.copy(alpha = 0.08f),
                                RoundedCornerShape(999.dp)
                            )
                            .clickable { currentRegionFilter = tab.code }
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (active) Color.White else Color.White.copy(alpha = 0.9f)
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color(0x52020617))
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = (regionCounts[tab.code] ?: 0).toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.95f)
                            )
                        }
                    }
                }
            }
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter
            ) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp)
                ) {
                    when {
                        loading -> Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = translationStore.t("creator.mobile.loading", "Loading…"),
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                        filteredItems.isEmpty() -> Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            val emptyText = when {
                                items.isEmpty() -> translationStore.t("creator.marketing.no_hero_images", "No hero images yet.")
                                currentRegionFilter != "ALL" -> "Keine Hero Images in dieser Region."
                                else -> translationStore.t("creator.marketing.no_hero_images", "No hero images yet.")
                            }
                            Text(
                                text = emptyText,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                        else -> LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(filteredItems) { item ->
                                val urlCandidates = listOfNotNull(
                                    r2PublicUrl(publicBaseUrl, item.r2Key),
                                    item.previewUrl,
                                    item.thumbnailUrl,
                                    item.imageUrl,
                                    item.originalUrl
                                ).distinct()
                                Box(
                                    modifier = Modifier
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color.White.copy(alpha = 0.08f))
                                        .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(10.dp))
                                ) {
                                    if (urlCandidates.isNotEmpty()) {
                                        var urlIndex by remember(item.id) { mutableIntStateOf(0) }
                                        val currentUrl = urlCandidates.getOrNull(urlIndex)
                                        if (currentUrl != null) {
                                            val model = ImageRequest.Builder(context)
                                                .data(currentUrl)
                                                .apply {
                                                    if (jwt.isNotBlank()) {
                                                        addHeader("Authorization", "Bearer $jwt")
                                                    }
                                                }
                                                .build()
                                            SubcomposeAsyncImage(
                                                model = model,
                                                contentDescription = item.title,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop,
                                                loading = {},
                                                error = {
                                                    if (urlIndex < urlCandidates.lastIndex) {
                                                        urlIndex += 1
                                                    } else {
                                                        Text(
                                                            text = "—",
                                                            color = Color.White.copy(alpha = 0.5f),
                                                            modifier = Modifier
                                                                .fillMaxSize()
                                                                .padding(16.dp),
                                                            style = MaterialTheme.typography.bodyLarge
                                                        )
                                                    }
                                                }
                                            )
                                        }
                                    }
                                    else {
                                        Text(
                                            text = "—",
                                            color = Color.White.copy(alpha = 0.5f),
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(16.dp),
                                            style = MaterialTheme.typography.bodyLarge
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
