package com.eazpire.creator.ui

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.debug.debugLog
import com.eazpire.creator.i18n.LocalTranslationStore
import com.eazpire.creator.i18n.TranslationStore
import com.eazpire.creator.locale.LocaleStore
import com.eazpire.creator.ui.account.AccountModalSheet
import com.eazpire.creator.ui.footer.GlobalFooter
import com.eazpire.creator.ui.footer.SubFooter
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
    val translationStore = remember { TranslationStore(context) }
    val languageCode by localeStore.languageCode.collectAsState(initial = java.util.Locale.getDefault().language.lowercase())

    LaunchedEffect(languageCode) {
        translationStore.load(languageCode)
    }
    CompositionLocalProvider(LocalTranslationStore provides translationStore) {
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
    val productModalHandleState = remember { mutableStateOf<String?>(null) }

    LaunchedEffect(productModalHandleState.value) {
        // #region agent log
        debugLog("ShopScreen.kt:59", "LaunchedEffect productModalHandleState changed", mapOf("value" to productModalHandleState.value), "H2")
        // #endregion
        Log.d("ProductModalDebug", "[5] ShopScreen: productModalHandleState changed to ${productModalHandleState.value}")
    }

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
                        productModalHandleState.value = null
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
            if (selectedProductHandle == null) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SubFooter(
                        localeStore = localeStore,
                        tokenStore = tokenStore,
                        cartCount = com.eazpire.creator.cart.AppCartStore.itemCount,
                        onAccountClick = {
                            if (tokenStore.isLoggedIn()) {
                                accountModalVisible = true
                            } else {
                                showLoginOptions = true
                            }
                        },
                        onFavoritesClick = { favoritesModalVisible = true },
                        onCartClick = { cartDrawerVisible = true }
                    )
                    GlobalFooter()
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
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
                    productModalHandleState = productModalHandleState,
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
                tokenStore = tokenStore
            )
        }
    }
    }
}
