package com.eazpire.creator.ui.creator

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.eazpire.creator.R
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.i18n.TranslationStore
import com.eazpire.creator.locale.LocaleStore
import com.eazpire.creator.ui.creator.CreatorDrawer
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
    onGeneratorJobStarted: (jobId: String, summary: String) -> Unit = { _, _ -> },
    /** True while on Generator (screen 1) and prompt/refs ready – drives floating Eazy lookLeft in ShopScreen */
    onGeneratorEazyLookLeftChange: (Boolean) -> Unit = {},
    eazyDocked: Boolean = false,
    eazySnapModeActive: Boolean = false,
    onEazySnapModeChange: (Boolean) -> Unit = {},
    onEazyLongPress: () -> Unit = {},
    slotBoundsState: androidx.compose.runtime.MutableState<Rect?>? = null,
    modifier: Modifier = Modifier
) {
    var drawerVisible by remember { mutableStateOf(false) }
    var salesModalVisible by remember { mutableStateOf(false) }
    var creatorSettingsVisible by remember { mutableStateOf(false) }
    var audioModalVisible by remember { mutableStateOf(false) }
    var languageModalVisible by remember { mutableStateOf(false) }
    var termsModalVisible by remember { mutableStateOf(false) }
    var marketingTitleOverride by remember { mutableStateOf<String?>(null) }
    var marketingSessionKey by remember { mutableIntStateOf(0) }
    val audioStore = remember { com.eazpire.creator.audio.CreatorAudioStore() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val api = remember { CreatorApi(jwt = tokenStore.getJwt()) }
    val ownerId = remember(tokenStore) { tokenStore.getOwnerId() ?: "" }
    var currentScreen by remember { mutableIntStateOf(0) }
    var generatorEazyReady by remember { mutableStateOf(false) }
    var heroEazyReady by remember { mutableStateOf(false) }
    var genHeaderStartNonce by remember { mutableIntStateOf(0) }
    var heroHeaderStartNonce by remember { mutableIntStateOf(0) }
    var generatorGenerating by remember { mutableStateOf(false) }
    var heroGenerating by remember { mutableStateOf(false) }

    LaunchedEffect(currentScreen, generatorEazyReady, heroEazyReady) {
        val look =
            (currentScreen == 1 && generatorEazyReady) || (currentScreen == 3 && heroEazyReady)
        onGeneratorEazyLookLeftChange(look)
    }

    LaunchedEffect(currentScreen) {
        if (currentScreen != 1) generatorGenerating = false
        if (currentScreen != 3) heroGenerating = false
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
                    audioStore.play(item)
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
                        translationStore.t("creator.mobile.marketing", "Marketing")
                    ),
                    translationStore = translationStore,
                    onMenuClick = { drawerVisible = true },
                    onBalanceClick = { salesModalVisible = true },
                    onAccountClick = { creatorSettingsVisible = true },
                    tokenStore = tokenStore,
                    eazyDocked = eazyDocked,
                    eazySnapModeActive = eazySnapModeActive,
                    onEazyClick = { onEazyChatOpen(null) },
                    onEazyLongPress = onEazyLongPress,
                    slotBoundsState = slotBoundsState,
                    audioStore = audioStore,
                    onAudioModalOpen = { audioModalVisible = true },
                    marketingTitleOverride = marketingTitleOverride,
                    eazyLookLeft = (currentScreen == 1 && generatorEazyReady) ||
                        (currentScreen == 3 && heroEazyReady),
                    showStartGenerationBubble = (currentScreen == 1 && generatorEazyReady) ||
                        (currentScreen == 3 && heroEazyReady),
                    startGenerationLoading = (currentScreen == 1 && generatorGenerating) ||
                        (currentScreen == 3 && heroGenerating),
                    onStartGenerationClick = {
                        when (currentScreen) {
                            1 -> genHeaderStartNonce++
                            3 -> heroHeaderStartNonce++
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
                                        dragX <= -120f -> currentScreen = (currentScreen + 1).coerceAtMost(3)
                                        dragX >= 120f -> currentScreen = (currentScreen - 1).coerceAtLeast(0)
                                    }
                                    dragX = 0f
                                }
                            )
                        }
                ) {
                    when (currentScreen) {
                        0 -> CreatorDashboardScreen(
                            tokenStore = tokenStore,
                            translationStore = translationStore,
                            onOpenSalesModal = { salesModalVisible = true },
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
                            maxHeight = contentMaxHeight,
                            modifier = Modifier.fillMaxSize()
                        )
                        2 -> CreatorCreationsScreen(
                            tokenStore = tokenStore,
                            translationStore = translationStore,
                            maxHeight = contentMaxHeight,
                            modifier = Modifier.fillMaxSize()
                        )
                        else -> MarketingScreen(
                            tokenStore = tokenStore,
                            translationStore = translationStore,
                            onHeaderTitleChange = { marketingTitleOverride = it },
                            sessionKey = marketingSessionKey,
                            maxHeight = contentMaxHeight,
                            onEazyChatOpen = onEazyChatOpen,
                            onHeroJobStarted = onHeroJobStarted,
                            onHeroEazyReadyChange = { heroEazyReady = it },
                            heroHeaderStartNonce = heroHeaderStartNonce,
                            onHeroGeneratingChange = { heroGenerating = it },
                            modifier = Modifier.fillMaxSize()
                        )
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

        AnimatedVisibility(
            visible = drawerVisible,
            enter = fadeIn() + slideInHorizontally(initialOffsetX = { -it }),
            exit = fadeOut() + slideOutHorizontally(targetOffsetX = { -it })
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { drawerVisible = false },
                contentAlignment = Alignment.CenterStart
            ) {
                CreatorDrawer(
                    visible = true,
                    currentScreen = currentScreen,
                    screenLabels = listOf(
                        translationStore.t("creator.mobile.dashboard", "Dashboard"),
                        translationStore.t("creator.mobile.generator", "Generator"),
                        translationStore.t("creator.mobile.creations", "Creations"),
                        translationStore.t("creator.mobile.marketing", "Marketing")
                    ),
                    onDismiss = { drawerVisible = false },
                    onSwitchToShop = onSwitchToShop,
                    onScreenSelect = { index ->
                        drawerVisible = false
                        currentScreen = index
                    }
                )
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
                onDismiss = { salesModalVisible = false }
            )
        }
        if (creatorSettingsVisible) {
            CreatorSettingsModal(
                tokenStore = tokenStore,
                translationStore = translationStore,
                onDismiss = { creatorSettingsVisible = false }
            )
        }
        if (audioModalVisible) {
            CreatorAudioModal(
                store = audioStore,
                tokenStore = tokenStore,
                translationStore = translationStore,
                onDismiss = { audioModalVisible = false }
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
