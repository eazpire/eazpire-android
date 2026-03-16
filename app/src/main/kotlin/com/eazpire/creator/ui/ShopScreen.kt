package com.eazpire.creator.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.locale.LocaleStore
import com.eazpire.creator.ui.account.AccountModalSheet
import com.eazpire.creator.ui.footer.GlobalFooter
import com.eazpire.creator.ui.header.CollectionBreadcrumb
import com.eazpire.creator.ui.header.MainHeader
import com.eazpire.creator.ui.header.MenuDrawer
import com.eazpire.creator.ui.header.ShopMenuBar

/**
 * Shop-Screen: Direkt zugänglich ohne Login.
 * Zeigt MainHeader und Platzhalter-Content (native UI).
 */
@Composable
fun ShopScreen(
    tokenStore: SecureTokenStore,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val localeStore = remember { LocaleStore(context) }
    var accountModalVisible by remember { mutableStateOf(false) }
    var showLoginOptions by remember { mutableStateOf(false) }
    var showAuthScreen by remember { mutableStateOf(false) }
    var menuDrawerVisible by remember { mutableStateOf(false) }
    var cartDrawerVisible by remember { mutableStateOf(false) }
    var favoritesModalVisible by remember { mutableStateOf(false) }
    var currentPagePath by remember { mutableStateOf("/") }
    var scrollToTopTrigger by remember { mutableStateOf(0) }
    var selectedCollection by remember { mutableStateOf<Triple<String, String, String?>?>(null) }
    var selectedProductHandle by remember { mutableStateOf<String?>(null) }
    var productModalHandle by remember { mutableStateOf<String?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
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
                    onLogoClick = {
                        accountModalVisible = false
                        showLoginOptions = false
                        menuDrawerVisible = false
                        showAuthScreen = false
                        selectedCollection = null
                        selectedProductHandle = null
                        productModalHandle = null
                        scrollToTopTrigger++
                    },
                    onAccountClick = {
                        if (tokenStore.isLoggedIn()) {
                            accountModalVisible = true
                        } else {
                            showLoginOptions = true
                        }
                    }
                )
                ShopMenuBar(
                    onAllClick = {
                        when {
                            selectedProductHandle != null -> menuDrawerVisible = true
                            selectedCollection != null -> selectedCollection = null
                            else -> menuDrawerVisible = true
                        }
                    },
                    onCategoryClick = { title, handle ->
                        selectedProductHandle = null
                        selectedCollection = Triple(title, handle, null)
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
        },
        bottomBar = {
            if (selectedProductHandle == null) GlobalFooter()
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { focusManager.clearFocus() }
        ) {
            when {
                selectedProductHandle != null -> ProductDetailScreen(
                    productHandle = selectedProductHandle!!,
                    onBack = { selectedProductHandle = null },
                    tokenStore = tokenStore
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
                    onCurrentPageChange = { currentPagePath = it },
                    onCategoryClick = { title, h -> selectedCollection = Triple(title, h, null) },
                    onProductClick = { params ->
                        selectedProductHandle = params.handle
                        if (params.collectionTitle != null && params.collectionHandle != null) {
                            selectedCollection = Triple(params.collectionTitle, params.collectionHandle, null)
                        }
                    },
                    onHotspotProductClick = { handle -> productModalHandle = handle },
                    scrollToTopTrigger = scrollToTopTrigger
                )
            }
        }
    }

    MenuDrawer(
        visible = menuDrawerVisible,
        tokenStore = tokenStore,
        cartCount = com.eazpire.creator.cart.AppCartStore.itemCount,
        onDismiss = { menuDrawerVisible = false },
        onCategoryClick = { title, handle, productType ->
            menuDrawerVisible = false
            selectedProductHandle = null
            selectedCollection = Triple(title, handle, productType)
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
        }
    )

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
                onAuthSuccess = { showAuthScreen = false }
            )
        }
    }

    if (productModalHandle != null) {
        Dialog(
            onDismissRequest = { productModalHandle = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
            ) {
                ProductDetailScreen(
                    productHandle = productModalHandle!!,
                    onBack = { productModalHandle = null },
                    tokenStore = tokenStore,
                    showCloseButton = true,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
