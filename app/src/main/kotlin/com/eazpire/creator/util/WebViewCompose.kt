package com.eazpire.creator.util

import android.view.ViewGroup
import android.webkit.WebView

/** Release WebView resources when Compose disposes an [AndroidView]. */
fun WebView.releaseForCompose() {
    stopLoading()
    loadUrl("about:blank")
    clearHistory()
    removeAllViews()
    (parent as? ViewGroup)?.removeView(this)
    destroy()
}
