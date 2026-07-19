package com.eazpire.creator.ui.creator

import androidx.compose.runtime.Composable
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.chat.EazySidebarTab
import com.eazpire.creator.i18n.TranslationStore

/**
 * Temporary stubs so MarketingScreen compiles while full tool screens live under
 * `android/wip/creator-screens/` (missing CreatorApi methods).
 * Replace by moving WIP files back once API methods exist.
 */

@Composable
fun HeroImagesCreateModal(
    visible: Boolean,
    onDismiss: () -> Unit,
    tokenStore: SecureTokenStore,
    translationStore: TranslationStore,
    onHeroJobStarted: (jobId: String, summary: String) -> Unit = { _, _ -> },
    onOpenEazyChat: (EazySidebarTab) -> Unit = {},
    onHeroEazyReadyChange: (Boolean) -> Unit = {},
    headerStartNonce: Int = 0,
    onHeroGeneratingChange: (Boolean) -> Unit = {},
    showDockedComposeBar: Boolean = false,
    dockedComposeLoading: Boolean = false,
    onDockedComposeStart: () -> Unit = {},
) {
    if (visible) onDismiss()
}

@Composable
fun VideoGeneratorScreen(
    visible: Boolean,
    onDismiss: () -> Unit,
    tokenStore: SecureTokenStore,
    translationStore: TranslationStore,
    onVideoJobStarted: (jobId: String, summary: String) -> Unit = { _, _ -> },
) {
    if (visible) onDismiss()
}

@Composable
fun VideoStudioScreen(
    visible: Boolean,
    onDismiss: () -> Unit,
    tokenStore: SecureTokenStore,
    translationStore: TranslationStore,
) {
    if (visible) onDismiss()
}

@Composable
fun SocialMediaManagerScreen(
    visible: Boolean,
    onDismiss: () -> Unit,
    tokenStore: SecureTokenStore,
    translationStore: TranslationStore,
    oauthRefreshNonce: Int = 0,
) {
    if (visible) onDismiss()
}
