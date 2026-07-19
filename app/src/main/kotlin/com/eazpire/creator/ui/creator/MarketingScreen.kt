package com.eazpire.creator.ui.creator

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.eazpire.creator.EazColors
import com.eazpire.creator.R
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.chat.EazySidebarTab
import com.eazpire.creator.i18n.TranslationStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Marketing Screen – 1:1 Web Mobile skill-tree (IDEA-039 / IDEA-043).
 * Parents: Content Creation | Content Publish | Promotion
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
    initialOpenHeroImages: Boolean = false,
    onInitialOpenHeroImagesConsumed: () -> Unit = {},
    smmOAuthRefreshNonce: Int = 0,
    modifier: Modifier = Modifier,
) {
    val boundedHeight = if (maxHeight == Dp.Infinity) 4000.dp else maxHeight
    var expandedParent by remember { mutableStateOf("") }
    var selectedChild by remember { mutableStateOf("") }
    var showHeroCreateModal by remember { mutableStateOf(false) }
    var showVideoGenerator by remember { mutableStateOf(false) }
    var showVideoStudio by remember { mutableStateOf(false) }
    var showSocialMediaManager by remember { mutableStateOf(false) }
    val geometry = remember { MarketingTreeGeometry() }
    var connectorTick by remember { mutableIntStateOf(0) }

    val parents = rememberMarketingParentCards(translationStore)
    val creationChildren = rememberMarketingCreationChildren(translationStore)
    val publishChildren = rememberMarketingPublishChildren(translationStore)
    val videoFunctions = rememberMarketingVideoFunctions(translationStore)

    fun updateHeaderTitle() {
        val title = when {
            showSocialMediaManager ->
                translationStore.t("creator.social_media_manager.title", "Social Media Manager")
            showVideoStudio ->
                translationStore.t("creator.video_studio.title", "Video Studio")
            showVideoGenerator ->
                translationStore.t("creator.video_generator.title", "Video Generator")
            showHeroCreateModal ->
                translationStore.t("creator.marketing.hero_images", "Hero Images")
            expandedParent == MKT_PARENT_PROMOTIONS ->
                translationStore.t("creator.marketing.promotion", "Promotion")
            expandedParent == MKT_PARENT_PUBLISH && selectedChild == MKT_CHILD_HERO ->
                translationStore.t("creator.marketing.hero_images", "Hero Images")
            expandedParent == MKT_PARENT_CREATION && selectedChild == MKT_CHILD_VIDEO ->
                translationStore.t("creator.marketing.video", "Video")
            expandedParent == MKT_PARENT_CREATION && selectedChild == MKT_CHILD_IMAGES ->
                translationStore.t("creator.marketing.images", "Images")
            expandedParent == MKT_PARENT_CREATION ->
                translationStore.t("creator.marketing.content_creation", "Content Creation")
            expandedParent == MKT_PARENT_PUBLISH ->
                translationStore.t("creator.marketing.content_publish", "Content Publish")
            else ->
                translationStore.t("creator.mobile.marketing", "Marketing")
        }
        onHeaderTitleChange(title)
    }

    LaunchedEffect(initialOpenHeroImages) {
        if (initialOpenHeroImages) {
            expandedParent = MKT_PARENT_CREATION
            selectedChild = MKT_CHILD_HERO
            showHeroCreateModal = true
            onInitialOpenHeroImagesConsumed()
        }
    }

    LaunchedEffect(sessionKey) {
        expandedParent = ""
        selectedChild = ""
        showHeroCreateModal = false
        showVideoGenerator = false
        showVideoStudio = false
        showSocialMediaManager = false
        updateHeaderTitle()
    }

    LaunchedEffect(
        expandedParent,
        selectedChild,
        showHeroCreateModal,
        showVideoGenerator,
        showVideoStudio,
        showSocialMediaManager,
    ) {
        updateHeaderTitle()
        val heroVisible = showHeroCreateModal
        val videoVisible = showVideoGenerator || showVideoStudio
        if (!heroVisible) onHeroEazyReadyChange(false)
        if (!videoVisible) onVideoEazyReadyChange(false)
        onMarketingTabVisibility(heroVisible, videoVisible)
        connectorTick++
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .heightIn(max = boundedHeight),
    ) {
        Image(
            painter = painterResource(R.drawable.galaxy_nebula_bg),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.55f,
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xCC070B14)),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 16.dp)
                .onGloballyPositioned { geometry.rootCoords = it },
        ) {
            // Parent cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                parents.forEach { spec ->
                    MarketingSkillTreeCard(
                        spec = spec,
                        isActive = expandedParent == spec.id,
                        dimmed = expandedParent.isNotBlank() && expandedParent != spec.id,
                        onClick = {
                            if (expandedParent == spec.id) {
                                expandedParent = ""
                                selectedChild = ""
                            } else {
                                expandedParent = spec.id
                                selectedChild = ""
                            }
                        },
                        onPositioned = {
                            geometry.updateCard("parent-${spec.id}", it)
                            connectorTick++
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // Content Creation branch
            if (expandedParent == MKT_PARENT_CREATION) {
                MarketingForkConnector(
                    parentCenterXPx = geometry.centerX("parent-$MKT_PARENT_CREATION"),
                    childCenterXsPx = creationChildren.map { geometry.centerX("child-$MKT_PARENT_CREATION-${it.id}") },
                    animate = true,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    creationChildren.forEach { spec ->
                        MarketingSkillTreeCard(
                            spec = spec,
                            isActive = selectedChild == spec.id,
                            dimmed = selectedChild.isNotBlank() && selectedChild != spec.id,
                            onClick = {
                                selectedChild = if (selectedChild == spec.id) "" else spec.id
                                when (spec.id) {
                                    MKT_CHILD_HERO -> showHeroCreateModal = true
                                    MKT_CHILD_VIDEO -> { /* expand functions below */ }
                                    MKT_CHILD_IMAGES -> { /* coming soon panel */ }
                                }
                            },
                            onPositioned = {
                                geometry.updateCard("child-$MKT_PARENT_CREATION-${spec.id}", it)
                                connectorTick++
                            },
                            modifier = Modifier.weight(1f),
                            minHeightDp = 88,
                        )
                    }
                }

                if (selectedChild == MKT_CHILD_VIDEO) {
                    MarketingForkConnector(
                        parentCenterXPx = geometry.centerX("child-$MKT_PARENT_CREATION-$MKT_CHILD_VIDEO"),
                        childCenterXsPx = videoFunctions.map { geometry.centerX("fn-${it.id}") },
                        animate = true,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        videoFunctions.forEach { spec ->
                            MarketingSkillTreeCard(
                                spec = spec,
                                isActive = false,
                                dimmed = false,
                                onClick = {
                                    when (spec.id) {
                                        MKT_FN_STUDIO -> showVideoStudio = true
                                        MKT_FN_GENERATOR -> showVideoGenerator = true
                                    }
                                },
                                onPositioned = {
                                    geometry.updateCard("fn-${spec.id}", it)
                                    connectorTick++
                                },
                                modifier = Modifier.weight(1f),
                                minHeightDp = 110,
                            )
                        }
                    }
                }

                if (selectedChild == MKT_CHILD_IMAGES) {
                    Spacer(modifier = Modifier.height(16.dp))
                    MarketingComingSoonBlock(
                        label = translationStore.t("creator.marketing.images", "Images"),
                        translationStore = translationStore,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                    )
                }
            }

            // Content Publish branch
            if (expandedParent == MKT_PARENT_PUBLISH) {
                MarketingForkConnector(
                    parentCenterXPx = geometry.centerX("parent-$MKT_PARENT_PUBLISH"),
                    childCenterXsPx = publishChildren.map { geometry.centerX("child-$MKT_PARENT_PUBLISH-${it.id}") },
                    animate = true,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    publishChildren.forEach { spec ->
                        MarketingSkillTreeCard(
                            spec = spec,
                            isActive = selectedChild == spec.id || (spec.id == MKT_FN_SMM && showSocialMediaManager),
                            dimmed = selectedChild.isNotBlank() && selectedChild != spec.id && spec.id != MKT_FN_SMM,
                            onClick = {
                                if (spec.id == MKT_FN_SMM) {
                                    showSocialMediaManager = true
                                } else {
                                    selectedChild = if (selectedChild == spec.id) "" else spec.id
                                }
                            },
                            onPositioned = {
                                geometry.updateCard("child-$MKT_PARENT_PUBLISH-${spec.id}", it)
                                connectorTick++
                            },
                            modifier = Modifier.weight(1f),
                            minHeightDp = if (spec.isFunction) 110 else 88,
                        )
                    }
                }

                if (selectedChild == MKT_CHILD_HERO) {
                    Spacer(modifier = Modifier.height(16.dp))
                    MarketingHeroImagesGrid(
                        tokenStore = tokenStore,
                        translationStore = translationStore,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 320.dp, max = 560.dp),
                    )
                }
            }

            // Promotion panel
            if (expandedParent == MKT_PARENT_PROMOTIONS) {
                Spacer(modifier = Modifier.height(16.dp))
                MarketingPromotionsPanel(
                    tokenStore = tokenStore,
                    translationStore = translationStore,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 400.dp),
                )
            }
        }

        // Fullscreen tools
        HeroImagesCreateModal(
            visible = showHeroCreateModal,
            onDismiss = { showHeroCreateModal = false },
            tokenStore = tokenStore,
            translationStore = translationStore,
            onHeroJobStarted = onHeroJobStarted,
            onOpenEazyChat = { tab -> onEazyChatOpen(tab) },
            onHeroEazyReadyChange = onHeroEazyReadyChange,
            headerStartNonce = heroHeaderStartNonce,
            onHeroGeneratingChange = onHeroGeneratingChange,
            showDockedComposeBar = showHeroDockedComposeBar,
            dockedComposeLoading = heroDockedComposeLoading,
            onDockedComposeStart = onHeroDockedComposeStart,
        )

        VideoGeneratorScreen(
            visible = showVideoGenerator,
            onDismiss = {
                showVideoGenerator = false
                onVideoEazyReadyChange(false)
                onVideoGeneratingChange(false)
            },
            tokenStore = tokenStore,
            translationStore = translationStore,
            onVideoJobStarted = onVideoJobStarted,
        )

        VideoStudioScreen(
            visible = showVideoStudio,
            onDismiss = { showVideoStudio = false },
            tokenStore = tokenStore,
            translationStore = translationStore,
        )

        SocialMediaManagerScreen(
            visible = showSocialMediaManager,
            onDismiss = { showSocialMediaManager = false },
            tokenStore = tokenStore,
            translationStore = translationStore,
            oauthRefreshNonce = smmOAuthRefreshNonce,
        )
    }
}

@Composable
private fun MarketingComingSoonBlock(
    label: String,
    translationStore: TranslationStore,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x991C1F2B))
            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center,
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
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
            Text(
                text = label,
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 12.dp),
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
    val region: String,
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
    val encodedKey = key.split('/').joinToString("/") {
        java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20")
    }
    return "${baseUrl.trimEnd('/')}/file/$encodedKey"
}

@Composable
internal fun MarketingHeroImagesGrid(
    tokenStore: SecureTokenStore,
    translationStore: TranslationStore,
    modifier: Modifier = Modifier,
) {
    data class RegionTab(val code: String, val label: String)

    val regionTabs = listOf(
        RegionTab("ALL", "All"),
        RegionTab("EU", "EU"),
        RegionTab("US", "US"),
        RegionTab("GB", "UK"),
        RegionTab("CA", "CA"),
        RegionTab("AU", "AU"),
        RegionTab("CN", "CN"),
        RegionTab("OTHER", "Other"),
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
                        imageUrl = normalizeHeroImageUrl(
                            firstNonBlank(
                                obj.optString("image_url", ""),
                                obj.optString("preview_url", ""),
                                obj.optString("original_url", ""),
                                obj.optString("thumbnail_url", ""),
                                obj.optString("public_url", ""),
                                obj.optString("url", ""),
                            ),
                        ),
                        thumbnailUrl = normalizeHeroImageUrl(
                            firstNonBlank(
                                obj.optString("thumbnail_url", ""),
                                obj.optString("preview_url", ""),
                                obj.optString("image_url", ""),
                                obj.optString("public_url", ""),
                            ),
                        ),
                        previewUrl = normalizeHeroImageUrl(obj.optString("preview_url", "")),
                        originalUrl = normalizeHeroImageUrl(
                            firstNonBlank(
                                obj.optString("original_url", ""),
                                obj.optString("public_url", ""),
                            ),
                        ),
                        title = obj.optString(
                            "title",
                            obj.optString("user_prompt", "Hero #${obj.optString("id", "")}"),
                        ),
                        productKey = obj.optString("product_key", "").takeIf { it.isNotBlank() },
                        region = obj.optString("region", "OTHER").uppercase(),
                    )
                }
            }
        } catch (_: Exception) {
        }
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

    Box(modifier = modifier.fillMaxSize()) {
        HeroImagePreviewModal(
            visible = previewHeroId != null,
            heroId = previewHeroId,
            ownerId = ownerId,
            jwt = jwt,
            translationStore = translationStore,
            api = api,
            onDismiss = { previewHeroId = null },
            onSaved = { gridRefresh++ },
        )
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                regionTabs.forEach { tab ->
                    val active = currentRegionFilter == tab.code
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .border(
                                1.dp,
                                if (active) EazColors.Orange else EazColors.Orange.copy(alpha = 0.4f),
                                RoundedCornerShape(999.dp),
                            )
                            .background(
                                if (active) EazColors.Orange.copy(alpha = 0.25f)
                                else EazColors.Orange.copy(alpha = 0.08f),
                                RoundedCornerShape(999.dp),
                            )
                            .clickable { currentRegionFilter = tab.code }
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (active) Color.White else Color.White.copy(alpha = 0.9f),
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color(0x52020617))
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = (regionCounts[tab.code] ?: 0).toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.95f),
                            )
                        }
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
            ) {
                when {
                    loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = translationStore.t("creator.mobile.loading", "Loading..."),
                                color = Color.White.copy(alpha = 0.6f),
                            )
                        }
                    }
                    filteredItems.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = translationStore.t(
                                    "creator.marketing.no_hero_images",
                                    "No hero images yet.",
                                ),
                                color = Color.White.copy(alpha = 0.6f),
                            )
                        }
                    }
                    else -> {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(filteredItems) { item ->
                                val urlCandidates = listOfNotNull(
                                    r2PublicUrl(publicBaseUrl, item.r2Key),
                                    item.previewUrl,
                                    item.thumbnailUrl,
                                    item.imageUrl,
                                    item.originalUrl,
                                ).distinct()
                                Box(
                                    modifier = Modifier
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color.White.copy(alpha = 0.08f))
                                        .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(10.dp))
                                        .clickable { previewHeroId = item.id },
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
                                                    }
                                                },
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
}
