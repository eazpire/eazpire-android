package com.eazpire.creator.ui.creator

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.chat.EazyMascotIcon
import com.eazpire.creator.i18n.TranslationStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun CreatorHeaderEazyStartBubble(
    label: String,
    loading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * false: tail leads the pill and points left (Eazy sits to the left of the bubble in the parent Row).
     * true: tail follows the pill and points right (Eazy sits to the right — e.g. centered overlay [bubble][eazy]).
     */
    tailTowardEnd: Boolean = false
) {
    val shouldPulse = enabled && !loading
    val infiniteTransition = rememberInfiniteTransition(label = "eazyStartBubblePulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val scale = if (shouldPulse) pulseScale else 1f
    val shape = RoundedCornerShape(14.dp)
    val gradient = Brush.linearGradient(
        colors = listOf(Color(0xFFFF9F40), EazColors.Orange, Color(0xFFEA580C))
    )
    val tailBox: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .size(width = 8.dp, height = 12.dp)
                .drawBehind {
                    val w = size.width
                    val h = size.height
                    val p = Path().apply {
                        if (tailTowardEnd) {
                            moveTo(0f, h * 0.2f)
                            lineTo(w, h * 0.5f)
                            lineTo(0f, h * 0.8f)
                        } else {
                            moveTo(w, h * 0.2f)
                            lineTo(0f, h * 0.5f)
                            lineTo(w, h * 0.8f)
                        }
                        close()
                    }
                    drawPath(p, brush = gradient)
                    drawPath(
                        p,
                        color = Color.White.copy(alpha = 0.88f),
                        style = Stroke(
                            width = 2f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f), 0f)
                        )
                    )
                }
        )
    }
    val pill: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .width(148.dp)
                .clip(shape)
                .drawBehind {
                    drawRoundRect(
                        brush = gradient,
                        cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx())
                    )
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.88f),
                        style = Stroke(
                            width = 2.2f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(9f, 6f), 0f)
                        ),
                        cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx())
                    )
                }
                .clickable(enabled = enabled && !loading) { onClick() }
                .padding(horizontal = 8.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    text = label,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    maxLines = 2
                )
            }
        }
    }
    Row(
        modifier = modifier.scale(scale),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (tailTowardEnd) {
            pill()
            tailBox()
        } else {
            tailBox()
            pill()
        }
    }
}

/** Header 1:1 wie Web creator-mobile.css .creator-header */
@Composable
fun CreatorHeader(
    currentScreen: Int,
    screenLabels: List<String>,
    translationStore: TranslationStore,
    onMenuClick: () -> Unit = {},
    onBalanceClick: () -> Unit,
    onAccountClick: () -> Unit,
    tokenStore: SecureTokenStore,
    eazyDocked: Boolean = false,
    eazySnapModeActive: Boolean = false,
    onEazyClick: () -> Unit = {},
    onEazyLongPress: () -> Unit = {},
    slotBoundsState: androidx.compose.runtime.MutableState<Rect?>? = null,
    audioStore: com.eazpire.creator.audio.CreatorAudioStore? = null,
    onAudioModalOpen: () -> Unit = {},
    marketingTitleOverride: String? = null,
    /** Generator / Hero: face toward header speech bubble */
    eazyLookLeft: Boolean = false,
    showStartGenerationBubble: Boolean = false,
    startGenerationLoading: Boolean = false,
    onStartGenerationClick: () -> Unit = {},
    startGenerationLabel: String = "",
    modifier: Modifier = Modifier
) {
    var fiatText by remember { mutableStateOf("…") }
    var fiatSymbol by remember { mutableStateOf("€") }
    val api = remember { CreatorApi(jwt = tokenStore.getJwt()) }
    val ownerId = remember(tokenStore) { tokenStore.getOwnerId() ?: "" }
    val scope = rememberCoroutineScope()
    val boomScale = remember { Animatable(1f) }

    LaunchedEffect(eazyDocked) {
        if (eazyDocked) {
            boomScale.snapTo(1.15f)
            boomScale.animateTo(1f, tween(400))
        }
    }

    LaunchedEffect(ownerId) {
        if (ownerId.isNotBlank()) {
            try {
                val payout = withContext(Dispatchers.IO) { api.getCreatorPayoutOverview(ownerId, 90) }
                if (payout.optBoolean("ok", false)) {
                    val amount = payout.optDouble("availableAmount", 0.0)
                    fiatText = "%.2f".format(amount)
                    fiatSymbol = when (payout.optString("currency", "EUR").uppercase()) {
                        "USD" -> "$"
                        "GBP" -> "£"
                        "CHF" -> "CHF "
                        else -> "€"
                    }
                } else {
                    fiatText = "0.00"
                }
            } catch (_: Exception) {
                fiatText = "0.00"
            }
        } else {
            fiatText = "0.00"
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xEB080512),
                        Color(0x99080512),
                        Color.Transparent
                    )
                )
            )
            .padding(top = 12.dp, start = 16.dp, end = 16.dp, bottom = 12.dp)
    ) {
        // Top row: logo (zentriert) | balance + account
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Menu + Logo links
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { onMenuClick() }
                        .background(
                            Color.White.copy(alpha = 0.08f),
                            RoundedCornerShape(10.dp)
                        )
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Menu,
                        contentDescription = "Menu",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data("https://cdn.shopify.com/s/files/1/0739/5203/5098/files/eazpire-creator-logo.png?v=1763666950")
                        .build(),
                    contentDescription = "eazpire creator",
                    modifier = Modifier.size(height = 36.dp, width = 126.dp),
                    contentScale = ContentScale.Fit
                )
            }

            // Balance + Account
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .height(36.dp)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { onBalanceClick() }
                        .background(
                            Color.White.copy(alpha = 0.08f),
                            RoundedCornerShape(10.dp)
                        )
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = fiatText,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            ),
                            color = EazColors.Orange
                        )
                        Text(
                            text = fiatSymbol,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp
                            ),
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { onAccountClick() }
                        .background(
                            Color.White.copy(alpha = 0.08f),
                            RoundedCornerShape(10.dp)
                        )
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = "Account",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Content: dots + title + swipe hint (gap 4dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Dots: 8x8, gap 8
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                screenLabels.forEachIndexed { index, _ ->
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                color = if (index == currentScreen) EazColors.Orange else Color.White.copy(alpha = 0.2f),
                                shape = androidx.compose.foundation.shape.CircleShape
                            )
                    )
                }
            }
            // Title row: Audio-Widget (links) + Titel (zentriert) + Eazy-Snap-Slot (rechts) – wie Web
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    audioStore?.let { store ->
                        val isPlaying by store.isPlaying.collectAsState()
                        val currentPlaybackId by store.currentPlaybackId.collectAsState()
                        val currentPlaybackItem by store.currentPlaybackItem.collectAsState()
                        val visualizerLevels by store.visualizerLevels.collectAsState()
                        val hasAudio = currentPlaybackId != null
                        CreatorAudioWidget(
                            isPlaying = isPlaying,
                            hasAudio = hasAudio,
                            visualizerLevels = visualizerLevels,
                            onOpenModal = onAudioModalOpen,
                            onPlayPause = {
                                val item = store.getItem(currentPlaybackId as? String ?: "")
                                    ?: currentPlaybackItem
                                store.togglePlay(item)
                            },
                            onSeekBack = { store.seekBack() },
                            onSeekForward = { store.seekForward() },
                            modifier = Modifier.align(Alignment.CenterStart)
                        )
                    }
                }
                Text(
                    text = if (currentScreen == 3 && marketingTitleOverride != null) {
                        marketingTitleOverride
                    } else {
                        screenLabels.getOrElse(currentScreen) { "" }
                    },
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color.White
                )
                Box(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.align(Alignment.CenterEnd),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .scale(boomScale.value)
                                .padding(horizontal = 2.dp)
                                .size(36.dp)
                                .onGloballyPositioned { coordinates ->
                                    slotBoundsState?.value = coordinates.boundsInRoot()
                                }
                                .then(
                                    if (eazySnapModeActive && !eazyDocked) Modifier
                                        .background(EazColors.Orange.copy(alpha = 0.15f), CircleShape)
                                        .border(2.dp, EazColors.Orange.copy(alpha = 0.5f), CircleShape)
                                    else Modifier
                                )
                                .then(
                                    if (eazyDocked) Modifier.pointerInput(Unit) {
                                        detectTapGestures(
                                            onTap = { onEazyClick() },
                                            onPress = {
                                                var job: Job? = null
                                                job = scope.launch {
                                                    delay(300)
                                                    onEazyLongPress()
                                                }
                                                try {
                                                    awaitRelease()
                                                } catch (_: Exception) {}
                                                job?.cancel()
                                            }
                                        )
                                    } else Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (eazyDocked) {
                                val faceBubble =
                                    eazyLookLeft && !showStartGenerationBubble
                                EazyMascotIcon(
                                    modifier = Modifier.fillMaxSize(),
                                    lookLeft = faceBubble
                                )
                            }
                        }
                        if (showStartGenerationBubble && startGenerationLabel.isNotBlank()) {
                            Spacer(modifier = Modifier.width(10.dp))
                            CreatorHeaderEazyStartBubble(
                                label = startGenerationLabel,
                                loading = startGenerationLoading,
                                enabled = true,
                                onClick = onStartGenerationClick,
                                modifier = Modifier.padding(end = 2.dp)
                            )
                        }
                    }
                }
            }
            // Swipe hint: 12sp, rgba(255,255,255,0.6)
            Text(
                text = translationStore.t("creator.mobile.swipe_hint", "Swipe to switch"),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = Color.White.copy(alpha = 0.6f)
            )
        }
    }
}
