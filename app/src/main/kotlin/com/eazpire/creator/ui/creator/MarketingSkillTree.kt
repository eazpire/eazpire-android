package com.eazpire.creator.ui.creator

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eazpire.creator.EazColors
import com.eazpire.creator.R
import com.eazpire.creator.i18n.TranslationStore

const val MKT_PARENT_CREATION = "content-creation"
const val MKT_PARENT_PUBLISH = "content-publish"
const val MKT_PARENT_PROMOTIONS = "promotions"

const val MKT_CHILD_HERO = "hero-images"
const val MKT_CHILD_VIDEO = "videos"
const val MKT_CHILD_IMAGES = "images"
const val MKT_FN_STUDIO = "video-studio"
const val MKT_FN_GENERATOR = "video-generator"
const val MKT_FN_TRANSITION = "video-transition"
const val MKT_FN_HERO_GENERATOR = "hero-generator"
const val MKT_FN_CHARACTER_GENERATOR = "character-generator"
const val MKT_FN_SMM = "social-media-manager"

private val GoldLine = Color(0xFFE8C547)
private val GoldGlow = Color(0x66E8C547)

data class MarketingCardSpec(
    val id: String,
    val title: String,
    val icon: String,
    val bgRes: Int,
    val description: String? = null,
    val isFunction: Boolean = false,
)

@Composable
fun MarketingSkillTreeCard(
    spec: MarketingCardSpec,
    isActive: Boolean,
    dimmed: Boolean,
    onClick: () -> Unit,
    onPositioned: (LayoutCoordinates) -> Unit = {},
    modifier: Modifier = Modifier,
    minHeightDp: Int = 96,
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeightDp.dp)
            .clip(shape)
            .onGloballyPositioned(onPositioned)
            .clickable(onClick = onClick)
            .border(
                width = if (isActive) 2.dp else 1.dp,
                color = if (isActive) EazColors.Orange else Color.White.copy(alpha = 0.18f),
                shape = shape,
            )
    ) {
        Image(
            painter = painterResource(spec.bgRes),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = when {
                isActive -> 0.55f
                dimmed -> 0.18f
                else -> 0.42f
            },
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    when {
                        isActive -> Color(0x99000000)
                        dimmed -> Color(0xCC0A0E18)
                        else -> Color(0x990A0E18)
                    }
                )
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(text = spec.icon, fontSize = if (spec.isFunction) 22.sp else 26.sp)
            Box(modifier = Modifier.height(6.dp))
            Text(
                text = spec.title,
                color = Color.White.copy(alpha = if (dimmed) 0.45f else 1f),
                fontSize = if (spec.isFunction) 12.sp else 13.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!spec.description.isNullOrBlank()) {
                Box(modifier = Modifier.height(4.dp))
                Text(
                    text = spec.description,
                    color = Color.White.copy(alpha = if (dimmed) 0.3f else 0.65f),
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun MarketingForkConnector(
    parentCenterXPx: Float,
    childCenterXsPx: List<Float>,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
) {
    if (childCenterXsPx.isEmpty() || parentCenterXPx <= 0f) {
        androidx.compose.foundation.layout.Spacer(modifier = modifier.height(28.dp))
        return
    }
    val density = LocalDensity.current
    val heightPx = with(density) { 36.dp.toPx() }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(parentCenterXPx, childCenterXsPx.joinToString(), animate) {
        if (animate) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(900))
        } else {
            progress.snapTo(1f)
        }
    }
    Canvas(modifier = modifier.fillMaxWidth().height(36.dp)) {
        val sorted = childCenterXsPx.sorted()
        val barY = heightPx * 0.42f
        val endY = heightPx - 2f
        val path = Path().apply {
            moveTo(parentCenterXPx, 0f)
            lineTo(parentCenterXPx, barY)
            lineTo(sorted.first(), barY)
            sorted.forEachIndexed { i, x ->
                lineTo(x, endY)
                if (i < sorted.lastIndex) {
                    lineTo(x, barY)
                    lineTo(sorted[i + 1], barY)
                }
            }
        }
        val measure = PathMeasure()
        measure.setPath(path, false)
        val len = measure.length
        val dash = PathEffect.dashPathEffect(
            floatArrayOf(len, len),
            (1f - progress.value) * len,
        )
        drawPath(
            path = path,
            color = GoldGlow,
            style = Stroke(width = 8f, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = dash),
        )
        drawPath(
            path = path,
            color = GoldLine,
            style = Stroke(width = 3.5f, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = dash),
        )
    }
}

@Composable
fun rememberMarketingParentCards(translationStore: TranslationStore): List<MarketingCardSpec> = listOf(
    MarketingCardSpec(
        id = MKT_PARENT_CREATION,
        title = translationStore.t("creator.marketing.content_creation", "Content Creation"),
        icon = "✨",
        bgRes = R.drawable.cmkt_bg_content_creation,
    ),
    MarketingCardSpec(
        id = MKT_PARENT_PUBLISH,
        title = translationStore.t("creator.marketing.content_publish", "Content Publish"),
        icon = "📤",
        bgRes = R.drawable.cmkt_bg_content_publish,
    ),
    MarketingCardSpec(
        id = MKT_PARENT_PROMOTIONS,
        title = translationStore.t("creator.marketing.promotion", "Promotion"),
        icon = "🏷️",
        bgRes = R.drawable.cmkt_bg_promotions,
    ),
)

@Composable
fun rememberMarketingCreationChildren(translationStore: TranslationStore): List<MarketingCardSpec> = listOf(
    MarketingCardSpec(
        id = MKT_CHILD_IMAGES,
        title = translationStore.t("creator.marketing.images", "Images"),
        icon = "📷",
        bgRes = R.drawable.cmkt_bg_images,
    ),
    MarketingCardSpec(
        id = MKT_CHILD_VIDEO,
        title = translationStore.t("creator.marketing.video", "Video"),
        icon = "🎬",
        bgRes = R.drawable.cmkt_bg_videos,
    ),
)

@Composable
fun rememberMarketingImageFunctions(translationStore: TranslationStore): List<MarketingCardSpec> = listOf(
    MarketingCardSpec(
        id = MKT_FN_HERO_GENERATOR,
        title = translationStore.t("creator.marketing.hero_generator", "Hero Generator"),
        icon = "🖼️",
        bgRes = R.drawable.cmkt_bg_images,
        description = translationStore.t(
            "creator.content_creation.videos.tool_hero_generator_desc",
            "Create shop hero images with your products.",
        ),
        isFunction = true,
    ),
    MarketingCardSpec(
        id = MKT_FN_CHARACTER_GENERATOR,
        title = translationStore.t("creator.marketing.character_generator", "Character Generator"),
        icon = "🧑",
        bgRes = R.drawable.cmkt_bg_images,
        description = translationStore.t(
            "creator.content_creation.videos.tool_character_generator_desc",
            "Create character images for video and social posts.",
        ),
        isFunction = true,
    ),
)

@Composable
fun rememberMarketingVideoFunctions(translationStore: TranslationStore): List<MarketingCardSpec> = listOf(
    MarketingCardSpec(
        id = MKT_FN_STUDIO,
        title = translationStore.t("creator.video_studio.title", "Video Studio"),
        icon = "🎞️",
        bgRes = R.drawable.cmkt_bg_videos,
        description = translationStore.t(
            "creator.video_studio.card_desc",
            "Edit timeline, assets, and export.",
        ),
        isFunction = true,
    ),
    MarketingCardSpec(
        id = MKT_FN_GENERATOR,
        title = translationStore.t("creator.video_generator.title", "Video Generator"),
        icon = "✨",
        bgRes = R.drawable.cmkt_bg_videos,
        description = translationStore.t(
            "creator.video_generator.card_desc",
            "Motion control AI video generation.",
        ),
        isFunction = true,
    ),
    MarketingCardSpec(
        id = MKT_FN_TRANSITION,
        title = translationStore.t("creator.video_transition.title", "Video Transition"),
        icon = "🔀",
        bgRes = R.drawable.cmkt_bg_videos,
        description = translationStore.t(
            "creator.video_transition.card_desc",
            "Chain clips with cinematic transitions.",
        ),
        isFunction = true,
    ),
)

@Composable
fun rememberMarketingPublishChildren(translationStore: TranslationStore): List<MarketingCardSpec> = listOf(
    MarketingCardSpec(
        id = MKT_CHILD_HERO,
        title = translationStore.t("creator.marketing.hero_images", "Hero Images"),
        icon = "🖼️",
        bgRes = R.drawable.cmkt_bg_hero_images,
    ),
    MarketingCardSpec(
        id = MKT_FN_SMM,
        title = translationStore.t("creator.social_media_manager.title", "Social Media Manager"),
        icon = "📣",
        bgRes = R.drawable.cmkt_bg_content_publish,
        description = translationStore.t(
            "creator.social_media_manager.card_desc",
            "Connect your accounts and publish posts.",
        ),
        isFunction = true,
    ),
)

/**
 * Measures card centers relative to [treeRoot] for fork connectors.
 */
class MarketingTreeGeometry {
    var rootCoords: LayoutCoordinates? by mutableStateOf(null)
    private val cardCenters = mutableMapOf<String, Offset>()

    fun updateCard(id: String, coords: LayoutCoordinates) {
        val root = rootCoords ?: return
        if (!root.isAttached || !coords.isAttached) return
        val pos = coords.positionInRoot()
        val rootPos = root.positionInRoot()
        cardCenters[id] = Offset(
            x = pos.x - rootPos.x + coords.size.width / 2f,
            y = pos.y - rootPos.y + coords.size.height,
        )
    }

    fun centerX(id: String): Float = cardCenters[id]?.x ?: 0f
}
