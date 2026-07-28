package com.eazpire.creator.ui.header

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.i18n.LocalTranslationStore

/**
 * Full-screen search modal (web mobile `#eazSearchModal`):
 * search field + close X on one header row, results fill the rest of the screen.
 */
@Composable
fun HeaderSearchModal(
    visible: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSubmitSearchQuery: (String) -> Unit,
    onNavigateToUrl: (String) -> Unit,
    ownerId: String = "",
    creatorApi: CreatorApi? = null,
    mockPreviewRevision: Int = 0,
    onCreateProductFromRefSearch: (RefSearchCreateProductRequest) -> Unit = {},
) {
    if (!visible) return

    val store = LocalTranslationStore.current
    val placeholder = store?.t("search.placeholder", "Search...") ?: "Search..."

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            HeaderSearch(
                query = query,
                onQueryChange = onQueryChange,
                onSearch = {},
                onSubmitSearchQuery = { q ->
                    onSubmitSearchQuery(q)
                    onDismiss()
                },
                onNavigateToUrl = { url ->
                    onNavigateToUrl(url)
                    onDismiss()
                },
                onClose = onDismiss,
                ownerId = ownerId,
                creatorApi = creatorApi,
                mockPreviewRevision = mockPreviewRevision,
                placeholder = placeholder,
                fullscreen = true,
                onCreateProductFromRefSearch = { req ->
                    onCreateProductFromRefSearch(req)
                    onDismiss()
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
