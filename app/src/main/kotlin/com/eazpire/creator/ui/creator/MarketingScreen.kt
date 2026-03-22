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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.chat.EazySidebarTab
import com.eazpire.creator.i18n.TranslationStore
import android.content.Intent
import android.net.Uri as AndroidUri
import com.eazpire.creator.ui.account.AccountHeroImagesTab
import com.eazpire.creator.ui.account.AccountVideoTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Marketing sub-tab: Content Creation | Content Publish */
private typealias MarketingSubTab = String

/** Content tab: Hero Images | Videos | Images */
private typealias MarketingContentTab = String

private const val SUBTAB_CONTENT_CREATION = "content-creation"
private const val SUBTAB_CONTENT_PUBLISH = "content-publish"
private const val SUBTAB_PROMOTIONS = "promotions"
private const val CONTENT_HERO_IMAGES = "hero-images"
private const val CONTENT_VIDEOS = "videos"
private const val CONTENT_IMAGES = "images"

/**
 * Marketing Screen – 1:1 wie Web (creator-mobile-marketing.liquid, creator-marketing-screen.js)
 * Main tabs: Content Creation | Content Publish | Promotions
 * Under-tabs: Hero Images | Videos | Images
 */
@Composable
fun MarketingScreen(
    tokenStore: SecureTokenStore,
    translationStore: TranslationStore,
    onHeaderTitleChange: (String) -> Unit,
    sessionKey: Int = 0,
    maxHeight: Dp = Dp.Infinity,
    onEazyChatOpen: (EazySidebarTab?) -> Unit = {},
    onHeroJobStarted: (jobId: String, summary: String) -> Unit = { _, _ -> },
    onVideoJobStarted: (jobId: String, summary: String) -> Unit = { _, _ -> },
    onHeroEazyReadyChange: (Boolean) -> Unit = {},
    onVideoEazyReadyChange: (Boolean) -> Unit = {},
    onVideoGeneratingChange: (Boolean) -> Unit = {},
    heroHeaderStartNonce: Int = 0,
    videoHeaderStartNonce: Int = 0,
    onHeroGeneratingChange: (Boolean) -> Unit = {},
    showHeroDockedComposeBar: Boolean = false,
    heroDockedComposeLoading: Boolean = false,
    onHeroDockedComposeStart: () -> Unit = {},
    showVideoDockedComposeBar: Boolean = false,
    videoDockedComposeLoading: Boolean = false,
    onVideoDockedComposeStart: () -> Unit = {},
    onMarketingTabVisibility: (heroContentTabVisible: Boolean, videoContentTabVisible: Boolean) -> Unit = { _, _ -> },
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
        when {
            currentSubTab == SUBTAB_PROMOTIONS ->
                onHeaderTitleChange(translationStore.t("creator.promotions.tab", "Promotions"))
            currentSubTab == SUBTAB_CONTENT_PUBLISH ->
                onHeaderTitleChange(translationStore.t("creator.marketing.content_publish", "Content Publish"))
            else ->
                onHeaderTitleChange(
                    labels[currentContentTab] ?: translationStore.t("creator.marketing.content_creation", "Content Creation")
                )
        }
    }

    LaunchedEffect(sessionKey) {
        currentSubTab = SUBTAB_CONTENT_CREATION
        currentContentTab = CONTENT_HERO_IMAGES
        updateHeaderTitle()
    }

    LaunchedEffect(currentSubTab, currentContentTab) {
        updateHeaderTitle()
        val heroTabVisible =
            currentSubTab == SUBTAB_CONTENT_CREATION && currentContentTab == CONTENT_HERO_IMAGES
        val videoTabVisible =
            currentSubTab == SUBTAB_CONTENT_CREATION && currentContentTab == CONTENT_VIDEOS
        if (currentSubTab == SUBTAB_PROMOTIONS) {
            onHeroEazyReadyChange(false)
            onVideoEazyReadyChange(false)
        } else {
            if (!heroTabVisible) onHeroEazyReadyChange(false)
            if (!videoTabVisible) onVideoEazyReadyChange(false)
        }
        onMarketingTabVisibility(heroTabVisible, videoTabVisible)
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
                    SUBTAB_CONTENT_PUBLISH to translationStore.t("creator.marketing.content_publish", "Content Publish"),
                    SUBTAB_PROMOTIONS to translationStore.t("creator.promotions.tab", "Promotions")
                ).forEach { (subtab, label) ->
                    val isActive = currentSubTab == subtab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { currentSubTab = subtab }
                    ) {
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isActive) EazColors.Orange else Color.White.copy(alpha = 0.6f),
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(vertical = 12.dp, horizontal = 6.dp)
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
                onEazyChatOpen = onEazyChatOpen,
                onHeroJobStarted = onHeroJobStarted,
                onVideoJobStarted = onVideoJobStarted,
                onHeroEazyReadyChange = onHeroEazyReadyChange,
                onVideoEazyReadyChange = onVideoEazyReadyChange,
                onVideoGeneratingChange = onVideoGeneratingChange,
                heroHeaderStartNonce = heroHeaderStartNonce,
                videoHeaderStartNonce = videoHeaderStartNonce,
                onHeroGeneratingChange = onHeroGeneratingChange,
                showHeroDockedComposeBar = showHeroDockedComposeBar,
                heroDockedComposeLoading = heroDockedComposeLoading,
                onHeroDockedComposeStart = onHeroDockedComposeStart,
                showVideoDockedComposeBar = showVideoDockedComposeBar,
                videoDockedComposeLoading = videoDockedComposeLoading,
                onVideoDockedComposeStart = onVideoDockedComposeStart,
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
            SUBTAB_PROMOTIONS -> MarketingPromotionsPanel(
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
    onEazyChatOpen: (EazySidebarTab?) -> Unit,
    onHeroJobStarted: (jobId: String, summary: String) -> Unit,
    onVideoJobStarted: (jobId: String, summary: String) -> Unit,
    onHeroEazyReadyChange: (Boolean) -> Unit = {},
    onVideoEazyReadyChange: (Boolean) -> Unit = {},
    onVideoGeneratingChange: (Boolean) -> Unit = {},
    heroHeaderStartNonce: Int = 0,
    videoHeaderStartNonce: Int = 0,
    onHeroGeneratingChange: (Boolean) -> Unit = {},
    showHeroDockedComposeBar: Boolean = false,
    heroDockedComposeLoading: Boolean = false,
    onHeroDockedComposeStart: () -> Unit = {},
    showVideoDockedComposeBar: Boolean = false,
    videoDockedComposeLoading: Boolean = false,
    onVideoDockedComposeStart: () -> Unit = {},
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
                    onHeroJobStarted = onHeroJobStarted,
                    onOpenEazyChat = { tab -> onEazyChatOpen(tab) },
                    onHeroEazyReadyChange = onHeroEazyReadyChange,
                    headerStartNonce = heroHeaderStartNonce,
                    onHeroGeneratingChange = onHeroGeneratingChange,
                    modifier = Modifier
                        .padding(20.dp)
                        .padding(bottom = if (showHeroDockedComposeBar) 88.dp else 20.dp)
                )
                if (showHeroDockedComposeBar) {
                    CreatorDockedComposeFloatingBar(
                        visible = true,
                        loading = heroDockedComposeLoading,
                        onStart = onHeroDockedComposeStart,
                        translationStore = translationStore,
                        modifier = Modifier.align(Alignment.BottomEnd)
                    )
                }
            }
            CONTENT_VIDEOS -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                AccountVideoTab(
                    tokenStore = tokenStore,
                    translationStore = translationStore,
                    darkMode = true,
                    onGenerated = onSwitchToPublish,
                    onVideoJobStarted = onVideoJobStarted,
                    onOpenEazyChat = { tab -> onEazyChatOpen(tab) },
                    onVideoEazyReadyChange = onVideoEazyReadyChange,
                    onVideoGeneratingChange = onVideoGeneratingChange,
                    headerStartNonce = videoHeaderStartNonce,
                    modifier = Modifier
                        .padding(20.dp)
                        .padding(bottom = if (showVideoDockedComposeBar) 88.dp else 20.dp)
                )
                if (showVideoDockedComposeBar) {
                    CreatorDockedComposeFloatingBar(
                        visible = true,
                        loading = videoDockedComposeLoading,
                        onStart = onVideoDockedComposeStart,
                        translationStore = translationStore,
                        modifier = Modifier.align(Alignment.BottomEnd)
                    )
                }
            }
            CONTENT_IMAGES -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                MarketingComingSoonBlock(
                    label = translationStore.t("creator.marketing.images", "Images"),
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
            CONTENT_VIDEOS -> MarketingVideosGrid(
                tokenStore = tokenStore,
                translationStore = translationStore,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
            CONTENT_IMAGES -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                MarketingComingSoonBlock(
                    label = translationStore.t("creator.marketing.images", "Images"),
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

private data class VideoGridItem(
    val id: String,
    val videoUrl: String?,
    val thumbnailUrl: String?,
    val title: String,
    val region: String
)

@Composable
private fun MarketingVideosGrid(
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
    val api = remember(jwt) { CreatorApi(jwt = jwt) }
    val ownerId = remember(tokenStore) { tokenStore.getOwnerId() ?: "" }
    var items by remember { mutableStateOf<List<VideoGridItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var currentRegionFilter by remember { mutableStateOf("ALL") }

    LaunchedEffect(ownerId) {
        if (ownerId.isBlank()) {
            loading = false
            return@LaunchedEffect
        }
        loading = true
        try {
            val resp = withContext(Dispatchers.IO) { api.creatorVideosList(ownerId, limit = 100) }
            if (resp.optBoolean("ok", false)) {
                val arr = resp.optJSONArray("items") ?: org.json.JSONArray()
                items = (0 until arr.length()).mapNotNull { i ->
                    val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                    val vUrl = normalizeHeroImageUrl(
                        firstNonBlank(
                            obj.optString("video_url", ""),
                            obj.optString("thumbnail_url", "")
                        )
                    )
                    val thumb = normalizeHeroImageUrl(
                        firstNonBlank(
                            obj.optString("thumbnail_url", ""),
                            obj.optString("video_url", "")
                        )
                    )
                    VideoGridItem(
                        id = obj.optString("id", ""),
                        videoUrl = vUrl,
                        thumbnailUrl = thumb,
                        title = obj.optString("user_prompt", "").ifBlank { "Video #${obj.optString("id", "")}" },
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

    Column(modifier = modifier.fillMaxSize()) {
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
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 16.dp)
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
                        items.isEmpty() -> translationStore.t("creator.marketing.no_videos_yet", "No videos yet.")
                        currentRegionFilter != "ALL" -> translationStore.t(
                            "creator.marketing.no_videos_region",
                            "No videos in this region."
                        )
                        else -> translationStore.t("creator.marketing.no_videos_yet", "No videos yet.")
                    }
                    Text(text = emptyText, color = Color.White.copy(alpha = 0.6f))
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredItems) { item ->
                        val thumb = item.thumbnailUrl ?: item.videoUrl
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(10.dp))
                                .clickable {
                                    val url = item.videoUrl ?: return@clickable
                                    try {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, AndroidUri.parse(url))
                                        )
                                    } catch (_: Exception) {}
                                }
                        ) {
                            if (!thumb.isNullOrBlank()) {
                                val model = ImageRequest.Builder(context)
                                    .data(thumb)
                                    .apply {
                                        if (jwt.isNotBlank()) {
                                            addHeader("Authorization", "Bearer $jwt")
                                        }
                                    }
                                    .build()
                                AsyncImage(
                                    model = model,
                                    contentDescription = item.title,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Text(
                                    text = "▶",
                                    color = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .fillMaxWidth()
                                    .background(Color.Black.copy(alpha = 0.55f))
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    maxLines = 2
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class PromotionRow(
    val id: String,
    val name: String,
    val discountType: String,
    val discountValue: Double,
    val durationDays: Int,
    val endsAt: Long?,
    val productIds: List<String>
)

@Composable
private fun MarketingPromotionsPanel(
    tokenStore: SecureTokenStore,
    translationStore: TranslationStore,
    modifier: Modifier = Modifier
) {
    val jwt = remember(tokenStore) { tokenStore.getJwt().orEmpty() }
    val api = remember(jwt) { CreatorApi(jwt = jwt) }
    val ownerId = remember(tokenStore) { tokenStore.getOwnerId().orEmpty() }
    var items by remember { mutableStateOf<List<PromotionRow>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var showDialog by remember { mutableStateOf(false) }
    var editId by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var discountType by remember { mutableStateOf("percent") }
    var discountValue by remember { mutableStateOf("15") }
    var durationDays by remember { mutableStateOf("7") }
    var productIdsText by remember { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    fun parsePromotionList(resp: JSONObject): List<PromotionRow> {
        val arr = resp.optJSONArray("promotions") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val pidArr = o.optJSONArray("product_ids")
            val pids = mutableListOf<String>()
            if (pidArr != null) {
                for (j in 0 until pidArr.length()) {
                    pids.add(pidArr.optString(j, ""))
                }
            }
            PromotionRow(
                id = o.optString("id", ""),
                name = o.optString("name", ""),
                discountType = o.optString("discount_type", "percent"),
                discountValue = o.optDouble("discount_value", 0.0),
                durationDays = o.optInt("duration_days", 7),
                endsAt = if (o.has("ends_at")) o.optLong("ends_at") else null,
                productIds = pids
            )
        }
    }

    LaunchedEffect(ownerId, refresh) {
        if (ownerId.isBlank()) {
            loading = false
            return@LaunchedEffect
        }
        loading = true
        try {
            val resp = withContext(Dispatchers.IO) { api.listPromotions(ownerId) }
            if (resp.optBoolean("ok", false)) {
                items = parsePromotionList(resp)
            } else {
                items = emptyList()
            }
        } catch (_: Exception) {
            items = emptyList()
        }
        loading = false
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedContainerColor = Color(0x991C1F2B),
        unfocusedContainerColor = Color(0x991C1F2B),
        focusedLabelColor = EazColors.Orange,
        unfocusedLabelColor = Color.White.copy(alpha = 0.55f),
        cursorColor = EazColors.Orange,
        focusedBorderColor = EazColors.Orange,
        unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        if (loading) {
            Text(
                text = translationStore.t("creator.common.loading", "Loading..."),
                color = Color.White.copy(alpha = 0.7f)
            )
        } else if (items.isEmpty()) {
            Text(
                text = translationStore.t("creator.promotions.empty", "No promotions yet."),
                color = Color.White.copy(alpha = 0.6f)
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, EazColors.Orange.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                        .background(EazColors.Orange.copy(alpha = 0.08f))
                        .clickable {
                            editId = null
                            name = ""
                            description = ""
                            discountType = "percent"
                            discountValue = "15"
                            durationDays = "7"
                            productIdsText = ""
                            showDialog = true
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "+",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = EazColors.Orange
                        )
                        Text(
                            text = translationStore.t("creator.promotions.new_promotion", "New promotion"),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }
            }
            items(items) { p ->
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                        .background(Color(0x991C1F2B))
                        .clickable {
                            editId = p.id
                            name = p.name
                            description = ""
                            discountType = if (p.discountType == "fixed_usd" || p.discountType == "fixed") "fixed_usd" else "percent"
                            discountValue = p.discountValue.toString()
                            durationDays = p.durationDays.toString()
                            productIdsText = p.productIds.joinToString(",")
                            showDialog = true
                        }
                        .padding(12.dp),
                    contentAlignment = Alignment.TopStart
                ) {
                    Column {
                        Text(
                            text = p.name,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 2
                        )
                        val label = if (p.discountType == "fixed_usd" || p.discountType == "fixed") {
                            "-" + "$" + p.discountValue
                        } else {
                            "-${p.discountValue.toInt()}%"
                        }
                        Text(
                            text = label,
                            fontSize = 13.sp,
                            color = EazColors.Orange,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val body = JSONObject()
                        body.put("owner_id", ownerId)
                        body.put("name", name.trim())
                        body.put("description", description.trim())
                        body.put("discount_type", discountType)
                        body.put("discount_value", discountValue.toDoubleOrNull() ?: 0.0)
                        body.put("duration_days", durationDays.toIntOrNull() ?: 7)
                        val arr = JSONArray()
                        productIdsText.split(',').map { it.trim() }.filter { it.isNotBlank() }.forEach { arr.put(it) }
                        body.put("product_ids", arr)
                        editId?.let { body.put("id", it) }
                        scope.launch {
                            try {
                                val resp = withContext(Dispatchers.IO) { api.savePromotion(body) }
                                if (resp.optBoolean("ok", false)) {
                                    showDialog = false
                                    refresh += 1
                                }
                            } catch (_: Exception) {
                            }
                        }
                    }
                ) {
                    Text(translationStore.t("creator.promotions.save", "Save"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(translationStore.t("creator.promotions.cancel", "Cancel"))
                }
            },
            title = {
                Text(
                    if (editId == null) translationStore.t("creator.promotions.modal_title_new", "New")
                    else translationStore.t("creator.promotions.modal_title_edit", "Edit")
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(translationStore.t("creator.promotions.name", "Name")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = fieldColors
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text(translationStore.t("creator.promotions.description", "Description")) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = fieldColors
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = discountType,
                        onValueChange = { discountType = it },
                        label = { Text(translationStore.t("creator.promotions.discount_type", "Type")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = fieldColors
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = discountValue,
                        onValueChange = { discountValue = it },
                        label = { Text(translationStore.t("creator.promotions.discount_value", "Value")) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = fieldColors
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = durationDays,
                        onValueChange = { durationDays = it },
                        label = { Text(translationStore.t("creator.promotions.duration_days", "Days")) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = fieldColors
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = productIdsText,
                        onValueChange = { productIdsText = it },
                        label = { Text(translationStore.t("creator.promotions.products_selected", "Products")) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = fieldColors
                    )
                    Text(
                        text = translationStore.t("creator.promotions.duration_hint", ""),
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.45f),
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        )
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
    val api = remember(jwt) { CreatorApi(jwt = jwt) }
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
    var gridRefresh by remember { mutableIntStateOf(0) }
    var previewHeroId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(ownerId, gridRefresh) {
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
        HeroImagePreviewModal(
            visible = previewHeroId != null,
            heroId = previewHeroId,
            ownerId = ownerId,
            jwt = jwt,
            translationStore = translationStore,
            api = api,
            onDismiss = { previewHeroId = null },
            onSaved = { gridRefresh++ }
        )
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
                                        .clickable { previewHeroId = item.id }
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
