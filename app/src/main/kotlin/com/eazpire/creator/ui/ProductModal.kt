package com.eazpire.creator.ui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.debug.debugLog

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
    modifier: Modifier = Modifier
) {
    // #region agent log
    debugLog("ProductModal.kt:28", "ProductModal COMPOSING", mapOf("handle" to productHandle), "H3")
    // #endregion
    Log.d("ProductModalDebug", "[8] ProductModal COMPOSING: handle=$productHandle")
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        /** Nearly full height so inner Column/weight gets bounded max height (fixes bottom actions in sheet). */
        modifier = modifier.fillMaxWidth().fillMaxHeight(0.92f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
        ) {
            ProductDetailScreen(
                productHandle = productHandle,
                onBack = onDismiss,
                tokenStore = tokenStore,
                showCloseButton = true,
                onTermsClick = onTermsClick,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
