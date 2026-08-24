package com.eazpire.creator.ui.creator

import android.content.Context
import android.widget.Toast

/**
 * One on-screen info toast at a time. A new click cancels the previous toast
 * immediately so rapid taps never queue delayed messages.
 */
object ReplaceInfoToast {
    private var current: Toast? = null

    fun show(context: Context, text: String) {
        val msg = text.trim()
        if (msg.isEmpty()) return
        current?.cancel()
        current = Toast.makeText(context.applicationContext, msg, Toast.LENGTH_SHORT).also { it.show() }
    }
}
