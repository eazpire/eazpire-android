package com.eazpire.creator.ui.footer

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.eazpire.creator.EazColors
import com.eazpire.creator.i18n.TranslationStore

/**
 * Tab ID → relative URL path (Shopify policies / pages).
 * Matches web eaz-terms-popup.liquid tabs.
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

private fun normalizeBaseUrl(baseUrl: String): String {
    val trimmed = baseUrl.trim()
    return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed
    else "https://$trimmed"
}

/** Light theme colors for Shop */
private val LightBg = Color.White
private val LightText = Color(0xFF1a1a1a)
private val LightTextSecondary = Color(0xFF6b7280)
private val LightDivider = Color(0xFFe5e7eb)

/** Dark theme colors for Creator */
private val DarkBg = Color(0xFF0A0514)
private val DarkText = Color(0xFFf3f4f6)
private val DarkTextSecondary = Color(0xFF9ca3af)
private val DarkDivider = Color(0xFF374151)

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TermsModal(
    visible: Boolean,
    baseUrl: String,
    translationStore: TranslationStore?,
    onDismiss: () -> Unit,
    isDarkMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (!visible) return

    val context = LocalContext.current
    val t = translationStore?.let { { k: String, d: String -> it.t(k, d) } }
        ?: { _: String, d: String -> d }

    val bgColor = if (isDarkMode) DarkBg else LightBg
    val textColor = if (isDarkMode) DarkText else LightText
    val textSecondaryColor = if (isDarkMode) DarkTextSecondary else LightTextSecondary
    val dividerColor = if (isDarkMode) DarkDivider else LightDivider

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
    val normalizedBase = remember(baseUrl) { normalizeBaseUrl(baseUrl) }
    val paths = remember(normalizedBase) { policyPaths(normalizedBase) }
    val selectedPath = paths.find { it.first == selectedTab }?.second ?: "/policies/terms-of-service"
    val fullUrl = "$normalizedBase$selectedPath"

    var contentHtml by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf(false) }

    LaunchedEffect(selectedTab, fullUrl, isDarkMode) {
        contentHtml = null
        isLoading = true
        loadError = false
        val html = PolicyContentFetcher.fetchContent(fullUrl, darkMode = isDarkMode)
        contentHtml = html
        isLoading = false
        loadError = html == null
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = bgColor,
        modifier = modifier.fillMaxHeight(0.95f)
    ) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(bgColor)
        ) {
            // Header – title left, close right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bgColor)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = t("eaz.footer.terms_policies", "Terms & Policies"),
                    style = MaterialTheme.typography.titleMedium,
                    color = textColor
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = t("actions.close", "Close"),
                        tint = textColor
                    )
                }
            }

            // Tabs – native, matches web eaz-terms-modal__tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .background(bgColor)
                    .padding(horizontal = 24.dp, vertical = 0.dp),
                horizontalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                tabs.forEach { (tabId, label) ->
                    val isSelected = selectedTab == tabId
                    Column(
                        modifier = Modifier
                            .clickable { selectedTab = tabId }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isSelected) EazColors.Orange else textSecondaryColor
                        )
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 8.dp)
                                    .fillMaxWidth()
                                    .background(EazColors.Orange, RoundedCornerShape(2.dp))
                                    .padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // Divider under tabs
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(dividerColor)
                    .padding(vertical = 1.dp)
            )

            // Content area – WebView for HTML content
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bgColor)
            ) {
                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = EazColors.Orange)
                        }
                    }
                    loadError || contentHtml == null -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = t("eaz.footer.content_unavailable", "This content is not available yet."),
                                style = MaterialTheme.typography.bodyMedium,
                                color = textSecondaryColor
                            )
                        }
                    }
                    else -> {
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    settings.javaScriptEnabled = false
                                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                    webViewClient = object : WebViewClient() {
                                        override fun shouldOverrideUrlLoading(
                                            view: WebView?,
                                            request: android.webkit.WebResourceRequest?
                                        ): Boolean {
                                            val url = request?.url?.toString()
                                            if (url != null && !url.startsWith("about:blank")) {
                                                try {
                                                    context.startActivity(
                                                        Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                                    )
                                                } catch (_: Exception) {}
                                                return true
                                            }
                                            return false
                                        }
                                    }
                                }
                            },
                            update = { webView ->
                                contentHtml?.let { html ->
                                    webView.loadDataWithBaseURL(
                                        normalizedBase,
                                        html,
                                        "text/html",
                                        "UTF-8",
                                        null
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}
