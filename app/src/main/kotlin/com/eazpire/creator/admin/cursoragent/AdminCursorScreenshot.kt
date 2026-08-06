package com.eazpire.creator.admin.cursoragent

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume

object AdminCursorScreenshot {
    /**
     * Capture the activity window (page under the agent UI).
     * Prefers [PixelCopy]; falls back to View.draw.
     */
    suspend fun capturePng(activity: Activity): ByteArray? {
        val decor = activity.window?.decorView ?: return null
        if (decor.width <= 0 || decor.height <= 0) return null
        val bitmap =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                pixelCopy(activity, decor) ?: drawView(decor)
            } else {
                drawView(decor)
            } ?: return null
        return try {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
            out.toByteArray()
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    private fun drawView(view: View): Bitmap? {
        return try {
            val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            view.draw(canvas)
            bmp
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun pixelCopy(activity: Activity, view: View): Bitmap? =
        suspendCancellableCoroutine { cont ->
            val bmp =
                try {
                    Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                } catch (_: Exception) {
                    cont.resume(null)
                    return@suspendCancellableCoroutine
                }
            try {
                PixelCopy.request(
                    activity.window,
                    bmp,
                    { result ->
                        if (result == PixelCopy.SUCCESS) {
                            cont.resume(bmp)
                        } else {
                            if (!bmp.isRecycled) bmp.recycle()
                            cont.resume(null)
                        }
                    },
                    Handler(Looper.getMainLooper()),
                )
            } catch (_: Exception) {
                if (!bmp.isRecycled) bmp.recycle()
                cont.resume(null)
            }
        }
}
