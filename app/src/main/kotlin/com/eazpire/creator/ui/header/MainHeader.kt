package com.eazpire.creator.ui.header

import android.content.Intent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.sp
import com.eazpire.creator.EazColors
import com.eazpire.creator.chat.EazyMascotIcon
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.api.ShopifyStorefrontCartApi
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.cart.AppCartStore
import com.eazpire.creator.cart.StorefrontCartStore
import com.eazpire.creator.favorites.FavoritesRefreshTrigger
import com.eazpire.creator.ui.header.HeaderActions
import com.eazpire.creator.ui.share.buildShareUrl
import com.eazpire.creator.ui.share.getActiveRefUrl
import com.eazpire.creator.i18n.LocalTranslationStore
import com.eazpire.creator.locale.LocaleStore
import com.eazpire.creator.util.DebugLog
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
    onEazyClick: () -> Unit = {},
    onEazyLongPress: () -> Unit = {},
    slotBoundsState: androidx.compose.runtime.MutableState<Rect?>? = null,
    isCreatorMode: Boolean = false,
    onCreatorModeChange: (Boolean) -> Unit = {},
    onSearchNavigate: (String) -> Unit = {},
    onSearchQuerySubmit: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var internalCartDrawerVisible by remember { mutableStateOf(false) }
    val cartDrawerVisible = cartDrawerVisibleControl ?: internalCartDrawerVisible
    val onCartDrawerChangeActual = onCartDrawerChange ?: { internalCartDrawerVisible = it }
    var checkoutUrl by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val storefrontCartStore = remember { StorefrontCartStore(context) }
    val storefrontCartApi = remember { ShopifyStorefrontCartApi() }
    LaunchedEffect(Unit) {
        val cartId = storefrontCartStore.cartId
        if (cartId != null) {
            val cart = withContext(Dispatchers.IO) { storefrontCartApi.getCart(cartId) }
            AppCartStore.setCount(cart?.itemCount ?: 0)
            if (cart == null) storefrontCartStore.clear()
        } else {
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
                        val urlToShare = shareUrl?.let { buildShareUrl(it, currentPagePath) }
                            ?: "https://www.eazpire.com" + if (currentPagePath.isNotBlank() && currentPagePath != "/") currentPagePath else ""
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, urlToShare)
                        }
                        val chooser = Intent.createChooser(sendIntent, null)
                        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        ctx.startActivity(chooser)
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
                                            onTap = { onEazyClick() },
                                            onPress = {
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
                onModeChange = onCreatorModeChange
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            HeaderSearch(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                onSearch = { },
                onSubmitSearchQuery = { q ->
                    searchQuery = ""
                    onSearchQuerySubmit(q)
                },
                onNavigateToUrl = onSearchNavigate,
                placeholder = run {
                    val store = LocalTranslationStore.current
                    val tr = store?.translations?.collectAsState(initial = emptyMap())?.value
                    store?.t("search.placeholder", "Search...") ?: "Search..."
                },
                modifier = Modifier.weight(1f)
            )
            HeaderActions(
                cartCount = AppCartStore.itemCount,
                favoritesCount = favoritesCount,
                onAccountClick = onAccountClick,
                onFavoritesClick = { onFavoritesModalChangeActual(true) },
                onCartClick = { onCartDrawerChangeActual(true) }
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(EazColors.TopbarBorder)
        )
    }
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
        FavoritesModal(
            visible = favoritesModalVisible,
            customerId = ownerId.ifBlank { null },
            api = api,
            onDismiss = { onFavoritesModalChangeActual(false) },
            onCountChange = { count ->
                favoritesCount = count
            }
        )
    }
}
