package com.eazpire.creator.ui.footer

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.eazpire.creator.EazColors
import com.eazpire.creator.i18n.TranslationStore

/**
 * Tab ID → relative URL path (Shopify policies / pages)
 */
private fun policyPaths(baseUrl: String): List<Pair<String, String>> = listOf(
    "imprint" to "/pages/impressum",
    "privacy" to "/policies/privacy-policy",
    "terms" to "/policies/terms-of-service",
    "refund" to "/policies/refund-policy",
    "shipping" to "/policies/shipping-policy",
    "cookies" to "/pages/cookie-policy",
    "contact" to "/pages/contact"
)

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TermsModal(
    visible: Boolean,
    baseUrl: String,
    translationStore: TranslationStore?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!visible) return

    val t = translationStore?.let { { k: String, d: String -> it.t(k, d) } }
        ?: { _: String, d: String -> d }

    val tabs = listOf(
        "imprint" to t("eaz.footer.imprint", "Imprint"),
        "privacy" to t("eaz.footer.privacy", "Privacy"),
        "terms" to t("eaz.footer.terms", "Terms"),
        "refund" to t("eaz.footer.refund", "Refund"),
        "shipping" to t("eaz.footer.shipping", "Shipping"),
        "cookies" to t("eaz.footer.cookies", "Cookies"),
        "contact" to t("eaz.footer.help_contact", "Contact")
    )

    var selectedTab by remember { mutableStateOf("terms") }
    val paths = remember(baseUrl) { policyPaths(baseUrl) }
    val selectedPath = paths.find { it.first == selectedTab }?.second ?: "/policies/terms-of-service"
    val fullUrl = "$baseUrl$selectedPath"

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(Color.White)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = t("actions.close", "Close")
                    )
                }
                Text(
                    text = "Terms & Policies",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.Black
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .background(Color(0xFFF5F5F5))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                tabs.forEach { (tabId, label) ->
                    val isSelected = selectedTab == tabId
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isSelected) EazColors.Orange else Color.Gray,
                        modifier = Modifier
                            .clickable { selectedTab = tabId }
                            .background(
                                if (isSelected) EazColors.Orange.copy(alpha = 0.12f)
                                else Color.Transparent,
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    url: String?
                                ): Boolean = false
                            }
                        }
                    },
                    update = { webView ->
                        webView.loadUrl(fullUrl)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
