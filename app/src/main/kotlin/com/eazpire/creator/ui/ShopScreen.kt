package com.eazpire.creator.ui

import android.app.Activity
import android.graphics.Color as AndroidColor
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.eazpire.creator.MainActivity
import com.eazpire.creator.ar.poster.PosterArOverlay
import com.eazpire.creator.ar.poster.PosterArSessionConfig
import com.eazpire.creator.auth.AuthLoginMethod
import com.eazpire.creator.auth.OAuthPkceStore
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.auth.ShopSessionCoordinator
import com.eazpire.creator.auth.ShopSessionGuard
import com.eazpire.creator.debug.AuthDebugLog
import com.eazpire.creator.debug.debugLog
import com.eazpire.creator.debug.langDebug
import com.eazpire.creator.brand.BrandAssetsRepository
import com.eazpire.creator.i18n.LocalTranslationStore
import com.eazpire.creator.i18n.TranslationStore
import com.eazpire.creator.locale.LocaleStore
import com.eazpire.creator.mockup.CustomerMockPreviewStore
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.creatorcodes.CreatorCodeAvailableHintStore
import com.eazpire.creator.chat.EazyChatContext
import com.eazpire.creator.chat.EazyChatModal
import com.eazpire.creator.chat.EazyGuideOverlay
import com.eazpire.creator.chat.EazyGuideModeStore
import com.eazpire.creator.chat.EazyChatStore
import com.eazpire.creator.chat.EazySettingsStore
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
import com.eazpire.creator.ui.home.CreatorsIndexScreen
import com.eazpire.creator.favorites.FavoritesRefreshTrigger
import com.eazpire.creator.ui.header.FavoriteEditContext
import com.eazpire.creator.ui.header.FavoritesModal
import com.eazpire.creator.perf.EazPerfTrace
import com.eazpire.creator.ui.header.MainHeader
import com.eazpire.creator.ui.header.MenuDrawer
import com.eazpire.creator.ui.header.SHOP_CREATE_CATALOG_HANDLE
import com.eazpire.creator.ui.header.SHOP_CREATE_PAGE_HANDLE
import com.eazpire.creator.ui.header.SHOP_MENU_CREATE_HANDLE
import com.eazpire.creator.ui.header.ShopMenuBar
import com.eazpire.creator.ui.vouchers.VoucherGiftSubTab
import com.eazpire.creator.ui.vouchers.VoucherModal
import com.eazpire.creator.ui.vouchers.VoucherModalTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ShopScreen(
    tokenStore: SecureTokenStore,
    pendingDeepLink: MutableState<android.net.Uri?>? = null,
    pendingEazyTab: MutableState<EazySidebarTab?>? = null,
    pendingOpenCart: MutableState<Boolean>? = null,
    pendingOpenShop: MutableState<Boolean>? = null,
    pendingWearPairToken: MutableState<String?>? = null,
    pendingArtifactClaimToken: MutableState<String?>? = null,
    pendingCreatorInactiveDesigns: MutableState<Boolean>? = null,
    pendingCreatorCodesNav: MutableState<MainActivity.PendingCreatorCodesNav?>? = null,
    pendingOpenGiftCardsWon: MutableState<Boolean>? = null,
    pendingGamesSection: MutableState<String?>? = null,
    pendingTradeOfferId: MutableState<Int?>? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val localeStore = remember { LocaleStore(context) }
    val translationStore = remember { TranslationStore(context) }
    val brandAssets = remember { BrandAssetsRepository.get(context) }
    val languageCode by localeStore.languageCode.collectAsState(initial = java.util.Locale.getDefault().language.lowercase())
    val catalogRegion by localeStore.regionCode.collectAsState(initial = "EU")

    SideEffect {
        EazPerfTrace.mark("main_first_compose")
    }

    var sessionEpoch by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        AuthDebugLog.d("[TOKEN] ShopScreen compose ${tokenStore.sessionDebugSummary()}")
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val sessionRefreshScope = rememberCoroutineScope()
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                sessionRefreshScope.launch {
                    ShopSessionCoordinator.refreshSession(context, tokenStore, reason = "resume")
                    ShopSessionCoordinator.syncPushIfLoggedIn(context)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(languageCode) {
        // #region agent log
        langDebug("ShopScreen.kt:LaunchedEffect", "languageCode changed, loading", mapOf("languageCode" to languageCode), "H3")
        // #endregion
        translationStore.load(languageCode)
        brandAssets.refreshIfStale()
    }

    CompositionLocalProvider(LocalTranslationStore provides translationStore) {
        /** Outside [key(sessionEpoch)] — OAuth callback + login UI survive session refresh re-key. */
        val productModalHandleState = remember { mutableStateOf<String?>(null) }
        var posterArSessionConfig by remember { mutableStateOf<PosterArSessionConfig?>(null) }
        var showLoginOptions by remember { mutableStateOf(false) }
        var showAuthScreen by remember { mutableStateOf(false) }
        var authAutoStartOAuth by remember { mutableStateOf(false) }
        var authLoginMethod by remember { mutableStateOf(AuthLoginMethod.EMAIL) }
        val oauthCallbackForAuth = remember { mutableStateOf<String?>(null) }

        // OAuth return must be handled before/without losing state to [key(sessionEpoch)] re-key.
        LaunchedEffect(pendingDeepLink?.value) {
            val uri = pendingDeepLink?.value ?: return@LaunchedEffect
            if (uri.scheme?.startsWith("shop.") == true && uri.host == "callback" &&
                uri.getQueryParameter("code") != null
            ) {
                // Ignore stale callbacks after logout unless a new login attempt is in progress.
                if (!showAuthScreen && !OAuthPkceStore.hasPending(context)) {
                    AuthDebugLog.d("[CALLBACK] Ignoring OAuth callback — no active login attempt")
                    pendingDeepLink.value = null
                    return@LaunchedEffect
                }
                oauthCallbackForAuth.value = uri.toString()
                pendingDeepLink.value = null
                authAutoStartOAuth = false
                showAuthScreen = true
            }
        }

    key(sessionEpoch) {
    // Recomposition trigger: when translations load, UI must update
    val translations by translationStore.translations.collectAsState(initial = emptyMap())
    // #region agent log
    LaunchedEffect(translations) {
        langDebug("ShopScreen.kt:translations", "translations updated", mapOf("count" to translations.size, "sample" to translations.keys.take(3).toString()), "H5")
    }
    // #endregion
    var accountModalVisible by remember { mutableStateOf(false) }
    var authSessionTick by remember { mutableIntStateOf(0) }
    var menuDrawerVisible by remember { mutableStateOf(false) }
    var cartDrawerVisible by remember { mutableStateOf(false) }
    var shopContentReloadNonce by remember { mutableIntStateOf(0) }
    var favoritesModalVisible by remember { mutableStateOf(false) }
    var favoriteEditContext by remember { mutableStateOf<FavoriteEditContext?>(null) }
    var eazyChatVisible by remember { mutableStateOf(false) }
    var eazyStartTab by remember { mutableStateOf(EazySidebarTab.Chat) }

    val eazyChatStore = remember { EazyChatStore(context) }
    val eazySettingsStore = remember { EazySettingsStore(context) }
    val pollJwt = tokenStore.getJwt()
    val creatorPollApi = remember(pollJwt) { CreatorApi(jwt = pollJwt) }
    val heroJobForPoll by eazyChatStore.heroJobState.collectAsState()
    val videoJobForPoll by eazyChatStore.videoJobState.collectAsState()
    val designJobForPoll by eazyChatStore.designJobState.collectAsState()

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

    LaunchedEffect(designJobForPoll?.jobId) {
        val jobId = designJobForPoll?.jobId ?: return@LaunchedEffect
        if (designJobForPoll?.terminal == true) return@LaunchedEffect
        while (isActive) {
            try {
                val r = withContext(Dispatchers.IO) { creatorPollApi.pollJob(jobId) }
                val done = r.optBoolean("done")
                val saved = r.optBoolean("saved")
                val saving = r.optBoolean("saving")
                val notFound = r.optBoolean("not_found")
                if (!done) {
                    val progress = r.optInt("progress", 0)
                    val msg = r.optString("message", "").takeIf { it.isNotBlank() }
                    eazyChatStore.updateDesignJobPoll(progress, msg)
                    delay(2000)
                    continue
                }
                if (notFound) {
                    eazyChatStore.failDesignJob(
                        r.optString("message", "").takeIf { it.isNotBlank() } ?: "Job not found"
                    )
                } else if (saving && !saved) {
                    val progress = r.optInt("progress", 90).coerceIn(0, 99)
                    val msg = r.optString("message", "").takeIf { it.isNotBlank() }
                    eazyChatStore.updateDesignJobPoll(progress, msg ?: "Saving…")
                    delay(2000)
                    continue
                } else if (saved) {
                    eazyChatStore.completeDesignSave(jobId)
                } else {
                    eazyChatStore.completeDesignJob(jobId)
                }
                eazyStartTab = EazySidebarTab.Notifications
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
    var currentPagePath by rememberSaveable { mutableStateOf("/") }
    var scrollToTopTrigger by remember { mutableStateOf(0) }
    var selectedCollection by remember { mutableStateOf<Triple<String, String, String?>?>(null) }
    var shopSearchQuery by remember { mutableStateOf<String?>(null) }
    var shopCreateActive by remember { mutableStateOf(false) }
    var shopCreateStudioPhase by remember { mutableStateOf<ShopCreateProductPhase?>(null) }
    var shopCreateCatalogProducts by remember { mutableStateOf<List<CatalogProduct>>(emptyList()) }
    var selectedCreatorName by rememberSaveable { mutableStateOf<String?>(null) }
    var showCreatorsIndex by rememberSaveable { mutableStateOf(false) }
    val shopNavHistory = rememberShopNavHistoryController()
    var isCreatorMode by rememberSaveable { mutableStateOf(false) }

    fun switchCreatorMode(toCreator: Boolean, @Suppress("UNUSED_PARAMETER") animate: Boolean = false) {
        if (toCreator == isCreatorMode) return
        isCreatorMode = toCreator
    }

    fun openShopCreate() {
        shopSearchQuery = null
        selectedCreatorName = null
        showCreatorsIndex = false
        selectedCollection = null
        shopCreateStudioPhase = null
        shopCreateActive = true
    }

    fun openShopCollection(title: String, handle: String, productType: String? = null) {
        shopSearchQuery = null
        selectedCreatorName = null
        showCreatorsIndex = false
        shopCreateStudioPhase = null
        if (
            handle == SHOP_CREATE_PAGE_HANDLE ||
            handle == SHOP_CREATE_CATALOG_HANDLE ||
            handle == SHOP_MENU_CREATE_HANDLE
        ) {
            shopCreateActive = true
            selectedCollection = null
            return
        }
        shopCreateActive = false
        selectedCollection = Triple(title, handle, productType)
    }

    fun openShopHome() {
        shopCreateActive = false
        shopCreateStudioPhase = null
        selectedCollection = null
        selectedCreatorName = null
        showCreatorsIndex = false
        shopSearchQuery = null
        productModalHandleState.value = null
    }

    val pendingWearPair = pendingWearPairToken?.value
    LaunchedEffect(pendingWearPair) {
        if (!pendingWearPair.isNullOrBlank()) isCreatorMode = true
    }

    var pendingCreationsScreen by remember { mutableIntStateOf(-1) }
    var pendingDesignsActivityFilter by remember { mutableStateOf<String?>(null) }
    var pendingCreatorCodesSettings by remember { mutableStateOf<MainActivity.PendingCreatorCodesNav?>(null) }

    LaunchedEffect(
        pendingEazyTab?.value,
        pendingOpenCart?.value,
        pendingOpenShop?.value,
        pendingGamesSection?.value,
        pendingTradeOfferId?.value,
    ) {
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
            switchCreatorMode(toCreator = false, animate = false)
            eazyChatVisible = false
            ps.value = false
        }
        if (!pendingGamesSection?.value.isNullOrBlank() || (pendingTradeOfferId?.value ?: 0) > 0) {
            eazyStartTab = EazySidebarTab.Games
            eazyChatVisible = true
        }
    }

    LaunchedEffect(pendingCreatorInactiveDesigns?.value) {
        val pin = pendingCreatorInactiveDesigns
        if (pin?.value == true) {
            switchCreatorMode(toCreator = true, animate = false)
            eazyChatVisible = false
            pendingCreationsScreen = 2
            pendingDesignsActivityFilter = "inactive"
            pin.value = false
        }
    }

    LaunchedEffect(pendingCreatorCodesNav?.value) {
        val nav = pendingCreatorCodesNav?.value ?: return@LaunchedEffect
        switchCreatorMode(toCreator = true, animate = false)
        eazyChatVisible = false
        pendingCreatorCodesSettings = nav
        pendingCreatorCodesNav.value = null
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
    var voucherModalInitialTab by remember { mutableStateOf<VoucherModalTab?>(null) }
    var voucherModalInitialGiftSubTab by remember { mutableStateOf<VoucherGiftSubTab?>(null) }

    LaunchedEffect(pendingOpenGiftCardsWon?.value) {
        val pg = pendingOpenGiftCardsWon
        if (pg?.value == true) {
            voucherModalInitialTab = VoucherModalTab.GIFT_CARDS
            voucherModalInitialGiftSubTab = VoucherGiftSubTab.REWARDS
            voucherModalVisible = true
            pg.value = false
        }
    }

    val jwtForApi = tokenStore.getJwt()
    val ownerId = tokenStore.getOwnerId().orEmpty()
    val eazySyncApi = remember(jwtForApi, ownerId) { CreatorApi(jwt = jwtForApi) }
    val creatorCodeHintActive by CreatorCodeAvailableHintStore.active.collectAsState()

    LaunchedEffect(ownerId, jwtForApi) {
        while (true) {
            if (ownerId.isNotBlank() && !jwtForApi.isNullOrBlank()) {
                try {
                    CreatorCodeAvailableHintStore.refreshFromResponse(
                        withContext(Dispatchers.IO) { eazySyncApi.getCreatorCode(ownerId) }
                    )
                } catch (_: Exception) {
                    CreatorCodeAvailableHintStore.clear()
                }
            } else {
                CreatorCodeAvailableHintStore.clear()
            }
            delay(60_000)
        }
    }

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

    fun buildShopNavSnapshot(): ShopNavSnapshot =
        ShopNavSnapshot(
            selectedCollection = selectedCollection,
            shopSearchQuery = shopSearchQuery,
            shopCreateActive = shopCreateActive,
            shopCreateStudioOpen = shopCreateStudioPhase != null,
            selectedCreatorName = selectedCreatorName,
            showCreatorsIndex = showCreatorsIndex,
            productModalHandle = productModalHandleState.value,
        )

    fun applyShopNavSnapshot(snapshot: ShopNavSnapshot) {
        selectedCollection = snapshot.selectedCollection
        shopSearchQuery = snapshot.shopSearchQuery
        shopCreateActive = snapshot.shopCreateActive
        if (!snapshot.shopCreateStudioOpen) {
            shopCreateStudioPhase = null
        }
        selectedCreatorName = snapshot.selectedCreatorName
        showCreatorsIndex = snapshot.showCreatorsIndex
        productModalHandleState.value = snapshot.productModalHandle ?: snapshot.selectedProductHandle
    }

    LaunchedEffect(
        selectedCollection,
        shopSearchQuery,
        shopCreateActive,
        shopCreateStudioPhase,
        selectedCreatorName,
        showCreatorsIndex,
        productModalHandleState.value,
    ) {
        if (shopNavHistory.isRestoring) {
            shopNavHistory.finishRestore()
            return@LaunchedEffect
        }
        shopNavHistory.push(buildShopNavSnapshot())
    }

    val shopNavSwipeEnabled =
        !isCreatorMode &&
            !menuDrawerVisible &&
            !cartDrawerVisible &&
            !favoritesModalVisible &&
            !eazyChatVisible &&
            !showAuthScreen &&
            !accountModalVisible &&
            !termsModalVisible &&
            !voucherModalVisible &&
            !showLoginOptions

    fun handleShopNavSwipeBack() {
        if (productModalHandleState.value != null) {
            productModalHandleState.value = null
            return
        }
        if (shopSearchQuery != null) {
            shopSearchQuery = null
            return
        }
        if (selectedCreatorName != null) {
            selectedCreatorName = null
            return
        }
        if (showCreatorsIndex) {
            showCreatorsIndex = false
            return
        }
        if (selectedCollection != null) {
            selectedCollection = null
            return
        }
        if (shopCreateActive) {
            shopCreateActive = false
            shopCreateStudioPhase = null
            return
        }
        shopNavHistory.goBack()?.let { applyShopNavSnapshot(it) }
    }

    fun refreshShopContent() {
        shopContentReloadNonce++
        CustomerMockPreviewStore.invalidate()
        scope.launch {
            val ownerId = tokenStore.getOwnerId().orEmpty()
            if (ownerId.isNotBlank()) {
                withContext(Dispatchers.IO) {
                    CustomerMockPreviewStore.loadMap(creatorPollApi, ownerId, force = true)
                }
            }
        }
    }

    fun handleUserLogout() {
        oauthCallbackForAuth.value = null
        authAutoStartOAuth = false
        showAuthScreen = false
        showLoginOptions = false
        favoriteEditContext = null
        productModalHandleState.value = null
        menuDrawerVisible = false
        cartDrawerVisible = false
        favoritesModalVisible = false
        eazyChatVisible = false
        voucherModalVisible = false
        termsModalVisible = false
        // Account / creator settings modals dismiss via animated sheet hide, then onDismiss + onLogout.
        scope.launch {
            withFrameNanos { }
            withFrameNanos { }
            delay(350)
            ShopSessionGuard.performFullLogout(context, tokenStore)
            refreshShopContent()
            authSessionTick++
        }
    }

    BackHandler(enabled = !isCreatorMode && !showAuthScreen) {
        handleShopNavSwipeBack()
    }

    fun handleShopNavSwipeForward() {
        shopNavHistory.goForward()?.let { applyShopNavSnapshot(it) }
    }

    LaunchedEffect(pendingDeepLink?.value) {
        val uri = pendingDeepLink?.value ?: return@LaunchedEffect
        // OAuth handled outside [key(sessionEpoch)] — see LaunchedEffect above CompositionLocalProvider key.
        if (uri.scheme?.startsWith("shop.") == true && uri.host == "callback" &&
            uri.getQueryParameter("code") != null
        ) {
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
            path == "/creator" || path == "/creator/" -> {
                showCreatorsIndex = true
                selectedCreatorName = null
                selectedCollection = null
                shopSearchQuery = null
            }
            path.startsWith("/creator/") -> {
                val slug = path.removePrefix("/creator/").trimEnd('/').substringBefore("?")
                if (slug.isNotBlank()) {
                    selectedCreatorName = slug
                    selectedCollection = null
                }
            }
            path.startsWith("/products/") -> {
                val handle = path.removePrefix("/products/").trimEnd('/').substringBefore("?")
                if (handle.isNotBlank()) productModalHandleState.value = handle
            }
            path.startsWith("/collections/") -> {
                val handle = path.removePrefix("/collections/").trimEnd('/').substringBefore("?")
                if (handle.isNotBlank()) {
                    val title = COLLECTION_HANDLE_TO_TITLE[handle] ?: handle.replaceFirstChar { it.uppercase() }
                    openShopCollection(title, handle)
                }
            }
            path.startsWith("/pages/creator-dashboard") ||
                path.startsWith("/pages/design-generator") ->
                switchCreatorMode(toCreator = true, animate = false)
            path.startsWith("/search") -> {
                val q = uri.getQueryParameter("q")?.trim().orEmpty()
                if (q.isNotEmpty()) {
                    selectedCollection = null
                    shopSearchQuery = q
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
    if (isCreatorMode) {
        key("creator", authSessionTick) {
        CreatorMainScreen(
            tokenStore = tokenStore,
            localeStore = localeStore,
            translationStore = translationStore,
            initialScreen = pendingCreationsScreen.takeIf { it >= 0 },
            initialDesignsActivityFilter = pendingDesignsActivityFilter,
            onInitialDesignsActivityConsumed = {
                pendingCreationsScreen = -1
                pendingDesignsActivityFilter = null
            },
            onSwitchToShop = { switchCreatorMode(toCreator = false) },
            onAccountClick = {
                if (tokenStore.isLoggedIn()) {
                    accountModalVisible = true
                } else {
                    showLoginOptions = true
                }
            },
            onLogout = { handleUserLogout() },
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
                eazyChatStore.startDesignJob(id, summary)
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
            generationBubbleFaceLeft = generationBubbleFaceLeft,
            pendingWearPairToken = pendingWearPair,
            onWearPairTokenConsumed = { pendingWearPairToken?.value = null },
            pendingCreatorCodesNav = pendingCreatorCodesSettings,
            onPendingCreatorCodesConsumed = { pendingCreatorCodesSettings = null },
        )
        }
    } else {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            key(languageCode, authSessionTick) {
            Column(modifier = Modifier.fillMaxWidth()) {
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
                    eazyChatVisible = eazyChatVisible,
                    onEazyClick = {
                        eazyStartTab = EazySidebarTab.Chat
                        eazyChatVisible = true
                    },
                    onEazyLongPress = { eazyMascotStore.setDockedSync(false) },
                    slotBoundsState = slotBoundsState,
                    isCreatorMode = isCreatorMode,
                    onCreatorModeChange = { switchCreatorMode(toCreator = it) },
                    creatorCodeShopHintActive = creatorCodeHintActive,
                    creatorCodeProfileHintActive = creatorCodeHintActive,
                    onLogoClick = {
                        if (!showAuthScreen) {
                            accountModalVisible = false
                            showLoginOptions = false
                            menuDrawerVisible = false
                            openShopHome()
                            scrollToTopTrigger++
                        }
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
                                    shopCreateActive = false
                                    shopCreateStudioPhase = null
                                    shopSearchQuery = null
                                    selectedCreatorName = null
                                    productModalHandleState.value = handle
                                }
                            }
                            path.startsWith("/search") -> {
                                val q = uri.getQueryParameter("q")?.trim().orEmpty()
                                if (q.isNotEmpty()) {
                                    shopCreateActive = false
                                    shopCreateStudioPhase = null
                                    selectedCollection = null
                                    selectedCreatorName = null
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
                            shopCreateActive = false
                            shopCreateStudioPhase = null
                            selectedCollection = null
                            selectedCreatorName = null
                            productModalHandleState.value = null
                            shopSearchQuery = t
                        }
                    }
                )
                ShopMenuBar(
                    onAllClick = {
                        when {
                            productModalHandleState.value != null -> productModalHandleState.value = null
                            shopSearchQuery != null -> shopSearchQuery = null
                            shopCreateActive -> {
                                shopCreateActive = false
                                shopCreateStudioPhase = null
                            }
                            selectedCreatorName != null -> {
                                selectedCreatorName = null
                                if (!showCreatorsIndex) scrollToTopTrigger++
                            }
                            showCreatorsIndex -> {
                                showCreatorsIndex = false
                                scrollToTopTrigger++
                            }
                            selectedCollection != null -> selectedCollection = null
                            else -> menuDrawerVisible = true
                        }
                    },
                    onCategoryClick = { title, handle, productType ->
                        if (handle == SHOP_MENU_CREATE_HANDLE) {
                            openShopCreate()
                        } else {
                            openShopCollection(title, handle, productType)
                        }
                    },
                    selectedHandle = if (shopCreateActive) SHOP_MENU_CREATE_HANDLE else selectedCollection?.second
                )
                if (shopCreateActive || selectedCollection != null || selectedCreatorName != null || showCreatorsIndex) {
                    val creatorsLabel = translationStore.t("eaz.home.creators", "Creators")
                    CollectionBreadcrumb(
                        categoryTitle = when {
                            shopCreateActive -> translationStore.t("creator.shop_create_product.entry", "Create")
                            selectedCreatorName != null -> creatorsLabel
                            showCreatorsIndex -> creatorsLabel
                            else -> selectedCollection?.first ?: ""
                        },
                        onHomeClick = { openShopHome() },
                        productTitle = selectedCreatorName,
                        onCollectionClick = when {
                            selectedCreatorName != null -> {
                                {
                                    selectedCreatorName = null
                                    showCreatorsIndex = true
                                }
                            }
                            else -> null
                        }
                    )
                }
            }
            }
        },
        bottomBar = {
            key(languageCode, authSessionTick) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SubFooter(
                            localeStore = localeStore,
                            translationStore = translationStore,
                            tokenStore = tokenStore,
                            onWalletClick = { voucherModalVisible = true },
                            onCountryChange = { refreshShopContent() },
                        )
                        GlobalFooter(onTermsClick = { termsModalVisible = true })
                    }
                }
        }
    ) { padding ->
        key(languageCode) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .shopNavEdgeGestures(
                    enabled = shopNavSwipeEnabled,
                    onSwipeBack = { handleShopNavSwipeBack() },
                    onSwipeForward = { handleShopNavSwipeForward() },
                    onPullRefresh = { refreshShopContent() },
                )
        ) {
            when {
                shopCreateActive && shopCreateStudioPhase == null -> ShopCreateCollectionScreen(
                    api = creatorPollApi,
                    region = catalogRegion,
                    modifier = Modifier.fillMaxSize(),
                    onProductsLoaded = { shopCreateCatalogProducts = it },
                    onProductClick = { p ->
                        shopCreateStudioPhase = ShopCreateProductPhase.StudioCustomize(p)
                    }
                )
                selectedCreatorName != null -> CreatorProfileScreen(
                    creatorName = selectedCreatorName!!,
                    api = creatorPollApi,
                    viewerOwnerId = tokenStore.getOwnerId().orEmpty(),
                    onBack = { selectedCreatorName = null },
                    onProductClick = { handle ->
                        if (handle.isNotBlank()) productModalHandleState.value = handle
                    },
                    onCartClick = { handle ->
                        if (handle.isNotBlank()) productModalHandleState.value = handle
                    },
                    modifier = Modifier.fillMaxSize()
                )
                showCreatorsIndex -> CreatorsIndexScreen(
                    creatorApi = creatorPollApi,
                    labelForKey = { k, d -> translationStore.t(k, d) },
                    onCreatorClick = { name ->
                        selectedCreatorName = name
                        shopSearchQuery = null
                        selectedCollection = null
                    },
                    onProductClick = { handle ->
                        if (handle.isNotBlank()) productModalHandleState.value = handle
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                shopSearchQuery != null -> ShopSearchScreen(
                    searchQuery = shopSearchQuery!!,
                    onBack = { shopSearchQuery = null },
                    onProductClick = { p ->
                        productModalHandleState.value = p.handle
                    },
                    onCartClick = { p ->
                        productModalHandleState.value = p.handle
                    },
                    reloadTrigger = shopContentReloadNonce,
                )
                selectedCollection != null -> {
                    val (title, handle, productType) = selectedCollection!!
                    CollectionScreen(
                        title = title,
                        collectionHandle = handle,
                        initialProductType = productType,
                        onBack = { selectedCollection = null },
                        onProductClick = { productModalHandleState.value = it.handle },
                        onCartClick = { productModalHandleState.value = it.handle },
                        reloadTrigger = shopContentReloadNonce,
                    )
                }
                else -> ProductCarouselSection(
                    tokenStore = tokenStore,
                    modifier = Modifier.fillMaxSize(),
                    onCurrentPageChange = { currentPagePath = it },
                    onCreatorClick = { name ->
                        selectedCreatorName = name
                        showCreatorsIndex = false
                        shopSearchQuery = null
                        selectedCollection = null
                    },
                    onCreatorsTitleClick = {
                        showCreatorsIndex = true
                        selectedCreatorName = null
                        shopSearchQuery = null
                        selectedCollection = null
                        productModalHandleState.value = null
                    },
                    onCreateScratchClick = { catalogProduct ->
                        shopCreateStudioPhase = ShopCreateProductPhase.StudioCustomize(catalogProduct)
                    },
                    onCategoryClick = { title, handle ->
                        productModalHandleState.value = null
                        selectedCreatorName = null
                        showCreatorsIndex = false
                        shopSearchQuery = null
                        if (handle == SHOP_MENU_CREATE_HANDLE) {
                            openShopCreate()
                        } else {
                            openShopCollection(title, handle, null)
                        }
                    },
                    onProductClick = { params ->
                        productModalHandleState.value = null
                        shopSearchQuery = null
                        productModalHandleState.value = params.handle
                        selectedCreatorName = null

                        if (params.collectionTitle != null && params.collectionHandle != null) {
                            selectedCollection = Triple(params.collectionTitle, params.collectionHandle, null)
                        }
                    },
                    onHotspotProductClick = { handle ->
                        val cleanHandle = handle.trim()
                        if (cleanHandle.isNotBlank()) {
                            shopSearchQuery = null
                            selectedCreatorName = null
                            selectedCollection = null
                            productModalHandleState.value = cleanHandle

                            Log.d(
                                "ProductModalDebug",
                                "[SHOPSCREEN HOTSPOT] Opening ProductModal from hotspot: handle=$cleanHandle"
                            )
                        } else {
                            Log.w(
                                "ProductModalDebug",
                                "[SHOPSCREEN HOTSPOT] Empty handle received from hotspot"
                            )
                        }
                    },
                    productModalHandleState = productModalHandleState,
                    scrollToTopTrigger = scrollToTopTrigger,
                    reloadTrigger = shopContentReloadNonce,
                )
            }
        }
        }
    }
    }
    if (!isCreatorMode && shopCreateStudioPhase != null) {
        ShopCreateProductFlow(
            phase = shopCreateStudioPhase,
            catalogProducts = shopCreateCatalogProducts,
            onCloseStudio = { shopCreateStudioPhase = null },
            onPhaseChange = { shopCreateStudioPhase = it },
            api = creatorPollApi,
            tokenStore = tokenStore,
            translationStore = translationStore,
            translation = { k, d -> translationStore.t(k, d) },
            onRequireLogin = {
                shopCreateStudioPhase = null
                shopCreateActive = false
                showLoginOptions = true
            }
        )
    }
    val showGenOverlay = isCreatorMode && eazyGenerationOverlay
    // Full-screen zIndex layer must not cover product detail or product modal — it would steal touches (cart / buy now).
    val showEazyFloatingLayer =
        (!eazyDocked || showGenOverlay) && productModalHandleState.value == null && !showAuthScreen
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
                /** Let taps reach hero / carousel; mascot + bubble are real touch targets underneath. */
                .pointerInteropFilter { false }
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
                    /** Same pass-through: empty overlay must not steal hero hotspot taps when Eazy floats. */
                    .pointerInteropFilter { false }
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
                            val px = liveMascotX
                            val py = liveMascotY
                            if (px == null || py == null ||
                                kotlin.math.abs(x - px) > 6f ||
                                kotlin.math.abs(y - py) > 6f
                            ) {
                                liveMascotX = x
                                liveMascotY = y
                            }
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
        eazySettingsStore = eazySettingsStore,
        onDismiss = {
            if (EazyGuideModeStore.active.value) EazyGuideModeStore.exit()
            eazyChatVisible = false
        },
        onLoginClick = {
            eazyChatVisible = false
            showLoginOptions = true
        },
        onResetMascot = { eazyMascotStore.resetSync() },
        chatContext = if (isCreatorMode) EazyChatContext.Creator else EazyChatContext.Shop,
        startTab = eazyStartTab,
        pendingGamesSection = pendingGamesSection?.value,
        pendingTradeOfferId = pendingTradeOfferId?.value,
        onPendingGamesSectionConsumed = { pendingGamesSection?.value = null },
        onPendingTradeOfferConsumed = { pendingTradeOfferId?.value = null },
        pendingArtifactClaimToken = pendingArtifactClaimToken?.value,
        onPendingArtifactClaimConsumed = { pendingArtifactClaimToken?.value = null },
        onOpenCreatorCodes = { prefillCode ->
            eazyChatVisible = false
            switchCreatorMode(toCreator = true, animate = false)
            pendingCreatorCodesSettings = MainActivity.PendingCreatorCodesNav(prefillCode = prefillCode)
        },
    )

    EazyGuideOverlay(
        creatorApi = eazySyncApi,
        pagePath = currentPagePath,
        locale = localeStore.getLanguageCodeSync()
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
                openShopCreate()
            } else {
                openShopCollection(title, handle, productType)
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
            openShopHome()
            scrollToTopTrigger++
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
            if (tokenStore.isLoggedIn()) {
                accountModalVisible = true
            } else {
                showLoginOptions = true
            }
        },
        onVouchersClick = { tab ->
            voucherModalInitialTab = tab
            voucherModalVisible = true
        }
    )

    VoucherModal(
        visible = voucherModalVisible,
        onDismiss = {
            voucherModalVisible = false
            voucherModalInitialTab = null
            voucherModalInitialGiftSubTab = null
        },
        tokenStore = tokenStore,
        translationStore = translationStore,
        initialTab = voucherModalInitialTab,
        initialGiftSubTab = voucherModalInitialGiftSubTab,
    )

    if (termsModalVisible) {
        TermsModal(
            visible = true,
            baseUrl = "https://www.eazpire.com",
            translationStore = translationStore,
            onDismiss = { termsModalVisible = false }
        )
    }

    if (accountModalVisible) {
        AccountModalSheet(
            tokenStore = tokenStore,
            onDismiss = { accountModalVisible = false },
            onLogout = { handleUserLogout() }
        )
    }

    if (favoritesModalVisible) {
        FavoritesModal(
            visible = true,
            customerId = ownerId.ifBlank { null },
            api = eazySyncApi,
            tokenStore = tokenStore,
            onDismiss = { favoritesModalVisible = false },
            onCountChange = { FavoritesRefreshTrigger.trigger() },
            onProductClick = { handle ->
                if (handle.isNotBlank()) {
                    favoritesModalVisible = false
                    favoriteEditContext = null
                    productModalHandleState.value = handle
                }
            },
            onEditFavorite = { ctx ->
                favoritesModalVisible = false
                favoriteEditContext = ctx.copy(
                    onSaved = {
                        ctx.onSaved()
                        favoriteEditContext = null
                    },
                    onDismiss = {
                        favoriteEditContext = null
                        productModalHandleState.value = null
                    },
                )
                productModalHandleState.value = ctx.productHandle
            },
        )
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
            key(modalHandle) {
                ProductModal(
                    productHandle = modalHandle,
                    onDismiss = {
                        favoriteEditContext = null
                        productModalHandleState.value = null
                    },
                    tokenStore = tokenStore,
                    onTermsClick = { termsModalVisible = true },
                    onNavigateToCreator = { name ->
                        favoriteEditContext = null
                        productModalHandleState.value = null
                        selectedCreatorName = name
                    },
                    onNavigateToProduct = { handle ->
                        if (handle.isNotBlank()) {
                            favoriteEditContext = null
                            productModalHandleState.value = handle
                        }
                    },
                    onPosterArOpen = { config -> posterArSessionConfig = config },
                    favoriteEdit = favoriteEditContext,
                )
            }
        }
    }

    posterArSessionConfig?.let { config ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(Float.MAX_VALUE)
        ) {
            PosterArOverlay(
                config = config,
                onDismiss = { posterArSessionConfig = null },
            )
        }
    }

    }

    if (showLoginOptions) {
        LoginOptionsModal(
            onDismiss = { showLoginOptions = false },
            onLoginClick = { method ->
                showLoginOptions = false
                productModalHandleState.value = null
                authLoginMethod = method
                authAutoStartOAuth = true
                showAuthScreen = true
            }
        )
    }

    if (showAuthScreen) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(Float.MAX_VALUE)
        ) {
            AuthScreen(
                tokenStore = tokenStore,
                onAuthSuccess = {
                    showAuthScreen = false
                    authAutoStartOAuth = false
                    sessionEpoch++
                },
                onDismiss = {
                    showAuthScreen = false
                    authAutoStartOAuth = false
                },
                loginMethod = authLoginMethod,
                autoStartOAuth = authAutoStartOAuth,
                onAutoStartConsumed = { authAutoStartOAuth = false },
                oauthCallbackUri = oauthCallbackForAuth
            )
        }
    }

    }
}
