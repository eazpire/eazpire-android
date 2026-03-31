package com.eazpire.creator

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.auth.ShopSessionGuard
import com.eazpire.creator.debug.initDebugLog
import com.eazpire.creator.debug.initLangSwitchDebug
import com.eazpire.creator.chat.EazySidebarTab
import com.eazpire.creator.push.PushTokenRegistrar
import com.eazpire.creator.ui.ShopScreen

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_OPEN_NOTIFICATIONS = "eaz_open_notifications"
        const val EXTRA_OPEN_CART = "eaz_open_cart"
        const val EXTRA_OPEN_SHOP = "eaz_open_shop"
        const val EXTRA_OPEN_EAZY_CHAT = "eaz_open_eazy_chat"
        const val EXTRA_EAZY_TAB = "eaz_eazy_tab"
    }

    val pendingDeepLink = mutableStateOf<Uri?>(null)
    /** When non-null, open Eazy chat with this tab (from push / local notification tap). */
    val pendingEazyTab = mutableStateOf<EazySidebarTab?>(null)
    val pendingOpenCart = mutableStateOf(false)
    /** From FCM open_target=shop — opens main shop (no Eazy overlay). */
    val pendingOpenShop = mutableStateOf(false)

    private val notifPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) PushTokenRegistrar.syncIfLoggedIn(this)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initDebugLog(this)
        initLangSwitchDebug(this)
        val tokenStore = SecureTokenStore(this)
        if (ShopSessionGuard.shouldLogoutSync(tokenStore)) {
            ShopSessionGuard.performFullLogout(this, tokenStore)
        }
        pendingDeepLink.value = intent?.data
        consumeIntentExtras(intent)
        requestNotificationPermissionAndSyncPush()
        setContent {
            EazpireCreatorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                    tonalElevation = 0.dp
                ) {
                    ShopScreen(
                        tokenStore = tokenStore,
                        pendingDeepLink = pendingDeepLink,
                        pendingEazyTab = pendingEazyTab,
                        pendingOpenCart = pendingOpenCart,
                        pendingOpenShop = pendingOpenShop
                    )
                }
            }
        }
        handleOAuthCallback(intent, tokenStore)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.data?.let { pendingDeepLink.value = it }
        consumeIntentExtras(intent)
        handleOAuthCallback(intent, SecureTokenStore(this))
    }

    private fun consumeIntentExtras(intent: Intent?) {
        if (intent == null) return
        if (intent.getBooleanExtra(EXTRA_OPEN_CART, false)) {
            pendingOpenCart.value = true
        }
        if (intent.getBooleanExtra(EXTRA_OPEN_SHOP, false)) {
            pendingOpenShop.value = true
        }
        val tabName = intent.getStringExtra(EXTRA_EAZY_TAB)
        if (tabName != null) {
            EazySidebarTab.entries.find { it.name == tabName }?.let { pendingEazyTab.value = it }
        } else if (intent.getBooleanExtra(EXTRA_OPEN_NOTIFICATIONS, false)) {
            pendingEazyTab.value = EazySidebarTab.Notifications
        } else if (intent.getBooleanExtra(EXTRA_OPEN_EAZY_CHAT, false)) {
            pendingEazyTab.value = EazySidebarTab.Notifications
        }
    }

    private fun requestNotificationPermissionAndSyncPush() {
        if (Build.VERSION.SDK_INT >= 33) {
            when {
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED -> {
                    PushTokenRegistrar.syncIfLoggedIn(this)
                }
                else -> {
                    notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            PushTokenRegistrar.syncIfLoggedIn(this)
        }
    }

    private fun handleOAuthCallback(intent: Intent?, tokenStore: SecureTokenStore) {
        val data = intent?.data ?: return
        if (data.scheme?.startsWith("shop.") == true && data.host == "callback") {
            // OAuth-Callback via Deep Link (falls CustomTabs statt WebView genutzt wird)
            val code = data.getQueryParameter("code")
            val state = data.getQueryParameter("state")
            if (code != null && state != null) {
                // State/Verifier müssten hier aus einem temporären Store kommen
            }
        }
    }
}
