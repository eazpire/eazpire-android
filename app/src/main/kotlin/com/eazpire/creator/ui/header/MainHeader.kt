package com.eazpire.creator.ui.header

import android.content.Intent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.eazpire.creator.EazColors
import com.eazpire.creator.chat.EazyMascotIcon
import com.eazpire.creator.chat.EazyGuideModeStore
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.api.ShopifyStorefrontCartApi
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.cart.AppCartStore
import com.eazpire.creator.cart.StorefrontCartStore
import com.eazpire.creator.favorites.FavoritesRefreshTrigger
import com.eazpire.creator.ui.share.getActiveRefUrl
import com.eazpire.creator.ui.share.prefetchShareUrl
import com.eazpire.creator.ui.share.sharePageLink
import com.eazpire.creator.i18n.LocalTranslationStore
import com.eazpire.creator.locale.LocaleStore
import com.eazpire.creator.mockup.CustomerMockPreviewStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MainHeader(
    localeStore: LocaleStore,
    tokenStore: SecureTokenStore? = null,
    onAccountClick: () -> Unit = {},
    onLogoClick: () -> Unit = {},
    currentPagePath: String = "/",
    cartDrawerVisibleControl: Boolean? = null,
    onCartDrawerChange: ((Boolean) -> Unit)? = null,
    favoritesModalVisibleControl: Boolean? = null,
    onFavoritesModalChange: ((Boolean) -> Unit)? = null,
    eazyDocked: Boolean = false,
    eazySnapModeActive: Boolean = false,
    eazyChatVisible: Boolean = false,
    onEazyClick: () -> Unit = {},
    onEazyLongPress: () -> Unit = {},
    slotBoundsState: androidx.compose.runtime.MutableState<Rect?>? = null,
    isCreatorMode: Boolean = false,
    onCreatorModeChange: (Boolean) -> Unit = {},
    creatorCodeShopHintActive: Boolean = false,
    creatorCodeProfileHintActive: Boolean = false,
    onSearchNavigate: (String) -> Unit = {},
    onSearchQuerySubmit: (String) -> Unit = {},
    onCreateProductFromRefSearch: (RefSearchCreateProductRequest) -> Unit = {},
    onWalletClick: () -> Unit = {},
    onCountryChange: (String) -> Unit = {},
    onLanguageChange: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var searchModalVisible by remember { mutableStateOf(false) }
    var internalCartDrawerVisible by remember { mutableStateOf(false) }
    val cartDrawerVisible = cartDrawerVisibleControl ?: internalCartDrawerVisible
    val onCartDrawerChangeActual = onCartDrawerChange ?: { internalCartDrawerVisible = it }
    var checkoutUrl by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val storefrontCartStore = remember { StorefrontCartStore(context) }
    val storefrontCartApi = remember { ShopifyStorefrontCartApi() }
    LaunchedEffect(Unit) {
        try {
            val cartId = storefrontCartStore.cartId
            if (cartId != null) {
                val cart = withContext(Dispatchers.IO) { storefrontCartApi.getCart(cartId) }
                AppCartStore.setCount(cart?.itemCount ?: 0)
                if (cart == null) storefrontCartStore.clear()
            } else {
                AppCartStore.setCount(0)
            }
        } catch (_: Exception) {
            AppCartStore.setCount(0)
        }
    }
    LaunchedEffect(cartDrawerVisible) {
        if (!cartDrawerVisible) {
            val cartId = storefrontCartStore.cartId
            if (cartId != null) {
                val cart = withContext(Dispatchers.IO) { storefrontCartApi.getCart(cartId) }
                AppCartStore.setCount(cart?.itemCount ?: 0)
            }
        }
    }
    var internalFavoritesModalVisible by remember { mutableStateOf(false) }
    val favoritesModalVisible = favoritesModalVisibleControl ?: internalFavoritesModalVisible
    val onFavoritesModalChangeActual = onFavoritesModalChange ?: { internalFavoritesModalVisible = it }
    var favoritesCount by remember { mutableStateOf(0) }
    val jwt = tokenStore?.getJwt()
    val ownerId = tokenStore?.getOwnerId().orEmpty()
    val api = remember(jwt, ownerId) { CreatorApi(jwt = jwt) }
    var mockPreviewRevision by remember { mutableIntStateOf(CustomerMockPreviewStore.revision) }
    LaunchedEffect(ownerId) {
        if (ownerId.isNotBlank()) {
            CustomerMockPreviewStore.loadMap(api, ownerId)
            mockPreviewRevision = CustomerMockPreviewStore.revision
        }
    }
    val favoritesRefreshTick = FavoritesRefreshTrigger.value
    var shareUrl by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
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
                shareUrl = getActiveRefUrl(api, ownerId)
            } catch (_: Exception) {}
        } else {
            shareUrl = null
        }
    }

    // Prefetch opaque short link for current page so Share opens instantly
    LaunchedEffect(ownerId, currentPagePath) {
        if (ownerId.isBlank()) return@LaunchedEffect
        try {
            prefetchShareUrl(api, ownerId, currentPagePath, context)
        } catch (_: Exception) {}
    }

    LaunchedEffect(ownerId, favoritesRefreshTick) {
        if (ownerId.isBlank()) {
            favoritesCount = 0
            return@LaunchedEffect
        }
        try {
            val resp = withContext(Dispatchers.IO) { api.getFavorites(ownerId) }
            if (resp.optBoolean("ok", false)) {
                val arr = resp.optJSONArray("items")
                val n = resp.optInt("count", arr?.length() ?: 0)
                favoritesCount = if (n >= 0) n else (arr?.length() ?: 0)
            }
        } catch (_: Exception) {}
    }

    val countryCode by localeStore.countryCode.collectAsState(initial = localeStore.getCountryCodeSync())
    val languageCode by localeStore.languageCode.collectAsState(initial = localeStore.getLanguageCodeSync())
    var languageStandard by remember { mutableStateOf(AVAILABLE_LANGUAGES) }
    var languageChildren by remember { mutableStateOf<Map<String, LanguageChildren>>(emptyMap()) }
    LaunchedEffect(Unit) {
        try {
            val resp = CreatorApi().getLanguages()
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
    val translationStore = LocalTranslationStore.current
    val searchAria = translationStore?.t("eaz.search.input_aria", "Search") ?: "Search"

    Box {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 1.dp, start = 8.dp, end = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HeaderLogo(onClick = onLogoClick)
                val ctx = LocalContext.current
                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            if (ownerId.isNotBlank()) {
                                try {
                                    sharePageLink(ctx, api, ownerId, currentPagePath)
                                    return@launch
                                } catch (_: Exception) {}
                            }
                            val fallback = shareUrl
                                ?: ("https://www.eazpire.com" + if (currentPagePath.isNotBlank() && currentPagePath != "/") currentPagePath else "")
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, fallback)
                            }
                            val chooser = Intent.createChooser(sendIntent, null)
                            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            ctx.startActivity(chooser)
                        }
                    },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color.Transparent,
                        contentColor = EazColors.Orange
                    )
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Share")
                }
                Box(
                        modifier = Modifier
                            .scale(boomScale.value)
                            .padding(horizontal = 4.dp)
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
                                if (eazyDocked) Modifier
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onDoubleTap = { EazyGuideModeStore.enter(chatUiOnlyScope = eazyChatVisible) },
                                            onTap = {
                                                if (EazyGuideModeStore.active.value) {
                                                    EazyGuideModeStore.exit()
                                                } else {
                                                    onEazyClick()
                                                }
                                            },
                                            onPress = {
                                                if (EazyGuideModeStore.active.value) {
                                                    tryAwaitRelease()
                                                    return@detectTapGestures
                                                }
                                                var job: Job? = null
                                                job = coroutineScope.launch {
                                                    delay(300)
                                                    onEazyLongPress()
                                                }
                                                try {
                                                    awaitRelease()
                                                } catch (_: Exception) {}
                                                job?.cancel()
                                            }
                                        )
                                    }
                                else Modifier
                            )
                    ) {
                        if (eazyDocked) {
                            EazyMascotIcon(modifier = Modifier.fillMaxSize())
                        }
                    }
            }
            CreatorSwitch(
                isCreatorMode = isCreatorMode,
                onModeChange = onCreatorModeChange,
                autoShopHintActive = creatorCodeShopHintActive && !isCreatorMode,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(
                onClick = { searchModalVisible = true },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = searchAria,
                    tint = EazColors.TextPrimary
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            HeaderLocaleRow(
                localeStore = localeStore,
                countryCode = countryCode,
                languageCode = languageCode,
                translationStore = translationStore,
                standardLanguages = languageStandard,
                languageChildren = languageChildren,
                onCountryChange = onCountryChange,
                onLanguageChange = onLanguageChange
            )
            HeaderWalletPill(
                tokenStore = tokenStore,
                onClick = onWalletClick,
                translationStore = translationStore
            )
            Spacer(modifier = Modifier.width(2.dp))
            HeaderActions(
                cartCount = AppCartStore.itemCount,
                favoritesCount = favoritesCount,
                onAccountClick = onAccountClick,
                onFavoritesClick = { onFavoritesModalChangeActual(true) },
                onCartClick = { onCartDrawerChangeActual(true) },
                profileHintActive = creatorCodeProfileHintActive && isCreatorMode,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(EazColors.TopbarBorder)
        )
    }
        HeaderSearchModal(
            visible = searchModalVisible,
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            onDismiss = {
                searchModalVisible = false
                searchQuery = ""
            },
            onSubmitSearchQuery = { q ->
                searchQuery = ""
                searchModalVisible = false
                onSearchQuerySubmit(q)
            },
            onNavigateToUrl = onSearchNavigate,
            ownerId = ownerId,
            creatorApi = api,
            mockPreviewRevision = mockPreviewRevision,
            onCreateProductFromRefSearch = onCreateProductFromRefSearch,
        )
        CartDrawer(
            visible = cartDrawerVisible,
            tokenStore = tokenStore,
            onDismiss = { onCartDrawerChangeActual(false) },
            onCheckout = { url ->
                checkoutUrl = url
                onCartDrawerChangeActual(false)
            }
        )
        if (checkoutUrl != null) {
            CheckoutDrawer(
                visible = true,
                checkoutUrl = checkoutUrl!!,
                onDismiss = { checkoutUrl = null }
            )
        }
    }
}
