package com.eazpire.creator

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.eazpire.creator.admin.cursoragent.AdminCursorAgentHost
import com.eazpire.creator.ar.poster.PosterArOverlay
import com.eazpire.creator.ar.poster.PosterArSessionConfig
import androidx.browser.customtabs.CustomTabsIntent
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.auth.ShopSessionCoordinator
import com.eazpire.creator.debug.AuthDebugLog
import com.eazpire.creator.debug.initDebugLog
import com.eazpire.creator.debug.initLangSwitchDebug
import com.eazpire.creator.chat.ArtifactsJson
import com.eazpire.creator.chat.EazySidebarTab
import com.eazpire.creator.push.PushTokenRegistrar
import androidx.lifecycle.lifecycleScope
import com.eazpire.creator.ui.ShopScreen
import com.eazpire.creator.update.PlayInAppUpdateHelper
import com.eazpire.creator.api.WearPairApi
import com.eazpire.creator.perf.EazPerfTrace
import com.eazpire.creator.wear.sync.WearAuthSync
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_OPEN_NOTIFICATIONS = "eaz_open_notifications"
        const val EXTRA_OPEN_CART = "eaz_open_cart"
        const val EXTRA_OPEN_SHOP = "eaz_open_shop"
        const val EXTRA_OPEN_EAZY_CHAT = "eaz_open_eazy_chat"
        const val EXTRA_EAZY_TAB = "eaz_eazy_tab"
        const val EXTRA_GAMES_SECTION = "eaz_games_section"
        const val EXTRA_TRADE_OFFER_ID = "eaz_trade_offer_id"
        /** Creator → My Creations → Designs → Inactive (Wear upload complete). */
        const val EXTRA_OPEN_CREATOR_INACTIVE_DESIGNS = "eaz_open_creator_inactive_designs"
        /** Creator Settings → Creator Codes (invite / redeemed push). */
        const val EXTRA_OPEN_CREATOR_CODES = "eaz_open_creator_codes"
        const val EXTRA_CREATOR_CODE_PREFILL = "eaz_creator_code_prefill"
        /** Publish Assist pending sheet (IDEA-050). */
        const val EXTRA_OPEN_PUBLISH_ASSIST = "eaz_open_publish_assist"
        const val EXTRA_PUBLISH_ASSIST_TAB = "eaz_publish_assist_tab"
        /** Wallet → Gift Cards → Won (app install bonus push). */
        const val EXTRA_OPEN_GIFT_CARDS_WON = "eaz_open_gift_cards_won"
    }

    data class PendingCreatorCodesNav(val prefillCode: String? = null)
    data class PendingPublishAssistNav(val tab: String = "pending")

    val pendingDeepLink = mutableStateOf<Uri?>(null)
    /** When non-null, open Eazy chat with this tab (from push / local notification tap). */
    val pendingEazyTab = mutableStateOf<EazySidebarTab?>(null)
    val pendingOpenCart = mutableStateOf(false)
    /** From FCM open_target=shop — opens main shop (no Eazy overlay). */
    val pendingOpenShop = mutableStateOf(false)
    /** From eazpire://wear-pair or /wear-pair deep link — opens Creator Settings → Wear + claim. */
    val pendingWearPairToken = mutableStateOf<String?>(null)
    /** Incremented when Social Media Manager OAuth returns via eazpire://smm-oauth-callback. */
    val pendingSmmOAuthRefresh = mutableStateOf(0)
    /** From /artifacts/claim?t=… — opens Eazy Artifacts tab and claims slot NFT. */
    val pendingArtifactClaimToken = mutableStateOf<String?>(null)
    /** Optional open Games tab in specific section (e.g. collection). */
    val pendingGamesSection = mutableStateOf<String?>(null)
    /** Optional open trade offer detail inside Games collection panel. */
    val pendingTradeOfferId = mutableStateOf<Int?>(null)
    /** Wear upload finished — open Creator creations, inactive designs tab. */
    val pendingCreatorInactiveDesigns = mutableStateOf(false)
    /** Creator Settings → Creator Codes (from FCM / in-app notification). */
    val pendingCreatorCodesNav = mutableStateOf<PendingCreatorCodesNav?>(null)
    val pendingPublishAssistNav = mutableStateOf<PendingPublishAssistNav?>(null)
    val pendingOpenGiftCardsWon = mutableStateOf(false)
    /** Hoisted above ShopScreen — avoids Dialog+BottomSheet conflicts when opening Poster AR. */
    val posterArSessionConfig = mutableStateOf<PosterArSessionConfig?>(null)

    private val notifPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) PushTokenRegistrar.syncIfLoggedIn(this)
        }

    private val playInAppUpdateLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { _ -> }

    private lateinit var playInAppUpdateHelper: PlayInAppUpdateHelper
    private val playUpdateHandler = Handler(Looper.getMainLooper())
    private val playUpdateRetryRunnable = Runnable {
        if (isFinishing || isDestroyed) return@Runnable
        playInAppUpdateHelper.onResume()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EazPerfTrace.mark("mainActivity_onCreate_start")
        // API 35+/36: Edge-to-edge is enforced for targetSdk 36 (opt-out removed).
        // Insets are applied in Compose on the root surface (systemBarsPadding).
        WindowCompat.setDecorFitsSystemWindows(window, false)
        initDebugLog(this)
        initLangSwitchDebug(this)
        val tokenStore = SecureTokenStore.get(this)
        EazPerfTrace.mark("mainActivity_tokenStore_ready")
        AuthDebugLog.d("[TOKEN] App start ${tokenStore.sessionDebugSummary()}")
        // Session refresh off main thread — avoids ANR/crash on slow network at cold start.
        lifecycleScope.launch {
            try {
                EazPerfTrace.measureSectionSuspend("MainActivity.sessionGuard") {
                    ShopSessionCoordinator.refreshSession(
                        this@MainActivity,
                        tokenStore,
                        reason = "cold_start",
                        force = false,
                    )
                }
                AuthDebugLog.d("[TOKEN] After session guard ${tokenStore.sessionDebugSummary()}")
            } catch (e: Exception) {
                AuthDebugLog.d("[TOKEN] Session guard skipped on start: ${e.message}")
            } finally {
                ShopSessionCoordinator.syncPushIfLoggedIn(this@MainActivity)
            }
            EazPerfTrace.mark("mainActivity_sessionGuard_done")
        }

        if (!tokenStore.getJwt().isNullOrBlank()) {
            WearAuthSync.push(this, tokenStore)
        }
        pendingDeepLink.value = intent?.data
        consumeWearPairDeepLink(intent)
        consumePhoneUploadDeepLink(intent)
        consumeArtifactsClaimDeepLink(intent)
        consumeSmmOAuthDeepLink(intent)
        consumeIntentExtras(intent)
        requestNotificationPermissionAndSyncPush()
        playInAppUpdateHelper = PlayInAppUpdateHelper(this, playInAppUpdateLauncher)
        EazPerfTrace.mark("mainActivity_setContent")
        setContent {
            val activePosterAr = posterArSessionConfig.value
            EazpireCreatorTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .systemBarsPadding(),
                    color = MaterialTheme.colorScheme.background,
                    tonalElevation = 0.dp,
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        ShopScreen(
                            tokenStore = tokenStore,
                            pendingDeepLink = pendingDeepLink,
                            pendingEazyTab = pendingEazyTab,
                            pendingOpenCart = pendingOpenCart,
                            pendingOpenShop = pendingOpenShop,
                            pendingWearPairToken = pendingWearPairToken,
                            pendingArtifactClaimToken = pendingArtifactClaimToken,
                            pendingGamesSection = pendingGamesSection,
                            pendingTradeOfferId = pendingTradeOfferId,
                            pendingCreatorInactiveDesigns = pendingCreatorInactiveDesigns,
                            pendingCreatorCodesNav = pendingCreatorCodesNav,
                            pendingPublishAssistNav = pendingPublishAssistNav,
                            pendingOpenGiftCardsWon = pendingOpenGiftCardsWon,
                            posterArActive = activePosterAr != null,
                            onPosterArOpen = { posterArSessionConfig.value = it },
                        )
                        activePosterAr?.let { config ->
                            PosterArOverlay(
                                config = config,
                                onDismiss = { posterArSessionConfig.value = null },
                            )
                        }
                        // Admin-only Cursor Agent FAB (sub-panel window above Dialog modals).
                        AdminCursorAgentHost(
                            activity = this@MainActivity,
                            tokenStore = tokenStore,
                        )
                    }
                }
            }
        }
        handleOAuthCallback(intent)
    }

    override fun onResume() {
        super.onResume()
        playInAppUpdateHelper.onResume()
        // Wear OS: re-push session when reviewer returns from phone login (Data Layer).
        val tokenStore = SecureTokenStore.get(this)
        if (!tokenStore.getJwt().isNullOrBlank()) {
            WearAuthSync.push(this, tokenStore)
        }
        // Play Core ist manchmal beim ersten Frame noch nicht bereit — zweite Prüfung nach kurzer Verzögerung.
        playUpdateHandler.removeCallbacks(playUpdateRetryRunnable)
        playUpdateHandler.postDelayed(playUpdateRetryRunnable, 2_500L)
    }

    override fun onPause() {
        playUpdateHandler.removeCallbacks(playUpdateRetryRunnable)
        super.onPause()
    }

    override fun onDestroy() {
        playUpdateHandler.removeCallbacks(playUpdateRetryRunnable)
        playInAppUpdateHelper.onDestroy()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.data?.let { pendingDeepLink.value = it }
        consumeWearPairDeepLink(intent)
        consumePhoneUploadDeepLink(intent)
        consumeArtifactsClaimDeepLink(intent)
        consumeSmmOAuthDeepLink(intent)
        consumeIntentExtras(intent)
        handleOAuthCallback(intent)
    }

    private fun consumeSmmOAuthDeepLink(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "eazpire" && data.host == "smm-oauth-callback") {
            AuthDebugLog.d("[SMM OAUTH] callback ok=${data.getQueryParameter("ok")} channel=${data.getQueryParameter("channel")}")
            pendingSmmOAuthRefresh.value = pendingSmmOAuthRefresh.value + 1
        }
    }

    private fun consumeWearPairDeepLink(intent: Intent?) {
        val data = intent?.data ?: return
        val path = data.path.orEmpty()
        if (path.contains("artifacts/claim")) return
        val token = WearPairApi.parseTokenFromQrPayload(data.toString())
            ?: data.getQueryParameter("t")?.trim()?.takeIf { it.isNotBlank() }
        if (token != null) pendingWearPairToken.value = token
    }

    /**
     * Add-from-link / phone-upload QR → "App" choice.
     * Opens the worker paste+Extract page in a Custom Tab (same UI as Browser path).
     */
    private fun consumePhoneUploadDeepLink(intent: Intent?) {
        val data = intent?.data ?: return
        val sessionId = data.getQueryParameter("s")?.trim().orEmpty()
        val isPhoneUploadScheme = data.scheme == "eazpire" && data.host == "phone-upload"
        val isLegacyPhoneQr =
            data.scheme == "https" &&
                data.host == "creator-engine.eazpire.workers.dev" &&
                data.path?.startsWith("/q/") == true &&
                sessionId.isNotBlank()
        if (!isPhoneUploadScheme && !isLegacyPhoneQr) return
        if (sessionId.isBlank()) return
        val page = Uri.parse(
            "https://creator-engine.eazpire.workers.dev/creator-phone-upload?s=" +
                Uri.encode(sessionId),
        )
        try {
            CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(this, page)
        } catch (e: Exception) {
            AuthDebugLog.d("[PHONE UPLOAD] Custom Tab failed: ${e.message}")
            try {
                startActivity(Intent(Intent.ACTION_VIEW, page))
            } catch (_: Exception) {
                // ignore
            }
        }
    }

    private fun consumeArtifactsClaimDeepLink(intent: Intent?) {
        val data = intent?.data ?: return
        val raw = data.toString()

        // Phone-upload sessions use /q/{token}?s=… — never treat those as artifact claims.
        val phoneSession = data.getQueryParameter("s")?.trim().orEmpty()
        if (phoneSession.isNotBlank() && raw.contains("/q/")) return

        data.getQueryParameter("artifact_token")?.trim()?.takeIf { it.isNotBlank() }?.let { token ->
            pendingArtifactClaimToken.value = token
            pendingEazyTab.value = EazySidebarTab.Artifacts
            return
        }
        if (data.getQueryParameter("eazy") == "artifacts") {
            data.getQueryParameter("t")?.trim()?.takeIf { it.isNotBlank() }?.let { token ->
                pendingArtifactClaimToken.value = token
                pendingEazyTab.value = EazySidebarTab.Artifacts
                return
            }
            data.getQueryParameter("token")?.trim()?.takeIf { it.isNotBlank() }?.let { token ->
                pendingArtifactClaimToken.value = token
                pendingEazyTab.value = EazySidebarTab.Artifacts
                return
            }
        }

        if (!raw.contains("artifacts/claim") && !raw.contains("artifacts%2Fclaim") && !raw.contains("/q/")) return
        val token = ArtifactsJson.parseClaimToken(raw)
            ?: data.getQueryParameter("t")?.trim()?.takeIf { it.isNotBlank() }
            ?: data.getQueryParameter("token")?.trim()?.takeIf { it.isNotBlank() }
        if (token != null) {
            pendingArtifactClaimToken.value = token
            pendingEazyTab.value = EazySidebarTab.Artifacts
        }
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
        intent.getStringExtra(EXTRA_GAMES_SECTION)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { pendingGamesSection.value = it }
        if (intent.hasExtra(EXTRA_TRADE_OFFER_ID)) {
            val id = intent.getIntExtra(EXTRA_TRADE_OFFER_ID, 0)
            pendingTradeOfferId.value = id.takeIf { it > 0 }
        }
        if (intent.getBooleanExtra(EXTRA_OPEN_CREATOR_INACTIVE_DESIGNS, false)) {
            pendingCreatorInactiveDesigns.value = true
        }
        if (intent.getBooleanExtra(EXTRA_OPEN_CREATOR_CODES, false)) {
            val prefill = intent.getStringExtra(EXTRA_CREATOR_CODE_PREFILL)?.trim()?.takeIf { it.isNotBlank() }
            pendingCreatorCodesNav.value = PendingCreatorCodesNav(prefillCode = prefill)
        }
        if (intent.getBooleanExtra(EXTRA_OPEN_PUBLISH_ASSIST, false)) {
            val tab = intent.getStringExtra(EXTRA_PUBLISH_ASSIST_TAB)?.trim()?.ifBlank { "pending" } ?: "pending"
            pendingPublishAssistNav.value = PendingPublishAssistNav(tab = tab)
        }
        if (intent.getBooleanExtra(EXTRA_OPEN_GIFT_CARDS_WON, false)) {
            pendingOpenGiftCardsWon.value = true
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

    private fun handleOAuthCallback(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme?.startsWith("shop.") == true && data.host == "callback") {
            AuthDebugLog.d("[MAIN CALLBACK] Forwarding OAuth callback to ShopScreen via pendingDeepLink: $data")
            // Actual handling happens in ShopScreen -> oauthCallbackForAuth -> AuthScreen.
            // Do not exchange tokens here, otherwise state/verifier ownership becomes split.
        }
        consumeSmmOAuthDeepLink(intent)
    }
}
