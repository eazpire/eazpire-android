package com.eazpire.creator.ui.header

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.i18n.LocalTranslationStore

/**
 * Full-screen search modal: search field as header (web mobile `#eazSearchModal`).
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
) {
    if (!visible) return

    val store = LocalTranslationStore.current
    val placeholder = store?.t("search.placeholder", "Search...") ?: "Search..."
    val closeLabel = store?.t("eaz.search.close_aria", "Close search") ?: "Close search"

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = closeLabel,
                        tint = EazColors.TextPrimary
                    )
                }
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
                    ownerId = ownerId,
                    creatorApi = creatorApi,
                    mockPreviewRevision = mockPreviewRevision,
                    placeholder = placeholder,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
