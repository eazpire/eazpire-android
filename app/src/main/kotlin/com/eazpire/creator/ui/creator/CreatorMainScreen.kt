package com.eazpire.creator.ui.creator

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.eazpire.creator.R
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.audio.CreatorAudioStore
import com.eazpire.creator.creatorcodes.CreatorCodeAvailableHintStore
import com.eazpire.creator.i18n.TranslationStore
import com.eazpire.creator.locale.LocaleStore
import com.eazpire.creator.ui.footer.TermsModal
import com.eazpire.creator.ui.header.LanguageModal
import com.eazpire.creator.ui.header.LocaleModalItem
import com.eazpire.creator.chat.EazySidebarTab
import com.eazpire.creator.ui.header.LanguageChildren
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val GalaxyGradient = Brush.verticalGradient(
    colors = listOf(
        Color(0x660A0514),
        Color(0x9905020F)
    )
)

@Composable
fun CreatorMainScreen(
    tokenStore: SecureTokenStore,
    localeStore: LocaleStore,
    translationStore: TranslationStore,
    onSwitchToShop: () -> Unit,
    onAccountClick: () -> Unit,
    onEazyChatOpen: (EazySidebarTab?) -> Unit,
    onHeroJobStarted: (jobId: String, summary: String) -> Unit = { _, _ -> },
    onVideoJobStarted: (jobId: String, summary: String) -> Unit = { _, _ -> },
    onGeneratorJobStarted: (jobId: String, summary: String) -> Unit = { _, _ -> },
    /** True while on Generator (screen 1) and prompt/refs ready – drives floating Eazy lookLeft in ShopScreen */
    onGeneratorEazyLookLeftChange: (Boolean) -> Unit = {},
    eazyDocked: Boolean = false,
    eazySnapModeActive: Boolean = false,
    onEazySnapModeChange: (Boolean) -> Unit = {},
    onEazyLongPress: () -> Unit = {},
    slotBoundsState: androidx.compose.runtime.MutableState<Rect?>? = null,
    onEazyGenerationOverlayChange: (visible: Boolean, loading: Boolean) -> Unit = { _, _ -> },
    /**
     * ShopScreen `eazyGenerationOverlay` — single source for header slot hide + dock bar suppress
     * (must match overlay timing from generator direct sync).
     */
    shopGenerationOverlayActive: Boolean = false,
    /** ShopScreen: docked mascot should face toward the generation bubble when overlay is on. */
    generationBubbleFaceLeft: Boolean? = null,
    /** Bumped from compose overlay "Start" tap (ShopScreen); mirrors header start nonces. */
    overlayComposeStartKey: Int = 0,
    pendingWearPairToken: String? = null,
    onWearPairTokenConsumed: () -> Unit = {},
    initialScreen: Int? = null,
    initialDesignsActivityFilter: String? = null,
    onInitialDesignsActivityConsumed: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var drawerVisible by remember { mutableStateOf(false) }
    var salesModalVisible by remember { mutableStateOf(false) }
    var creatorSettingsVisible by remember { mutableStateOf(false) }
    var wearPairTokenForSettings by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(pendingWearPairToken) {
        val t = pendingWearPairToken?.trim().orEmpty()
        if (t.isNotBlank()) {
            wearPairTokenForSettings = t
            creatorSettingsVisible = true
            onWearPairTokenConsumed()
        }
    }
    var audioModalVisible by remember { mutableStateOf(false) }
    var languageModalVisible by remember { mutableStateOf(false) }
    var termsModalVisible by remember { mutableStateOf(false) }
    var marketingTitleOverride by remember { mutableStateOf<String?>(null) }
    var automationsTitleOverride by remember { mutableStateOf<String?>(null) }
    var marketingSessionKey by remember { mutableIntStateOf(0) }
    var pendingCreationsTab by remember { mutableStateOf<String?>(null) }
    var pendingMarketingHero by remember { mutableStateOf(false) }
    val appContext = LocalContext.current.applicationContext
    val audioStore = remember { CreatorAudioStore(appContext) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val api = remember { CreatorApi(jwt = tokenStore.getJwt()) }
    val ownerId = remember(tokenStore) { tokenStore.getOwnerId() ?: "" }
    val creatorCodeHintActive by CreatorCodeAvailableHintStore.active.collectAsState()
    var currentScreen by remember(initialScreen) {
        mutableIntStateOf(initialScreen?.coerceIn(0, 4) ?: 0)
    }
    LaunchedEffect(initialScreen) {
        val screen = initialScreen?.coerceIn(0, 4) ?: return@LaunchedEffect
        currentScreen = screen
    }
    var generatorEazyReady by remember { mutableStateOf(false) }
    var heroEazyReady by remember { mutableStateOf(false) }
    var videoEazyReady by remember { mutableStateOf(false) }
    var genHeaderStartNonce by remember { mutableIntStateOf(0) }
    var heroHeaderStartNonce by remember { mutableIntStateOf(0) }
    var videoHeaderStartNonce by remember { mutableIntStateOf(0) }
    var generatorGenerating by remember { mutableStateOf(false) }
    var heroGenerating by remember { mutableStateOf(false) }
    var videoGenerating by remember { mutableStateOf(false) }
    var marketingHeroTabVisible by remember { mutableStateOf(false) }
    var marketingVideoTabVisible by remember { mutableStateOf(false) }

    /** Open native generator with design prefill (Remix / Generate New from design detail sheet). */
    var generatorPrefillRequest by remember { mutableStateOf<GeneratorPrefillRequest?>(null) }

    var prevOverlayComposeKey by remember { mutableIntStateOf(-1) }
    LaunchedEffect(overlayComposeStartKey) {
        if (overlayComposeStartKey <= 0 || overlayComposeStartKey == prevOverlayComposeKey) return@LaunchedEffect
        prevOverlayComposeKey = overlayComposeStartKey
        when (currentScreen) {
            1 -> genHeaderStartNonce++
            3 -> {
                if (marketingVideoTabVisible) videoHeaderStartNonce++
                else heroHeaderStartNonce++
            }
            else -> Unit
        }
    }

    LaunchedEffect(
        currentScreen,
        generatorEazyReady,
        heroEazyReady,
        videoEazyReady,
        marketingHeroTabVisible,
        marketingVideoTabVisible
    ) {
        val marketingLook =
            (currentScreen == 3 && marketingHeroTabVisible && heroEazyReady) ||
                (currentScreen == 3 && marketingVideoTabVisible && videoEazyReady)
        val look = (currentScreen == 1 && generatorEazyReady) || marketingLook
        onGeneratorEazyLookLeftChange(look)
    }

    /** Marketing (hero + video tabs): overlay for docked compose bubble + header slot hide. */
    LaunchedEffect(
        currentScreen,
        marketingHeroTabVisible,
        marketingVideoTabVisible,
        heroEazyReady,
        heroGenerating,
        videoEazyReady,
        videoGenerating
    ) {
        when (currentScreen) {
            3 -> {
                val visible =
                    (marketingHeroTabVisible && (heroEazyReady || heroGenerating)) ||
                        (marketingVideoTabVisible && (videoEazyReady || videoGenerating))
                val loading =
                    (marketingHeroTabVisible && heroGenerating) ||
                        (marketingVideoTabVisible && videoGenerating)
                onEazyGenerationOverlayChange(visible, loading)
            }
            1 -> Unit
            else -> onEazyGenerationOverlayChange(false, false)
        }
    }

    LaunchedEffect(currentScreen) {
        if (currentScreen != 1) generatorGenerating = false
        if (currentScreen != 3) {
            heroGenerating = false
            videoGenerating = false
        }
    }

    DisposableEffect(lifecycleOwner, audioStore) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> audioStore.setAppActive(true)
                Lifecycle.Event.ON_STOP -> audioStore.setAppActive(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            audioStore.setAppActive(false)
            audioStore.release()
        }
    }

    LaunchedEffect(ownerId) {
        if (ownerId.isBlank()) return@LaunchedEffect
        audioStore.bindOwner(ownerId)
        try {
            val res = withContext(Dispatchers.IO) { api.getCreatorAudio(ownerId) }
            if (res.optBoolean("ok", false)) {
                val url = res.optString("url", "").takeIf { it.isNotBlank() }
                val audioId = res.optString("audio_id", "").takeIf { it.isNotBlank() }
                if (url != null && audioId != null) {
                    val item = com.eazpire.creator.audio.CreatorAudioItem(
                        id = audioId,
                        title = "",
                        url = url,
                        durationSec = 0,
                        ownerId = ownerId,
                        coverUrl = null
                    )
                    audioStore.armRemoteTrack(item)
                }
            }
        } catch (_: Exception) {}
    }
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // Galaxy background (wie Web: creator-mobile.css)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(GalaxyGradient)
        ) {
            Image(
                painter = painterResource(R.drawable.galaxy_nebula_bg),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.85f
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0x660A0514),
                                Color(0x9905020F)
                            )
                        )
                    )
            )
        }
        Column(modifier = Modifier.fillMaxSize()) {
                CreatorHeader(
                    currentScreen = currentScreen,
                    screenLabels = listOf(
                        translationStore.t("creator.mobile.dashboard", "Dashboard"),
                        translationStore.t("creator.mobile.generator", "Generator"),
                        translationStore.t("creator.mobile.creations", "Creations"),
                        translationStore.t("creator.mobile.marketing", "Marketing"),
                        translationStore.t("creator.mobile.automations", "Automations")
                    ),
                    translationStore = translationStore,
                    onMenuClick = { drawerVisible = true },
                    onBalanceClick = { salesModalVisible = true },
                    onAccountClick = { creatorSettingsVisible = true },
                    profileHintActive = creatorCodeHintActive,
                    tokenStore = tokenStore,
                    eazyDocked = eazyDocked,
                    eazySnapModeActive = eazySnapModeActive,
                    onEazyClick = { onEazyChatOpen(null) },
                    onEazyLongPress = onEazyLongPress,
                    slotBoundsState = slotBoundsState,
                    audioStore = audioStore,
                    onAudioModalOpen = { audioModalVisible = true },
                    marketingTitleOverride = marketingTitleOverride,
                    automationsTitleOverride = automationsTitleOverride,
                    eazyLookLeft = generationBubbleFaceLeft
                        ?: run {
                            val marketingLook =
                                (currentScreen == 3 && marketingHeroTabVisible && heroEazyReady) ||
                                    (currentScreen == 3 && marketingVideoTabVisible && videoEazyReady)
                            (currentScreen == 1 && generatorEazyReady) || marketingLook
                        },
                    hideEazyHeaderSlotWhenGenerationOverlay = shopGenerationOverlayActive,
                    showStartGenerationBubble = false,
                    startGenerationLoading = (currentScreen == 1 && generatorGenerating) ||
                        (currentScreen == 3 && (
                            (marketingHeroTabVisible && heroGenerating) ||
                                (marketingVideoTabVisible && videoGenerating)
                            )),
                    onStartGenerationClick = {
                        when (currentScreen) {
                            1 -> genHeaderStartNonce++
                            3 -> {
                                if (marketingVideoTabVisible) videoHeaderStartNonce++
                                else heroHeaderStartNonce++
                            }
                        }
                    },
                    startGenerationLabel = translationStore.t(
                        "creator.generator_eazy.bubble_start",
                        "Start generation"
                    )
                )

            LaunchedEffect(currentScreen) {
                if (currentScreen == 3) {
                    marketingSessionKey++
                } else {
                    marketingTitleOverride = null
                }
                if (currentScreen != 4) {
                    automationsTitleOverride = null
                }
            }
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                val contentMaxHeight = maxHeight
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(
                            currentScreen,
                            drawerVisible,
                            salesModalVisible,
                            creatorSettingsVisible,
                            audioModalVisible,
                            languageModalVisible,
                            termsModalVisible
                        ) {
                            var dragX = 0f
                            detectHorizontalDragGestures(
                                onHorizontalDrag = { change, amount ->
                                    val modalVisible = drawerVisible ||
                                        salesModalVisible ||
                                        creatorSettingsVisible ||
                                        audioModalVisible ||
                                        languageModalVisible ||
                                        termsModalVisible
                                    if (modalVisible) return@detectHorizontalDragGestures
                                    dragX += amount
                                    change.consume()
                                },
                                onDragEnd = {
                                    val modalVisible = drawerVisible ||
                                        salesModalVisible ||
                                        creatorSettingsVisible ||
                                        audioModalVisible ||
                                        languageModalVisible ||
                                        termsModalVisible
                                    if (modalVisible) return@detectHorizontalDragGestures
                                    when {
                                        dragX <= -120f -> currentScreen = (currentScreen + 1).coerceAtMost(4)
                                        dragX >= 120f -> currentScreen = (currentScreen - 1).coerceAtLeast(0)
                                    }
                                    dragX = 0f
                                }
                            )
                        }
                ) {
                    val guestLockedScreen = !tokenStore.isLoggedIn() && currentScreen in 1..4
                    Box(modifier = Modifier.fillMaxSize()) {
                    when (currentScreen) {
                        0 -> CreatorDashboardScreen(
                            tokenStore = tokenStore,
                            translationStore = translationStore,
                            onOpenSalesModal = { salesModalVisible = true },
                            onLoginClick = onAccountClick,
                            onNavigateToGenerator = { currentScreen = 1 },
                            onNavigateToDesigns = {
                                pendingCreationsTab = "designs"
                                currentScreen = 2
                            },
                            onNavigateToProducts = {
                                pendingCreationsTab = "products"
                                currentScreen = 2
                            },
                            onNavigateToMarketingHero = {
                                pendingMarketingHero = true
                                currentScreen = 3
                            },
                            onNavigateToAutomations = { currentScreen = 4 },
                            maxHeight = contentMaxHeight,
                            modifier = Modifier.fillMaxSize()
                        )
                        1 -> CreatorGeneratorScreen(
                            tokenStore = tokenStore,
                            translationStore = translationStore,
                            onOpenEazyChat = onEazyChatOpen,
                            onGeneratorJobStarted = onGeneratorJobStarted,
                            onGeneratorEazyReadyChange = { generatorEazyReady = it },
                            headerStartNonce = genHeaderStartNonce,
                            onGeneratorGeneratingChange = { generatorGenerating = it },
                            eazyDocked = eazyDocked,
                            suppressDockedComposeBar = shopGenerationOverlayActive,
                            onGenerationOverlaySyncToShop = { visible, loading ->
                                onEazyGenerationOverlayChange(visible, loading)
                            },
                            onFloatingComposeStart = { genHeaderStartNonce++ },
                            maxHeight = contentMaxHeight,
                            modifier = Modifier.fillMaxSize(),
                            generatorPrefillRequest = generatorPrefillRequest,
                            onGeneratorPrefillConsumed = { generatorPrefillRequest = null }
                        )
                        2 -> CreatorCreationsScreen(
                            tokenStore = tokenStore,
                            translationStore = translationStore,
                            initialDesignsActivityFilter = initialDesignsActivityFilter,
                            onInitialDesignsActivityConsumed = onInitialDesignsActivityConsumed,
                            initialCreationsTab = pendingCreationsTab,
                            onInitialCreationsTabConsumed = { pendingCreationsTab = null },
                            maxHeight = contentMaxHeight,
                            modifier = Modifier.fillMaxSize(),
                            onRequestGeneratorPrefill = { req ->
                                generatorPrefillRequest = req
                                currentScreen = 1
                            }
                        )
                        3 -> MarketingScreen(
                            tokenStore = tokenStore,
                            translationStore = translationStore,
                            onHeaderTitleChange = { marketingTitleOverride = it },
                            sessionKey = marketingSessionKey,
                            initialOpenHeroImages = pendingMarketingHero,
                            onInitialOpenHeroImagesConsumed = { pendingMarketingHero = false },
                            maxHeight = contentMaxHeight,
                            onEazyChatOpen = onEazyChatOpen,
                            onHeroJobStarted = onHeroJobStarted,
                            onVideoJobStarted = onVideoJobStarted,
                            onHeroEazyReadyChange = { heroEazyReady = it },
                            onVideoEazyReadyChange = { videoEazyReady = it },
                            onVideoGeneratingChange = { videoGenerating = it },
                            heroHeaderStartNonce = heroHeaderStartNonce,
                            videoHeaderStartNonce = videoHeaderStartNonce,
                            onHeroGeneratingChange = { heroGenerating = it },
                            showHeroDockedComposeBar = eazyDocked &&
                                (heroEazyReady || heroGenerating) &&
                                !shopGenerationOverlayActive,
                            heroDockedComposeLoading = heroGenerating,
                            onHeroDockedComposeStart = { heroHeaderStartNonce++ },
                            showVideoDockedComposeBar = eazyDocked &&
                                (videoEazyReady || videoGenerating) &&
                                !shopGenerationOverlayActive,
                            videoDockedComposeLoading = videoGenerating,
                            onVideoDockedComposeStart = { videoHeaderStartNonce++ },
                            onMarketingTabVisibility = { heroVis, videoVis ->
                                marketingHeroTabVisible = heroVis
                                marketingVideoTabVisible = videoVis
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                        4 -> AutomationsScreen(
                            tokenStore = tokenStore,
                            translationStore = translationStore,
                            onHeaderTitleChange = { automationsTitleOverride = it },
                            maxHeight = contentMaxHeight,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    if (guestLockedScreen) {
                        CreatorGuestLockOverlay(
                            translationStore = translationStore,
                            onLoginClick = onAccountClick,
                        )
                    }
                    }
                }
            }

            CreatorFooter(
                localeStore = localeStore,
                tokenStore = tokenStore,
                translationStore = translationStore,
                onLanguageClick = { languageModalVisible = true },
                onTermsClick = { termsModalVisible = true }
            )
        }

        val drawerSlide by animateFloatAsState(
            targetValue = if (drawerVisible) 1f else 0f,
            animationSpec = tween(350, easing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)),
            label = "creatorDrawerSlide",
        )
        val backdropAlpha by animateFloatAsState(
            targetValue = if (drawerVisible) 1f else 0f,
            animationSpec = tween(300, easing = FastOutSlowInEasing),
            label = "creatorDrawerBackdrop",
        )

        if (backdropAlpha > 0.005f || drawerSlide > 0.005f) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val density = LocalDensity.current
                val drawerWidthDpFixed = (maxWidth * 0.85f).coerceAtMost(280.dp)
                val drawerWidthPx = with(density) { drawerWidthDpFixed.toPx() }
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f * backdropAlpha))
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                            ) { drawerVisible = false },
                    )
                    CreatorDrawer(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(drawerWidthDpFixed)
                            .offset {
                                IntOffset(
                                    x = ((-drawerWidthPx) * (1f - drawerSlide)).roundToInt(),
                                    y = 0,
                                )
                            }
                            .align(Alignment.CenterStart),
                        currentScreen = currentScreen,
                        screenLabels = listOf(
                            translationStore.t("creator.mobile.dashboard", "Dashboard"),
                            translationStore.t("creator.mobile.generator", "Generator"),
                            translationStore.t("creator.mobile.creations", "Creations"),
                            translationStore.t("creator.mobile.marketing", "Marketing"),
                            translationStore.t("creator.mobile.automations", "Automations"),
                        ),
                        translationStore = translationStore,
                        onDismiss = { drawerVisible = false },
                        onSwitchToShop = onSwitchToShop,
                        onScreenSelect = { index ->
                            drawerVisible = false
                            currentScreen = index
                        },
                    )
                }
            }
        }
        if (termsModalVisible) {
            TermsModal(
                visible = true,
                baseUrl = "https://www.eazpire.com",
                translationStore = translationStore,
                onDismiss = { termsModalVisible = false },
                isDarkMode = true
            )
        }
        if (salesModalVisible) {
            CreatorSalesModal(
                tokenStore = tokenStore,
                translationStore = translationStore,
                onDismiss = { salesModalVisible = false },
                onLoginClick = onAccountClick,
            )
        }
        if (creatorSettingsVisible) {
            CreatorSettingsModal(
                tokenStore = tokenStore,
                translationStore = translationStore,
                initialTab = if (wearPairTokenForSettings != null) 10 else 0,
                pendingWearPairToken = wearPairTokenForSettings,
                creatorCodeHintActive = creatorCodeHintActive,
                onDismiss = {
                    creatorSettingsVisible = false
                    wearPairTokenForSettings = null
                },
                onLoginClick = onAccountClick,
            )
        }
        if (audioModalVisible) {
            CreatorAudioModal(
                store = audioStore,
                tokenStore = tokenStore,
                translationStore = translationStore,
                onDismiss = { audioModalVisible = false },
                onLoginClick = onAccountClick,
            )
        }
        if (languageModalVisible) {
            val langCode by localeStore.languageCode.collectAsState(initial = "en")
            var languageStandard by remember { mutableStateOf(com.eazpire.creator.ui.header.AVAILABLE_LANGUAGES) }
            var languageChildren by remember { mutableStateOf<Map<String, LanguageChildren>>(emptyMap()) }
            LaunchedEffect(languageModalVisible) {
                if (languageModalVisible) {
                    try {
                        val resp = api.getLanguages()
                        if (resp.standard.isNotEmpty()) {
                            languageStandard = resp.standard.map { LocaleModalItem(it.code, it.label, it.flagCode) }
                            languageChildren = resp.children.mapValues { (_, v) ->
                                LanguageChildren(
                                    dialects = v.dialects.map { LocaleModalItem(it.code, it.label, it.flagCode) },
                                    scripts = v.scripts.map { LocaleModalItem(it.code, it.label, it.flagCode) }
                                )
                            }.mapKeys { it.key.lowercase() }
                        }
                    } catch (_: Exception) { /* keep fallback */ }
                }
            }
            LanguageModal(
                    title = translationStore.t("eaz.topbar.select_language", "Select language"),
                    standardLanguages = languageStandard,
                    languageChildren = languageChildren,
                    selectedCode = langCode,
                    onDismiss = { languageModalVisible = false },
                    onSelect = { code ->
                        scope.launch {
                            localeStore.setLanguageOverride(code)
                        }
                    },
                    searchPlaceholder = translationStore.t("eaz.topbar.search_language", "Search language..."),
                    darkMode = true
                )
        }
    }
}
