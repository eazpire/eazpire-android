package com.eazpire.creator.ui

import android.app.Activity
import android.graphics.Color as AndroidColor
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.auth.ShopSessionGuard
import com.eazpire.creator.push.PushTokenRegistrar
import com.eazpire.creator.debug.debugLog
import com.eazpire.creator.debug.langDebug
import com.eazpire.creator.i18n.LocalTranslationStore
import com.eazpire.creator.i18n.TranslationStore
import com.eazpire.creator.locale.LocaleStore
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.chat.EazyChatContext
import com.eazpire.creator.chat.EazyChatModal
import com.eazpire.creator.chat.EazyChatStore
import com.eazpire.creator.chat.EazyMascot
import com.eazpire.creator.chat.EazySidebarTab
import com.eazpire.creator.chat.EazyMascotStore
import com.eazpire.creator.ui.account.AccountModalSheet
import com.eazpire.creator.ui.footer.GlobalFooter
import com.eazpire.creator.ui.footer.SubFooter
import com.eazpire.creator.ui.footer.TermsModal
import com.eazpire.creator.ui.creator.CreatorHeaderEazyStartBubble
import com.eazpire.creator.ui.creator.CreatorMainScreen
import com.eazpire.creator.ui.header.CollectionBreadcrumb
import com.eazpire.creator.ui.header.MainHeader
import com.eazpire.creator.ui.header.MenuDrawer
import com.eazpire.creator.ui.header.SHOP_MENU_CREATE_HANDLE
import com.eazpire.creator.ui.header.ShopMenuBar
import com.eazpire.creator.ui.vouchers.VoucherModal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Shop-Screen: Direkt zugänglich ohne Login.
 * Zeigt MainHeader und Platzhalter-Content (native UI).
 */
private val COLLECTION_HANDLE_TO_TITLE = mapOf(
    "women" to "Women", "men" to "Men", "kids" to "Kids",
    "toddler" to "Toddler", "home-living" to "Home & Living"
)

@Composable
fun ShopScreen(
    tokenStore: SecureTokenStore,
    pendingDeepLink: MutableState<android.net.Uri?>? = null,
    pendingEazyTab: MutableState<EazySidebarTab?>? = null,
    pendingOpenCart: MutableState<Boolean>? = null,
    pendingOpenShop: MutableState<Boolean>? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val localeStore = remember { LocaleStore(context) }
    val translationStore = remember { TranslationStore(context) }
    val languageCode by localeStore.languageCode.collectAsState(initial = java.util.Locale.getDefault().language.lowercase())
    val catalogRegion by localeStore.regionCode.collectAsState(initial = "EU")

    var sessionEpoch by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        ShopSessionGuard.refreshAccessTokenIfNeeded(context, tokenStore)
        ShopSessionGuard.validateLegacyShopifySessionIfNeeded(context, tokenStore)
        PushTokenRegistrar.syncIfLoggedIn(context)
        sessionEpoch++
    }

    LaunchedEffect(languageCode) {
        // #region agent log
        langDebug("ShopScreen.kt:LaunchedEffect", "languageCode changed, loading", mapOf("languageCode" to languageCode), "H3")
        // #endregion
        translationStore.load(languageCode)
    }

    CompositionLocalProvider(LocalTranslationStore provides translationStore) {
    key(sessionEpoch) {
    // Recomposition trigger: when translations load, UI must update
    val translations by translationStore.translations.collectAsState(initial = emptyMap())
    // #region agent log
    LaunchedEffect(translations) {
        langDebug("ShopScreen.kt:translations", "translations updated", mapOf("count" to translations.size, "sample" to translations.keys.take(3).toString()), "H5")
    }
    // #endregion
    var accountModalVisible by remember { mutableStateOf(false) }
    var showLoginOptions by remember { mutableStateOf(false) }
    var showAuthScreen by remember { mutableStateOf(false) }
    /** OAuth redirect shop.*://callback?code=… delivered via MainActivity intent → token exchange in AuthScreen */
    val oauthCallbackForAuth = remember { mutableStateOf<String?>(null) }
    var menuDrawerVisible by remember { mutableStateOf(false) }
    var cartDrawerVisible by remember { mutableStateOf(false) }
    var favoritesModalVisible by remember { mutableStateOf(false) }
    var eazyChatVisible by remember { mutableStateOf(false) }
    var eazyStartTab by remember { mutableStateOf(EazySidebarTab.Chat) }

    val eazyChatStore = remember { EazyChatStore(context) }
    val pollJwt = tokenStore.getJwt()
    val creatorPollApi = remember(pollJwt) { CreatorApi(jwt = pollJwt) }
    val heroJobForPoll by eazyChatStore.heroJobState.collectAsState()
    val videoJobForPoll by eazyChatStore.videoJobState.collectAsState()

    LaunchedEffect(heroJobForPoll?.jobId) {
        val jobId = heroJobForPoll?.jobId ?: return@LaunchedEffect
        if (heroJobForPoll?.terminal == true) return@LaunchedEffect
        while (isActive) {
            try {
                val r = withContext(Dispatchers.IO) { creatorPollApi.pollJob(jobId) }
                val done = r.optBoolean("done")
                val notFound = r.optBoolean("not_found")
                if (!done) {
                    val progress = r.optInt("progress", 0)
                    val msg = r.optString("message", "").takeIf { it.isNotBlank() }
                    eazyChatStore.updateHeroJobPoll(progress, msg)
                    delay(2000)
                    continue
                }
                if (notFound) {
                    eazyChatStore.failHeroJob(
                        r.optString("message", "").takeIf { it.isNotBlank() } ?: "Job not found"
                    )
                } else {
                    val img = r.optJSONObject("result")?.optString("image_url", "")?.takeIf { it.isNotBlank() }
                    if (img != null) {
                        eazyChatStore.completeHeroJob(img)
                        eazyStartTab = EazySidebarTab.Notifications
                    } else {
                        eazyChatStore.failHeroJob(
                            r.optString("message", "").takeIf { it.isNotBlank() }
                                ?: "No image in result"
                        )
                    }
                }
                break
            } catch (_: Exception) {
                delay(3000)
            }
        }
    }

    LaunchedEffect(videoJobForPoll?.jobId) {
        val jobId = videoJobForPoll?.jobId ?: return@LaunchedEffect
        if (videoJobForPoll?.terminal == true) return@LaunchedEffect
        while (isActive) {
            try {
                val r = withContext(Dispatchers.IO) { creatorPollApi.pollJob(jobId) }
                val done = r.optBoolean("done")
                val notFound = r.optBoolean("not_found")
                if (!done) {
                    val progress = r.optInt("progress", 0)
                    val msg = r.optString("message", "").takeIf { it.isNotBlank() }
                    eazyChatStore.updateVideoJobPoll(progress, msg)
                    delay(2000)
                    continue
                }
                if (notFound) {
                    eazyChatStore.failVideoJob(
                        r.optString("message", "").takeIf { it.isNotBlank() } ?: "Job not found"
                    )
                } else {
                    val result = r.optJSONObject("result")
                    val vid = result?.optString("video_url", "")?.takeIf { it.isNotBlank() }
                    if (vid != null) {
                        eazyChatStore.completeVideoJob(vid)
                        eazyStartTab = EazySidebarTab.Notifications
                    } else {
                        eazyChatStore.failVideoJob(
                            r.optString("message", "").takeIf { it.isNotBlank() }
                                ?: "No video in result"
                        )
                    }
                }
                break
            } catch (_: Exception) {
                delay(3000)
            }
        }
    }

    val eazyMascotStore = remember { EazyMascotStore(context) }
    val eazyDocked by eazyMascotStore.isDocked.collectAsState(initial = false)
    val eazyPosX by eazyMascotStore.positionX.collectAsState(initial = null)
    val eazyPosY by eazyMascotStore.positionY.collectAsState(initial = null)
    var eazySnapModeActive by remember { mutableStateOf(false) }
    val slotBoundsState = remember { mutableStateOf<Rect?>(null) }
    val scope = rememberCoroutineScope()
    var currentPagePath by remember { mutableStateOf("/") }
    var scrollToTopTrigger by remember { mutableStateOf(0) }
    var selectedCollection by remember { mutableStateOf<Triple<String, String, String?>?>(null) }
    var shopSearchQuery by remember { mutableStateOf<String?>(null) }
    var shopCreateProductVisible by remember { mutableStateOf(false) }
    var selectedProductHandle by remember { mutableStateOf<String?>(null) }
    val productModalHandleState = remember { mutableStateOf<String?>(null) }
    var isCreatorMode by remember { mutableStateOf(false) }

    LaunchedEffect(pendingEazyTab?.value, pendingOpenCart?.value, pendingOpenShop?.value) {
        val pt = pendingEazyTab
        if (pt?.value != null) {
            eazyStartTab = pt.value!!
            eazyChatVisible = true
            pt.value = null
        }
        val pc = pendingOpenCart
        if (pc?.value == true) {
            cartDrawerVisible = true
            pc.value = false
        }
        val ps = pendingOpenShop
        if (ps?.value == true) {
            isCreatorMode = false
            eazyChatVisible = false
            ps.value = false
        }
    }

    var creatorGenEazyLookLeft by remember { mutableStateOf(false) }
    var eazyGenerationOverlay by remember { mutableStateOf(false) }
    var eazyGenerationOverlayLoading by remember { mutableStateOf(false) }
    /** When set, header docked Eazy faces toward ShopScreen generation bubble (snapped + input). */
    var generationBubbleFaceLeft by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(isCreatorMode, eazyGenerationOverlay) {
        if (!isCreatorMode || !eazyGenerationOverlay) {
            generationBubbleFaceLeft = null
        }
    }
    var overlayComposeStartKey by remember { mutableIntStateOf(0) }
    var termsModalVisible by remember { mutableStateOf(false) }
    var voucherModalVisible by remember { mutableStateOf(false) }

    val ownerId = remember(tokenStore) { tokenStore.getOwnerId() ?: "" }
    val eazySyncApi = remember(tokenStore) { CreatorApi(jwt = tokenStore.getJwt()) }

    LaunchedEffect(ownerId) {
        if (ownerId.isBlank()) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            eazyMascotStore.mergeFromRemoteIfEmpty(eazySyncApi, ownerId)
        }
    }

    LaunchedEffect(ownerId, eazyDocked, eazyPosX, eazyPosY) {
        if (ownerId.isBlank()) return@LaunchedEffect
        delay(2500)
        withContext(Dispatchers.IO) {
            eazyMascotStore.pushToRemote(eazySyncApi, ownerId)
        }
    }

    // Creator: StatusBar + NavBar dunkel (#0A0514), ohne Kontrastlinie; Shop: Orange
    LaunchedEffect(isCreatorMode) {
        val activity = context as? Activity
        activity?.window?.let { window ->
            if (isCreatorMode) {
                window.statusBarColor = AndroidColor.parseColor("#0A0514")
                window.navigationBarColor = AndroidColor.parseColor("#0A0514")
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    window.isNavigationBarContrastEnforced = false
                }
            } else {
                window.statusBarColor = AndroidColor.parseColor("#F97316")
                window.navigationBarColor = AndroidColor.parseColor("#F97316")
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    window.isNavigationBarContrastEnforced = true
                }
            }
        }
    }

    LaunchedEffect(productModalHandleState.value) {
        // #region agent log
        debugLog("ShopScreen.kt:59", "LaunchedEffect productModalHandleState changed", mapOf("value" to productModalHandleState.value), "H2")
        // #endregion
        Log.d("ProductModalDebug", "[5] ShopScreen: productModalHandleState changed to ${productModalHandleState.value}")
    }

    LaunchedEffect(isCreatorMode) {
        if (isCreatorMode) slotBoundsState.value = null
        if (!isCreatorMode) creatorGenEazyLookLeft = false
    }

    LaunchedEffect(pendingDeepLink?.value) {
        val uri = pendingDeepLink?.value ?: return@LaunchedEffect
        // Shopify OAuth callback (e.g. after Google sign-in in Chrome Custom Tab)
        if (uri.scheme?.startsWith("shop.") == true && uri.host == "callback" &&
            uri.getQueryParameter("code") != null
        ) {
            oauthCallbackForAuth.value = uri.toString()
            pendingDeepLink.value = null
            showAuthScreen = true
            return@LaunchedEffect
        }
        pendingDeepLink.value = null
        val path = when (uri.host) {
            "join.eazpire.com" -> {
                val urlParam = uri.getQueryParameter("url")
                urlParam?.let { java.net.URLDecoder.decode(it, "UTF-8") }
                    ?.substringAfter("www.eazpire.com")
                    ?.substringAfter("eazpire.com")
                    ?: "/"
            }
            "www.eazpire.com", "eazpire.com" -> uri.path ?: "/"
            else -> uri.path ?: "/"
        }
        when {
            path.startsWith("/products/") -> {
                val handle = path.removePrefix("/products/").trimEnd('/').substringBefore("?")
                if (handle.isNotBlank()) selectedProductHandle = handle
            }
            path.startsWith("/collections/") -> {
                val handle = path.removePrefix("/collections/").trimEnd('/').substringBefore("?")
                if (handle.isNotBlank()) {
                    val title = COLLECTION_HANDLE_TO_TITLE[handle] ?: handle.replaceFirstChar { it.uppercase() }
                    selectedCollection = Triple(title, handle, null)
                }
            }
            path.startsWith("/pages/creator-dashboard") || path == "/pages/creator" -> isCreatorMode = true
            path.startsWith("/pages/design-generator") -> isCreatorMode = true
            path.startsWith("/search") -> {
                val q = uri.getQueryParameter("q")?.trim().orEmpty()
                if (q.isNotEmpty()) {
                    selectedCollection = null
                    selectedProductHandle = null
                    shopSearchQuery = q
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
    if (isCreatorMode) {
        key("creator") {
        CreatorMainScreen(
            tokenStore = tokenStore,
            localeStore = localeStore,
            translationStore = translationStore,
            onSwitchToShop = { isCreatorMode = false },
            onAccountClick = {
                if (tokenStore.isLoggedIn()) {
                    accountModalVisible = true
                } else {
                    showLoginOptions = true
                }
            },
            onEazyChatOpen = { tab ->
                eazyStartTab = tab ?: EazySidebarTab.Chat
                eazyChatVisible = true
            },
            onHeroJobStarted = { id, summary ->
                eazyChatStore.startHeroJob(id, summary)
                eazyStartTab = EazySidebarTab.Jobs
            },
            onVideoJobStarted = { id, summary ->
                eazyChatStore.startVideoJob(id, summary)
                eazyStartTab = EazySidebarTab.Jobs
            },
            onGeneratorJobStarted = { id, summary ->
                eazyChatStore.startHeroJob(id, summary)
                eazyStartTab = EazySidebarTab.Jobs
            },
            onGeneratorEazyLookLeftChange = { creatorGenEazyLookLeft = it },
            eazyDocked = eazyDocked,
            eazySnapModeActive = eazySnapModeActive,
            onEazySnapModeChange = { eazySnapModeActive = it },
            onEazyLongPress = { eazyMascotStore.setDockedSync(false) },
            slotBoundsState = slotBoundsState,
            onEazyGenerationOverlayChange = { visible, loading ->
                eazyGenerationOverlay = visible
                eazyGenerationOverlayLoading = loading
            },
            shopGenerationOverlayActive = eazyGenerationOverlay,
            overlayComposeStartKey = overlayComposeStartKey,
            generationBubbleFaceLeft = generationBubbleFaceLeft
        )
        }
    } else {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            key(languageCode, translations.size) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { focusManager.clearFocus() }
            ) {
                MainHeader(
                    localeStore = localeStore,
                    tokenStore = tokenStore,
                    currentPagePath = selectedCollection?.let { "/collections/${it.second}" } ?: currentPagePath,
                    cartDrawerVisibleControl = cartDrawerVisible,
                    onCartDrawerChange = { cartDrawerVisible = it },
                    favoritesModalVisibleControl = favoritesModalVisible,
                    onFavoritesModalChange = { favoritesModalVisible = it },
                    eazyDocked = eazyDocked,
                    eazySnapModeActive = eazySnapModeActive,
                    onEazyClick = {
                        eazyStartTab = EazySidebarTab.Chat
                        eazyChatVisible = true
                    },
                    onEazyLongPress = { eazyMascotStore.setDockedSync(false) },
                    slotBoundsState = slotBoundsState,
                    isCreatorMode = isCreatorMode,
                    onCreatorModeChange = { isCreatorMode = it },
                    onLogoClick = {
                        accountModalVisible = false
                        showLoginOptions = false
                        menuDrawerVisible = false
                        showAuthScreen = false
                        selectedCollection = null
                        shopSearchQuery = null
                        selectedProductHandle = null
                        productModalHandleState.value = null
                        scrollToTopTrigger++
                    },
                    onAccountClick = {
                        if (tokenStore.isLoggedIn()) {
                            accountModalVisible = true
                        } else {
                            showLoginOptions = true
                        }
                    },
                    onSearchNavigate = { url ->
                        val uri = Uri.parse(url)
                        val path = uri.path ?: ""
                        when {
                            path.startsWith("/products/") -> {
                                val handle = path.removePrefix("/products/").trimEnd('/').substringBefore("?")
                                if (handle.isNotBlank()) {
                                    shopSearchQuery = null
                                    selectedProductHandle = handle
                                }
                            }
                            path.startsWith("/search") -> {
                                val q = uri.getQueryParameter("q")?.trim().orEmpty()
                                if (q.isNotEmpty()) {
                                    selectedCollection = null
                                    selectedProductHandle = null
                                    productModalHandleState.value = null
                                    shopSearchQuery = q
                                } else {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                }
                            }
                            else -> {
                                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                            }
                        }
                    },
                    onSearchQuerySubmit = { q ->
                        val t = q.trim()
                        if (t.isNotEmpty()) {
                            selectedCollection = null
                            selectedProductHandle = null
                            productModalHandleState.value = null
                            shopSearchQuery = t
                        }
                    }
                )
                ShopMenuBar(
                    onAllClick = {
                        when {
                            selectedProductHandle != null -> menuDrawerVisible = true
                            shopSearchQuery != null -> shopSearchQuery = null
                            selectedCollection != null -> selectedCollection = null
                            else -> menuDrawerVisible = true
                        }
                    },
                    onCategoryClick = { title, handle ->
                        if (handle == SHOP_MENU_CREATE_HANDLE) {
                            shopCreateProductVisible = true
                        } else {
                            shopSearchQuery = null
                            selectedProductHandle = null
                            selectedCollection = Triple(title, handle, null)
                        }
                    },
                    selectedHandle = selectedCollection?.second
                )
                if (selectedCollection != null || selectedProductHandle != null) {
                    CollectionBreadcrumb(
                        categoryTitle = selectedCollection?.first ?: "",
                        onHomeClick = {
                            selectedCollection = null
                            selectedProductHandle = null
                        },
                        productTitle = null,
                        onCollectionClick = if (selectedProductHandle != null && selectedCollection != null) {
                            { selectedProductHandle = null }
                        } else null
                    )
                }
            }
            }
        },
        bottomBar = {
            if (selectedProductHandle == null) {
                key(languageCode, translations.size) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SubFooter(
                            localeStore = localeStore,
                            translationStore = translationStore,
                            tokenStore = tokenStore,
                            onWalletClick = { voucherModalVisible = true }
                        )
                        GlobalFooter(onTermsClick = { termsModalVisible = true })
                    }
                }
            }
        }
    ) { padding ->
        key(languageCode, translations.size) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                selectedProductHandle != null -> ProductDetailScreen(
                    productHandle = selectedProductHandle!!,
                    onBack = { selectedProductHandle = null },
                    tokenStore = tokenStore,
                    onTermsClick = { termsModalVisible = true },
                    onNavigateToProduct = { selectedProductHandle = it }
                )
                shopSearchQuery != null -> ShopSearchScreen(
                    searchQuery = shopSearchQuery!!,
                    onBack = { shopSearchQuery = null },
                    onProductClick = { p ->
                        selectedProductHandle = p.handle
                    }
                )
                selectedCollection != null -> {
                    val (title, handle, productType) = selectedCollection!!
                    CollectionScreen(
                        title = title,
                        collectionHandle = handle,
                        initialProductType = productType,
                        onBack = { selectedCollection = null },
                        onProductClick = { selectedProductHandle = it.handle }
                    )
                }
                else -> ProductCarouselSection(
                    modifier = Modifier.fillMaxSize(),
                    onCurrentPageChange = { currentPagePath = it },
                    onCategoryClick = { title, h -> selectedCollection = Triple(title, h, null) },
                    onProductClick = { params ->
                        shopSearchQuery = null
                        selectedProductHandle = params.handle
                        if (params.collectionTitle != null && params.collectionHandle != null) {
                            selectedCollection = Triple(params.collectionTitle, params.collectionHandle, null)
                        }
                    },
                    productModalHandleState = productModalHandleState,
                    scrollToTopTrigger = scrollToTopTrigger
                )
            }
        }
        }
    }
    }
    if (!isCreatorMode) {
        ShopCreateProductFlow(
            visible = shopCreateProductVisible,
            onDismiss = { shopCreateProductVisible = false },
            api = creatorPollApi,
            tokenStore = tokenStore,
            region = catalogRegion,
            translationStore = translationStore,
            translation = { k, d -> translationStore.t(k, d) },
            onRequireLogin = {
                shopCreateProductVisible = false
                showLoginOptions = true
            }
        )
    }
    val showGenOverlay = isCreatorMode && eazyGenerationOverlay
    // Full-screen zIndex layer must not cover product detail or product modal — it would steal touches (cart / buy now).
    val showEazyFloatingLayer =
        (!eazyDocked || showGenOverlay) && selectedProductHandle == null && productModalHandleState.value == null
    if (showEazyFloatingLayer) {
        var liveMascotX by remember { mutableStateOf<Float?>(null) }
        var liveMascotY by remember { mutableStateOf<Float?>(null) }
        LaunchedEffect(eazyDocked, showGenOverlay) {
            if (eazyDocked && !showGenOverlay) {
                liveMascotX = null
                liveMascotY = null
            }
        }
        val contentBoundsState = remember { mutableStateOf<Rect?>(null) }
        /** Same coordinate space as EazyMascot offset (inside navigationBarsPadding); required for snap distance vs header slot */
        val mascotLayerBoundsState = remember { mutableStateOf<Rect?>(null) }
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(100f)
                .onGloballyPositioned { contentBoundsState.value = it.boundsInRoot() }
        ) {
            val density = LocalDensity.current
            val contentW = with(density) { maxWidth.toPx() }
            val contentH = with(density) { maxHeight.toPx() }
            val bubbleLabel = translationStore.t(
                "creator.generator_eazy.bubble_start",
                "Start generation"
            )
            val mascotSizePx = with(density) { 48.dp.toPx() }
            val maxXMascot = (contentW - mascotSizePx).coerceAtLeast(0f)
            val maxYMascot = (contentH - mascotSizePx).coerceAtLeast(0f)
            val defaultXMascot = contentW - mascotSizePx - 32f
            val defaultYMascot = contentH - mascotSizePx - 100f
            val slot = slotBoundsState.value
            val contentB = contentBoundsState.value
            val mascotLayerB = mascotLayerBoundsState.value
            /** Prefer layer that matches EazyMascot parent; avoids wrong snap math when padding shifts origin */
            val relB = mascotLayerB ?: contentB
            val overlayDockedMascot = eazyDocked && showGenOverlay
            /** Slot center in mascot layer space (same as snap / docked overlay) */
            val slotRawX = if (eazyDocked && slot != null && relB != null) {
                val cx = slot.left - relB.left + slot.width / 2f
                (cx - mascotSizePx / 2f).coerceIn(0f, maxXMascot)
            } else {
                null
            }
            val slotRawY = if (eazyDocked && slot != null && relB != null) {
                val cy = slot.top - relB.top + slot.height / 2f
                (cy - mascotSizePx / 2f).coerceIn(0f, maxYMascot)
            } else {
                null
            }
            /**
             * Same source as [EazyMascot] position props. Do not use [liveMascotX] here when undocked:
             * after undock the mascot jumps to [eazyPosX]/[eazyPosY] but live* could still be the old
             * slot position → bubble would stay in the header without the mascot.
             */
            val mascotPosX: Float? = when {
                overlayDockedMascot && liveMascotX != null -> liveMascotX
                overlayDockedMascot && slotRawX != null -> slotRawX
                overlayDockedMascot -> defaultXMascot
                else -> eazyPosX
            }
            val mascotPosY: Float? = when {
                overlayDockedMascot && liveMascotY != null -> liveMascotY
                overlayDockedMascot && slotRawY != null -> slotRawY
                overlayDockedMascot -> defaultYMascot
                else -> eazyPosY
            }
            /** Match EazyMascot internal rawX/rawY so the bubble tracks the visible mascot */
            val bubbleAnchorX = when {
                mascotPosX == null || mascotPosX.isNaN() -> defaultXMascot
                else -> mascotPosX.coerceIn(0f, maxXMascot)
            }
            val bubbleAnchorY = when {
                mascotPosY == null || mascotPosY.isNaN() -> defaultYMascot
                else -> mascotPosY.coerceIn(0f, maxYMascot)
            }
            val halfPx = contentW / 2f
            val bubbleLeftOfEazy = bubbleAnchorX + mascotSizePx / 2f >= halfPx
            val spacerPx = with(density) { 6.dp.toPx() }
            val bubbleRowWidthPx = with(density) { 160.dp.toPx() }
            val bubbleHeightPx = with(density) { 40.dp.toPx() }
            val bubbleLeftPx = if (bubbleLeftOfEazy) {
                bubbleAnchorX - spacerPx - bubbleRowWidthPx
            } else {
                bubbleAnchorX + mascotSizePx + spacerPx
            }
            val bubbleTopPx = bubbleAnchorY + (mascotSizePx - bubbleHeightPx) / 2f
            val bubbleLeftClamped = bubbleLeftPx.coerceIn(
                0f,
                (contentW - bubbleRowWidthPx).coerceAtLeast(0f)
            )
            val bubbleCenterX = bubbleLeftClamped + bubbleRowWidthPx / 2f
            val mascotCenterX = bubbleAnchorX + mascotSizePx / 2f
            val faceTowardBubbleLeft = bubbleCenterX < mascotCenterX
            SideEffect {
                if (showGenOverlay) {
                    generationBubbleFaceLeft = faceTowardBubbleLeft
                } else {
                    generationBubbleFaceLeft = null
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .onGloballyPositioned { mascotLayerBoundsState.value = it.boundsInRoot() }
            ) {
                if (!eazyDocked || showGenOverlay) {
                    EazyMascot(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .zIndex(100f),
                        isDocked = eazyDocked && !showGenOverlay,
                        positionX = mascotPosX,
                        positionY = mascotPosY,
                        onPositionChange = { x, y -> eazyMascotStore.setPositionSync(x, y) },
                        onDockedChange = { eazyMascotStore.setDockedSync(it) },
                        onOpenChat = { eazyChatVisible = true },
                        slotBoundsInRoot = slotBoundsState.value,
                        onSnapModeChange = { eazySnapModeActive = it },
                        scope = scope,
                        contentWidthPx = contentW,
                        contentHeightPx = contentH,
                        contentBoundsInRoot = mascotLayerBoundsState.value ?: contentBoundsState.value,
                        lookLeft = if (showGenOverlay) faceTowardBubbleLeft else creatorGenEazyLookLeft,
                        autoFaceFromScreenHalf = isCreatorMode && !showGenOverlay,
                        onVisualPositionChange = { x, y ->
                            liveMascotX = x
                            liveMascotY = y
                        }
                    )
                }
                if (showGenOverlay) {
                    CreatorHeaderEazyStartBubble(
                        label = bubbleLabel,
                        loading = eazyGenerationOverlayLoading,
                        enabled = !eazyGenerationOverlayLoading,
                        onClick = {
                            if (!eazyGenerationOverlayLoading) overlayComposeStartKey++
                        },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset {
                                IntOffset(
                                    bubbleLeftClamped.roundToInt(),
                                    bubbleTopPx.roundToInt().coerceAtLeast(0)
                                )
                            }
                            .zIndex(101f),
                        tailTowardEnd = bubbleLeftOfEazy
                    )
                }
            }
        }
    }
    }

    EazyChatModal(
        visible = eazyChatVisible,
        tokenStore = tokenStore,
        chatStore = eazyChatStore,
        onDismiss = { eazyChatVisible = false },
        onLoginClick = {
            eazyChatVisible = false
            showLoginOptions = true
        },
        onResetMascot = { eazyMascotStore.resetSync() },
        chatContext = if (isCreatorMode) EazyChatContext.Creator else EazyChatContext.Shop,
        startTab = eazyStartTab
    )

    MenuDrawer(
        visible = menuDrawerVisible,
        translationStore = translationStore,
        tokenStore = tokenStore,
        cartCount = com.eazpire.creator.cart.AppCartStore.itemCount,
        onDismiss = { menuDrawerVisible = false },
        onCategoryClick = { title, handle, productType ->
            menuDrawerVisible = false
            if (handle == SHOP_MENU_CREATE_HANDLE) {
                shopCreateProductVisible = true
            } else {
                selectedProductHandle = null
                selectedCollection = Triple(title, handle, productType)
            }
        },
        onExternalUrl = { url ->
            menuDrawerVisible = false
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (_: Exception) {}
        },
        onHomeClick = {
            menuDrawerVisible = false
            selectedCollection = null
            selectedProductHandle = null
        },
        onCartClick = {
            menuDrawerVisible = false
            cartDrawerVisible = true
        },
        onFavoritesClick = {
            menuDrawerVisible = false
            favoritesModalVisible = true
        },
        onAccountClick = {
            menuDrawerVisible = false
            accountModalVisible = true
        },
        onVouchersClick = { voucherModalVisible = true }
    )

    VoucherModal(
        visible = voucherModalVisible,
        onDismiss = { voucherModalVisible = false },
        tokenStore = tokenStore,
        translationStore = translationStore
    )

    if (termsModalVisible) {
        TermsModal(
            visible = true,
            baseUrl = "https://allyoucanpink.com",
            translationStore = translationStore,
            onDismiss = { termsModalVisible = false }
        )
    }

    if (accountModalVisible) {
        AccountModalSheet(
            tokenStore = tokenStore,
            onDismiss = { accountModalVisible = false }
        )
    }

    if (showLoginOptions) {
        LoginOptionsModal(
            onDismiss = { showLoginOptions = false },
            onShopifyLoginClick = {
                showLoginOptions = false
                showAuthScreen = true
            }
        )
    }

    if (showAuthScreen) {
        Box(modifier = Modifier.fillMaxSize()) {
            AuthScreen(
                tokenStore = tokenStore,
                onAuthSuccess = { showAuthScreen = false },
                oauthCallbackUri = oauthCallbackForAuth
            )
        }
    }

    val modalHandle = productModalHandleState.value
    // #region agent log
    debugLog("ShopScreen.kt:232", "ShopScreen rendering modal block", mapOf("modalHandle" to modalHandle), "H2")
    // #endregion
    Log.d("ProductModalDebug", "[6] ShopScreen: rendering modal block, modalHandle=$modalHandle")
    if (modalHandle != null) {
        // #region agent log
        debugLog("ShopScreen.kt:236", "ShopScreen composing ProductModal", mapOf("handle" to modalHandle), "H3")
        // #endregion
        Log.d("ProductModalDebug", "[7] ShopScreen: composing ProductModal with handle=$modalHandle")
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(Float.MAX_VALUE)
        ) {
            ProductModal(
                productHandle = modalHandle,
                onDismiss = { productModalHandleState.value = null },
                tokenStore = tokenStore,
                onTermsClick = { termsModalVisible = true }
            )
        }
    }
    }
    }
}
