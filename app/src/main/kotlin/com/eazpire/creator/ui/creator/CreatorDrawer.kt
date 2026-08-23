package com.eazpire.creator.ui.creator

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.eazpire.creator.EazColors
import com.eazpire.creator.i18n.TranslationStore
import com.eazpire.creator.ui.header.CreatorSwitch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private val DrawerGlassTint = Color(0xFF0F0C1C).copy(alpha = 0.65f)

/** Mirrors `creator-mobile.css`: `.creator-drawer` + nav rows + `.creator-nav-menu-*` */
@Composable
fun CreatorDrawer(
    modifier: Modifier = Modifier,
    currentScreen: Int,
    screenLabels: List<String>,
    translationStore: TranslationStore,
    onDismiss: () -> Unit,
    onSwitchToShop: () -> Unit,
    onScreenSelect: (Int) -> Unit,
) {
    val closeLabel =
        translationStore.t("creator.mobile.menu_close", "Close menu")

    val navIcons: List<ImageVector> = remember {
        listOf(
            Icons.Outlined.GridView,
            Icons.Outlined.TravelExplore,
            Icons.Outlined.LightMode,
            Icons.Outlined.Image,
            Icons.Outlined.Campaign,
            Icons.Outlined.Bolt,
        )
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .shadow(
                elevation = 40.dp,
                shape = RectangleShape,
                ambientColor = Color.Black.copy(alpha = 0.35f),
                spotColor = Color.Black.copy(alpha = 0.4f),
            )
            .drawBehind {
                val stroke = 1.dp.toPx()
                drawRect(
                    color = Color.White.copy(alpha = 0.12f),
                    topLeft = Offset(size.width - stroke, 0f),
                    size = Size(stroke, size.height),
                )
                drawRect(
                    color = Color.White.copy(alpha = 0.06f),
                    topLeft = Offset(0f, 0f),
                    size = Size(stroke, size.height),
                )
            }
            .pointerInput(onDismiss) {
                var swipeAccum = 0f
                detectHorizontalDragGestures(
                    onHorizontalDrag = { _, dx -> swipeAccum += dx },
                    onDragEnd = {
                        if (swipeAccum < -50f) onDismiss()
                        swipeAccum = 0f
                    },
                )
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DrawerGlassTint),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                CreatorSwitch(
                    isCreatorMode = true,
                    compact = true,
                    onModeChange = { if (!it) onSwitchToShop() },
                    modifier = Modifier.weight(1f, fill = false),
                )
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .semantics { contentDescription = closeLabel }
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) { onDismiss() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "×",
                        color = Color.White,
                        fontSize = 22.sp,
                    )
                }
            }

            Divider(
                modifier = Modifier.fillMaxWidth(),
                thickness = 1.dp,
                color = Color.White.copy(alpha = 0.06f),
            )

            CreatorDrawerNavBand(
                labels = screenLabels,
                icons = navIcons,
                currentScreen = currentScreen,
                onScreenSelect = onScreenSelect,
            )
            Spacer(Modifier.weight(1f))
        }
    }
}

/**
 * Matches web `drawer-aquarium.js` mobile:
 * Container height/from first→last `[data-nav]` ± `--creator-drawer-aquarium-inner-pad-y` (6px),
 * clamped with `--creator-drawer-aquarium-pad-y` (10px); mask is full button bounds, radius 16.
 */
private data class DrawerNavAquariumBand(
    val topPx: Float,
    val heightPx: Float,
    val clipShape: NavigationAquariumClipShape,
)

@Composable
private fun CreatorDrawerNavBand(
    labels: List<String>,
    icons: List<ImageVector>,
    currentScreen: Int,
    onScreenSelect: (Int) -> Unit,
) {
    val density = LocalDensity.current
    val radiusPx = with(density) { 16.dp.toPx() }
    val innerPadPx = with(density) { 6.dp.toPx() }
    val padNavPx = with(density) { 10.dp.toPx() }

    val count = labels.size
    val ancestor = remember { mutableStateOf<LayoutCoordinates?>(null) }

    val rowRects: SnapshotStateList<Rect?> = remember(count) {
        mutableStateListOf<Rect?>().also { list ->
            repeat(count) { list.add(null) }
        }
    }

    val bandMask by derivedStateOf {
        val a = ancestor.value
        if (a == null || !a.isAttached) return@derivedStateOf null
        if (rowRects.size != count) return@derivedStateOf null
        val rects = ArrayList<Rect>(count)
        repeat(count) { i ->
            val r = rowRects[i] ?: return@derivedStateOf null
            if (r.width <= 0f || r.height <= 0f) return@derivedStateOf null
            rects.add(r)
        }
        val fr = rects.first()
        val lr = rects.last()
        val hAncestor = a.size.height.toFloat()
        val navInnerTop = padNavPx
        val navInnerBottom = max(navInnerTop, hAncestor - padNavPx)

        var bandTop = fr.top - innerPadPx
        var bandBottom = lr.bottom + innerPadPx
        var topVp = max(navInnerTop, bandTop)
        var bottomVp = min(navInnerBottom, bandBottom)
        if (bottomVp <= topVp) {
            topVp = navInnerTop
            bottomVp = navInnerBottom
        }
        val heightPx = max(1f, bottomVp - topVp)
        val bandRects = rects.map { r ->
            Rect(r.left, r.top - topVp, r.right, r.bottom - topVp)
        }
        DrawerNavAquariumBand(
            topPx = topVp,
            heightPx = heightPx,
            clipShape = NavigationAquariumClipShape(bandRects, radiusPx),
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 16.dp)
            .onGloballyPositioned { ancestor.value = it },
    ) {
        val band = bandMask
        if (band != null) {
            val topDp = with(density) { band.topPx.toDp() }
            val heightDp = with(density) { band.heightPx.toDp() }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = topDp)
                    .height(heightDp)
                    .clip(band.clipShape)
                    .zIndex(0f),
            ) {
                DrawerAquarium(visible = true, modifier = Modifier.fillMaxSize())
                DrawerNavBandShimmer(modifier = Modifier.fillMaxSize())
            }
        }

        Column(
            modifier = Modifier.zIndex(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            labels.forEachIndexed { index, label ->
                CreatorDrawerNavRow(
                    icon = icons.getOrElse(index) { Icons.Outlined.GridView },
                    label = label,
                    selected = index == currentScreen,
                    onClick = { onScreenSelect(index) },
                    measureModifier =
                        Modifier.onGloballyPositioned { coords ->
                            val a = ancestor.value ?: return@onGloballyPositioned
                            rowRects[index] = a.localBoundingBoxOf(coords)
                        },
                )
            }
        }
    }
}

private data class NavigationAquariumClipShape(
    private val rects: List<Rect>,
    private val cornerRadiusPx: Float,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val path = Path()
        for (r in rects) {
            path.addRoundRect(
                RoundRect(
                    left = r.left,
                    top = r.top,
                    right = r.right,
                    bottom = r.bottom,
                    topLeftCornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                    topRightCornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                    bottomRightCornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                    bottomLeftCornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                ),
            )
        }
        return Outline.Generic(path)
    }
}

/** Mirrors `creator-drawer__aquarium::after` shimmer (masked with aquarium band on web mobile). */
@Composable
private fun DrawerNavBandShimmer(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "drawer-shimmer")
    val shift by transition.animateFloat(
        initialValue = -0.6f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "drawer-shimmer-shift",
    )
    val ux = cos(110.0 * PI / 180.0).toFloat()
    val uy = sin(110.0 * PI / 180.0).toFloat()
    Box(
        modifier = modifier.drawBehind {
            val cx = size.width / 2f + ux * ((shift - 0.5f) * size.width)
            val cy = size.height / 2f + uy * ((shift - 0.5f) * size.height)
            val bandCenter = Offset(cx, cy)
            val bandLen = max(size.width, size.height) * 1.35f
            drawRect(
                brush = Brush.linearGradient(
                    0f to Color.Transparent,
                    0.5f to Color.White.copy(alpha = 0.05f),
                    1f to Color.Transparent,
                    start = bandCenter,
                    end = Offset(bandCenter.x + ux * bandLen, bandCenter.y + uy * bandLen),
                ),
                size = this.size,
            )
        },
    )
}

@Composable
private fun CreatorDrawerNavRow(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    measureModifier: Modifier = Modifier,
) {
    val rowShape = RoundedCornerShape(16.dp)
    val borderColor =
        if (selected) Color(0xFFFB923C).copy(alpha = 0.45f) else Color.White.copy(alpha = 0.08f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(measureModifier),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (selected) {
                        Modifier.shadow(
                            elevation = 12.dp,
                            shape = rowShape,
                            spotColor = EazColors.Orange.copy(alpha = 0.35f),
                            ambientColor = EazColors.Orange.copy(alpha = 0.22f),
                        )
                    } else Modifier,
                )
                .clip(rowShape)
                .border(1.dp, borderColor, rowShape)
                .then(
                    if (selected) {
                        Modifier.background(
                            Brush.linearGradient(
                                colors = listOf(
                                    EazColors.Orange.copy(alpha = 0.28f),
                                    EazColors.OrangeDark.copy(alpha = 0.12f),
                                ),
                                start = Offset(0f, 0f),
                                end = Offset(220f * 0.574f, 220f * (-0.5f)),
                            ),
                        )
                    } else {
                        Modifier.background(Color.White.copy(alpha = 0.04f))
                    },
                )
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) { onClick() }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CreatorNavIconWrap(selected = selected) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.95f),
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                text = label,
                color = Color.White.copy(alpha = if (selected) 1f else 0.9f),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.02).sp,
                lineHeight = 19.sp,
            )
        }
    }
}

@Composable
private fun CreatorNavIconWrap(
    selected: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val borderColor =
        if (selected) Color(0xFFFFF5E0).copy(alpha = 0.45f)
        else Color.White.copy(alpha = 0.16f)
    val gradient = if (selected) {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFFFFF0B8).copy(alpha = 0.35f),
                EazColors.Orange.copy(alpha = 0.45f),
            ),
            start = Offset(0f, 0f),
            end = Offset(60f, 56f),
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.14f),
                Color.White.copy(alpha = 0.04f),
            ),
            start = Offset(0f, 0f),
            end = Offset(50f, 56f),
        )
    }
    Box(
        modifier = modifier
            .size(42.dp)
            .shadow(
                elevation = if (selected) 8.dp else 4.dp,
                shape = shape,
                spotColor = if (selected) EazColors.Orange.copy(alpha = 0.4f)
                else Color.Black.copy(alpha = 0.4f),
            )
            .clip(shape)
            .border(1.dp, borderColor, shape)
            .background(brush = gradient),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
