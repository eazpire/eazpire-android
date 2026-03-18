package com.eazpire.creator.ui.creator

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
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
    modifier: Modifier = Modifier
) {
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

    LaunchedEffect(currentSubTab, currentContentTab) {
        updateHeaderTitle()
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Main tabs: Content Creation | Content Publish (wie .creator-marketing-tabs)
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x4D1C2434))
                    .padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                listOf(
                    SUBTAB_CONTENT_CREATION to translationStore.t("creator.marketing.content_creation", "Content Creation"),
                    SUBTAB_CONTENT_PUBLISH to translationStore.t("creator.marketing.content_publish", "Content Publish")
                ).forEach { (subtab, label) ->
                    val isActive = currentSubTab == subtab
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { currentSubTab = subtab }
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp, horizontal = 20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isActive) EazColors.Orange else Color.White.copy(alpha = 0.6f)
                            )
                        }
                        if (isActive) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.dp)
                                    .background(EazColors.Orange)
                            )
                        }
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
                tokenStore = tokenStore,
                translationStore = translationStore
            )
            SUBTAB_CONTENT_PUBLISH -> MarketingContentPublishPanel(
                currentContentTab = currentContentTab,
                onContentTabChange = { currentContentTab = it; updateHeaderTitle() },
                tokenStore = tokenStore,
                translationStore = translationStore
            )
        }
    }
}

@Composable
private fun MarketingContentCreationPanel(
    currentContentTab: MarketingContentTab,
    onContentTabChange: (MarketingContentTab) -> Unit,
    tokenStore: SecureTokenStore,
    translationStore: TranslationStore
) {
    Column(modifier = Modifier.fillMaxSize()) {
        MarketingUnderTabs(
            currentContentTab = currentContentTab,
            onContentTabChange = onContentTabChange,
            translationStore = translationStore
        )
        when (currentContentTab) {
            CONTENT_HERO_IMAGES -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                AccountHeroImagesTab(
                    tokenStore = tokenStore,
                    darkMode = true,
                    modifier = Modifier.padding(20.dp)
                )
            }
            CONTENT_VIDEOS, CONTENT_IMAGES -> MarketingComingSoonBlock(
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

@Composable
private fun MarketingContentPublishPanel(
    currentContentTab: MarketingContentTab,
    onContentTabChange: (MarketingContentTab) -> Unit,
    tokenStore: SecureTokenStore,
    translationStore: TranslationStore
) {
    Column(modifier = Modifier.fillMaxSize()) {
        MarketingUnderTabs(
            currentContentTab = currentContentTab,
            onContentTabChange = onContentTabChange,
            translationStore = translationStore
        )
        when (currentContentTab) {
            CONTENT_HERO_IMAGES -> MarketingHeroImagesGrid(
                tokenStore = tokenStore,
                translationStore = translationStore
            )
            CONTENT_VIDEOS, CONTENT_IMAGES -> MarketingComingSoonBlock(
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
                .padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 0.dp),
            horizontalArrangement = Arrangement.spacedBy(0.dp)
        ) {
        tabs.forEach { (content, label) ->
            val isActive = currentContentTab == content
            Column(
                modifier = Modifier.clickable { onContentTabChange(content) }
            ) {
                Box(
                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isActive) EazColors.Orange else Color.White.copy(alpha = 0.6f)
                    )
                }
                if (isActive) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(EazColors.Orange)
                    )
                }
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
    val imageUrl: String?,
    val thumbnailUrl: String?,
    val title: String,
    val region: String
)

@Composable
private fun MarketingHeroImagesGrid(
    tokenStore: SecureTokenStore,
    translationStore: TranslationStore
) {
    val api = remember { CreatorApi(jwt = tokenStore.getJwt()) }
    val ownerId = remember(tokenStore) { tokenStore.getOwnerId() ?: "" }
    var items by remember { mutableStateOf<List<HeroImageItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(ownerId) {
        if (ownerId.isBlank()) {
            loading = false
            return@LaunchedEffect
        }
        loading = true
        try {
            val resp = withContext(Dispatchers.IO) { api.heroList(ownerId, limit = 100) }
            if (resp.optBoolean("ok", false)) {
                val arr = resp.optJSONArray("items") ?: org.json.JSONArray()
                items = (0 until arr.length()).mapNotNull { i ->
                    val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                    HeroImageItem(
                        id = obj.optString("id", ""),
                        imageUrl = obj.optString("image_url", null).takeIf { it.isNotBlank() },
                        thumbnailUrl = obj.optString("thumbnail_url", null).takeIf { it.isNotBlank() },
                        title = obj.optString("title", obj.optString("user_prompt", "Hero #${obj.optString("id", "")}")),
                        region = obj.optString("region", "OTHER").uppercase()
                    )
                }
            }
        } catch (_: Exception) {}
        loading = false
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
            items.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = translationStore.t("creator.marketing.no_hero_images", "No hero images yet."),
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(items) { item ->
                    val url = item.thumbnailUrl ?: item.imageUrl
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                    ) {
                        if (url != null) {
                            AsyncImage(
                                model = url,
                                contentDescription = item.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
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
                }
            }
        }
    }
}
