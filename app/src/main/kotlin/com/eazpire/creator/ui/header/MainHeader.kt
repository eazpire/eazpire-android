package com.eazpire.creator.ui.header

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.api.ShopifyStorefrontCartApi
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.cart.AppCartStore
import com.eazpire.creator.cart.StorefrontCartStore
import com.eazpire.creator.ui.share.buildShareUrl
import com.eazpire.creator.ui.share.getActiveRefUrl
import com.eazpire.creator.locale.LocaleStore
import com.eazpire.creator.util.DebugLog
import kotlinx.coroutines.Dispatchers
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
    modifier: Modifier = Modifier
) {
    val countryCode by localeStore.countryCode.collectAsState(initial = localeStore.getCountryCodeSync())
    val languageCode by localeStore.languageCode.collectAsState(initial = localeStore.getLanguageCodeSync())
    var searchQuery by remember { mutableStateOf("") }
    var languageStandard by remember { mutableStateOf(AVAILABLE_LANGUAGES) }
    var languageChildren by remember { mutableStateOf<Map<String, LanguageChildren>>(emptyMap()) }

    LaunchedEffect(Unit) {
        try {
            val api = CreatorApi()
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
    var isCreatorMode by remember { mutableStateOf(false) }
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
    val ownerId = remember(tokenStore) { tokenStore?.getOwnerId() ?: "" }
    val api = remember { CreatorApi(jwt = tokenStore?.getJwt()) }
    var shareUrl by remember { mutableStateOf<String?>(null) }

    val favoritesRefreshTrigger = com.eazpire.creator.favorites.FavoritesRefreshTrigger.value
    LaunchedEffect(ownerId, favoritesRefreshTrigger) {
        if (ownerId.isNotBlank()) {
            try {
                val resp = api.getFavorites(ownerId)
                if (resp.optBoolean("ok", false)) {
                    favoritesCount = resp.optInt("count", 0)
                }
                shareUrl = getActiveRefUrl(api, ownerId)
            } catch (_: Exception) {}
        } else {
            favoritesCount = 0
            shareUrl = null
        }
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
                HeaderLogo(onClick = null)
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
            }
            CreatorSwitch(
                isCreatorMode = isCreatorMode,
                onModeChange = { isCreatorMode = it }
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 1.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            HeaderLocaleRow(
                localeStore = localeStore,
                countryCode = countryCode,
                languageCode = languageCode,
                standardLanguages = languageStandard,
                languageChildren = languageChildren,
                onCountryChange = { },
                onLanguageChange = { }
            )
            HeaderActions(
                cartCount = com.eazpire.creator.cart.AppCartStore.itemCount,
                favoritesCount = favoritesCount,
                onAccountClick = onAccountClick,
                onFavoritesClick = {
                    DebugLog.click("Favorites icon")
                    onFavoritesModalChangeActual(true)
                },
                onCartClick = {
                    DebugLog.click("Cart icon")
                    onCartDrawerChangeActual(true)
                }
            )
        }
        HeaderSearch(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            onSearch = { }
        )
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
            onCountChange = { favoritesCount = it }
        )
    }
}
