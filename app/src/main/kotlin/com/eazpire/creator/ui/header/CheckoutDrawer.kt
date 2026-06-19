package com.eazpire.creator.ui.header

import android.annotation.SuppressLint
import android.os.Message
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.eazpire.creator.EazColors
import com.eazpire.creator.ui.modal.EazModalSheetLayout
import com.eazpire.creator.ui.modal.EazSideDrawer
import com.eazpire.creator.util.releaseForCompose

/** CSS to hide Shopify branding – checkout feels native in-app */
private val HIDE_SHOPIFY_BRANDING_CSS = """
(function() {
  var style = document.createElement('style');
  style.textContent = [
    '[class*="shopify"][class*="footer"],',
    '[class*="shopify"][class*="branding"],',
    '.shopify-challenge__container,',
    '[data-shopify="footer"],',
    'footer [class*="shopify"],',
    'a[href*="shopify.com"]:not([href*="checkout"]),',
    '[class*="powered-by"],',
    '[id*="shopify"]'
  ].join(' ') + ' { display: none !important; visibility: hidden !important; }';
  document.head.appendChild(style);
})();
""".trimIndent()

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CheckoutDrawer(
    visible: Boolean,
    checkoutUrl: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible || checkoutUrl.isBlank()) return

    EazSideDrawer(
        onDismissRequest = onDismiss,
        modifier = modifier,
        widthFraction = 0.92f,
        backdropColor = Color.Black.copy(alpha = 0.35f),
    ) { dismissAnimated ->
        var webViewReady by remember { mutableStateOf(false) }
        EazModalSheetLayout(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(),
            header = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Checkout",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = EazColors.TextPrimary,
                    )
                    IconButton(onClick = { dismissAnimated() }) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = EazColors.TextPrimary)
                    }
                }
            },
            body = {
                Box(modifier = Modifier.fillMaxSize()) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                layoutParams = FrameLayout.LayoutParams(
                                    FrameLayout.LayoutParams.MATCH_PARENT,
                                    FrameLayout.LayoutParams.MATCH_PARENT,
                                )
                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    databaseEnabled = true
                                    mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                                    cacheMode = WebSettings.LOAD_DEFAULT
                                    userAgentString =
                                        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                                }
                                val wv = this
                                CookieManager.getInstance().apply {
                                    setAcceptCookie(true)
                                    setAcceptThirdPartyCookies(wv, true)
                                }
                                webChromeClient = object : WebChromeClient() {
                                    override fun onCreateWindow(
                                        view: WebView?,
                                        isDialog: Boolean,
                                        isUserGesture: Boolean,
                                        resultMsg: Message?,
                                    ): Boolean {
                                        val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                                        transport.setWebView(view)
                                        resultMsg.sendToTarget()
                                        return true
                                    }
                                }
                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        request: android.webkit.WebResourceRequest?,
                                    ): Boolean {
                                        val url = request?.url?.toString() ?: return false
                                        view?.loadUrl(url)
                                        return true
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        val isCheckout = url != null && url.contains("checkout")
                                        if (isCheckout) {
                                            view?.evaluateJavascript(HIDE_SHOPIFY_BRANDING_CSS, null)
                                            webViewReady = true
                                        }
                                    }
                                }
                                loadUrl(checkoutUrl)
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        onRelease = { it.releaseForCompose() },
                    )
                    if (!webViewReady) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.White),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                CircularProgressIndicator(
                                    color = EazColors.Orange,
                                    modifier = Modifier.padding(8.dp),
                                )
                                Text(
                                    text = "Loading checkout…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = EazColors.TextSecondary,
                                )
                            }
                        }
                    }
                }
            },
        )
    }
}
