package com.eazpire.creator.ui.header

import android.annotation.SuppressLint
import android.os.Message
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.eazpire.creator.EazColors
import com.eazpire.creator.util.DebugLog
import kotlin.math.roundToInt

private const val CART_STORE_URL = "https://www.eazpire.com"

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun CartWebView() {
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    userAgentString = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                }
                val wv = this
                CookieManager.getInstance().apply {
                    setAcceptCookie(true)
                    setAcceptThirdPartyCookies(wv, true)
                }
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                        val url = request?.url?.toString() ?: return false
                        view?.loadUrl(url)
                        return true
                    }
                }
                webChromeClient = object : WebChromeClient() {
                    override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean {
                        val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                        transport.setWebView(view)
                        resultMsg.sendToTarget()
                        return true
                    }
                }
                loadUrl("$CART_STORE_URL/cart")
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
fun CartDrawer(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!visible) return
    Dialog(
        onDismissRequest = {
            DebugLog.click("CartDrawer dismiss (backdrop)")
            onDismiss()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val drawerWidthPx = with(density) { (maxWidth * 0.85f).toPx() }
            var isEntered by remember { mutableStateOf(false) }
            var isExiting by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) { isEntered = true }

            val offsetXPx by animateFloatAsState(
                targetValue = when {
                    !isEntered -> drawerWidthPx
                    isExiting -> drawerWidthPx
                    else -> 0f
                },
                animationSpec = tween(300, easing = FastOutSlowInEasing)
            )

            LaunchedEffect(isExiting, offsetXPx) {
                if (isExiting && offsetXPx >= drawerWidthPx - 1f) {
                    onDismiss()
                }
            }

            fun dismissWithAnimation() {
                isExiting = true
            }

            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f))
                        .clickable {
                            DebugLog.click("CartDrawer backdrop")
                            dismissWithAnimation()
                        }
                )
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0.85f)
                        .align(Alignment.CenterEnd)
                        .offset { IntOffset(offsetXPx.roundToInt(), 0) }
                        .background(Color.White)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Warenkorb",
                            style = MaterialTheme.typography.titleLarge,
                            color = EazColors.TextPrimary
                        )
                        IconButton(onClick = {
                            DebugLog.click("CartDrawer close")
                            dismissWithAnimation()
                        }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Schließen",
                                tint = EazColors.TextPrimary
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        val shouldLoad = offsetXPx < 10f
                        if (shouldLoad) {
                            CartWebView()
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.White)
                            )
                        }
                    }
                }
            }
        }
    }
}
