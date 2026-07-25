package com.eazpire.creator.ui.footer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.eazpire.creator.i18n.TranslationStore

/**
 * Shop footer — always visible (collapse tab removed; matches web).
 * Kept as a thin wrapper for existing call sites.
 */
@Composable
fun CollapsibleShopFooter(
    translationStore: TranslationStore? = null,
    onTermsClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    @Suppress("UNUSED_PARAMETER")
    val _unusedTs = translationStore
    Column(modifier = modifier.fillMaxWidth()) {
        GlobalFooter(onTermsClick = onTermsClick)
    }
}
