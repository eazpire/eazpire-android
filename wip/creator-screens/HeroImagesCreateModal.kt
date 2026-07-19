package com.eazpire.creator.ui.creator

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.chat.EazySidebarTab
import com.eazpire.creator.i18n.TranslationStore
import com.eazpire.creator.ui.account.AccountHeroImagesTab
import com.eazpire.creator.ui.modal.EazFullScreenDialog

/**
 * Fullscreen Hero Images creation modal — native parity with the marketing web
 * "Hero Images" content-creation tab (`creator-mobile-marketing.liquid`).
 *
 * Wraps the existing [AccountHeroImagesTab] (already 1:1 with web) in a dark
 * fullscreen dialog shell with header (title + close), matching the pattern
 * used by [com.eazpire.creator.ui.creator.CreatorDetailModal] / `CreatorJourneyModal`.
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
    /** Increment from header "Start generation" bubble (Creator flow). */
    headerStartNonce: Int = 0,
    onHeroGeneratingChange: (Boolean) -> Unit = {},
    showDockedComposeBar: Boolean = false,
    dockedComposeLoading: Boolean = false,
    onDockedComposeStart: () -> Unit = {},
) {
    if (!visible) return

    fun t(key: String, fallback: String): String = translationStore.t(key, fallback)

    EazFullScreenDialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .background(Color(0xFF0B1220))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F172A))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Text(
                    text = t("creator.marketing.hero_images", "Hero Images"),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                AccountHeroImagesTab(
                    tokenStore = tokenStore,
                    translationStore = translationStore,
                    darkMode = true,
                    onHeroJobStarted = onHeroJobStarted,
                    onOpenEazyChat = onOpenEazyChat,
                    onHeroEazyReadyChange = onHeroEazyReadyChange,
                    headerStartNonce = headerStartNonce,
                    onHeroGeneratingChange = onHeroGeneratingChange,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = if (showDockedComposeBar) 88.dp else 0.dp)
                )
                if (showDockedComposeBar) {
                    CreatorDockedComposeFloatingBar(
                        visible = true,
                        loading = dockedComposeLoading,
                        onStart = onDockedComposeStart,
                        translationStore = translationStore,
                        modifier = Modifier.align(Alignment.BottomEnd)
                    )
                }
            }
        }
    }
}
