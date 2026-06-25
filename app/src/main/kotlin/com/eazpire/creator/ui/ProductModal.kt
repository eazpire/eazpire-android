package com.eazpire.creator.ui

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import com.eazpire.creator.ui.modal.EazBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.eazpire.creator.ar.poster.PosterArSessionConfig
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.debug.debugLog
import com.eazpire.creator.ui.header.FavoriteEditContext

/**
 * Produkt-Modal für Hero-Hotspot-Klicks.
 * Zeigt ProductDetailScreen in einer ModalBottomSheet (slide-up von unten).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductModal(
    productHandle: String,
    onDismiss: () -> Unit,
    tokenStore: SecureTokenStore,
    onTermsClick: (() -> Unit)? = null,
    onNavigateToCreator: ((String) -> Unit)? = null,
    onNavigateToProduct: ((String) -> Unit)? = null,
    onPosterArOpen: ((PosterArSessionConfig) -> Unit)? = null,
    favoriteEdit: FavoriteEditContext? = null,
    modifier: Modifier = Modifier
) {
    // #region agent log
    debugLog("ProductModal.kt:28", "ProductModal COMPOSING", mapOf("handle" to productHandle), "H3")
    // #endregion
    Log.d("ProductModalDebug", "[8] ProductModal COMPOSING: handle=$productHandle")
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    EazBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        modifier = modifier.fillMaxWidth(),
        fullscreen = true,
    ) {
        Box(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
            ProductDetailScreen(
                productHandle = productHandle,
                onBack = onDismiss,
                tokenStore = tokenStore,
                showCloseButton = true,
                onTermsClick = onTermsClick,
                onNavigateToCreator = onNavigateToCreator,
                onNavigateToProduct = onNavigateToProduct,
                onPosterArOpen = onPosterArOpen,
                favoriteEdit = favoriteEdit,
                modifier = Modifier.fillMaxWidth().fillMaxHeight()
            )
        }
    }
}
