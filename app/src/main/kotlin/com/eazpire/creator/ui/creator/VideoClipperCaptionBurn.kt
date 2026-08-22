@file:OptIn(UnstableApi::class)

package com.eazpire.creator.ui.creator

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.text.Layout
import android.text.Spannable
import android.text.SpannableString
import android.text.style.AbsoluteSizeSpan
import android.text.style.AlignmentSpan
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.OverlaySettings
import androidx.media3.effect.Presentation
import androidx.media3.effect.TextOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

internal object VideoClipperCaptionBurn {
    suspend fun exportClip(
        context: Context,
        uri: Uri,
        startS: Double,
        endS: Double,
        outFile: File,
        words: List<ClipperWord>,
        style: ClipperCaptionStyle,
    ): Boolean {
        val startMs = (startS * 1000).toLong().coerceAtLeast(0L)
        val endMs = (endS * 1000).toLong().coerceAtLeast(startMs + 200L)
        val blocks = VideoClipperCaptions.buildBlocks(words, style.wordsPer, style.lines, startS, endS)
        val overlay = object : TextOverlay() {
            override fun getText(presentationTimeUs: Long): SpannableString {
                val timeS = startS + presentationTimeUs / 1_000_000.0
                val block = VideoClipperCaptions.atTime(blocks, timeS)
                val shown = VideoClipperCaptions.visibleText(block, timeS, style.animation)
                if (shown.isBlank() || block == null) return SpannableString("")
                val progress = VideoClipperCaptions.animProgress(block, timeS, style.animation)
                val fade = if (style.animation == "none") 1f else progress
                val span = SpannableString(shown)
                val end = span.length
                span.setSpan(AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER), 0, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                span.setSpan(StyleSpan(Typeface.BOLD), 0, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                span.setSpan(AbsoluteSizeSpan(28, true), 0, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                span.setSpan(ForegroundColorSpan(withAlpha(style.color, fade)), 0, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                span.setSpan(TypefaceSpan(overlayFontFamily(style.font)), 0, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (style.bgMode == "color") {
                    span.setSpan(BackgroundColorSpan(withAlpha(style.bgColor, fade)), 0, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                return span
            }

            override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
                val timeS = startS + presentationTimeUs / 1_000_000.0
                val block = VideoClipperCaptions.atTime(blocks, timeS)
                val progress = VideoClipperCaptions.animProgress(block, timeS, style.animation)
                var extraScale = 1f
                var dy = 0f
                if (style.animation == "slide_up") dy = (1f - progress) * 0.07f
                if (style.animation == "pop") extraScale = 0.86f + 0.14f * progress
                val x = (style.x - 0.5f) * 2f
                val y = (0.5f - style.y) * 2f - dy
                return OverlaySettings.Builder()
                    .setBackgroundFrameAnchor(x, y)
                    .setOverlayFrameAnchor(0f, 0f)
                    .setScale(style.scale * extraScale, style.scale * extraScale)
                    .setRotationDegrees(style.rotation)
                    .build()
            }
        }
        if (outFile.exists()) outFile.delete()
        val deferred = CompletableDeferred<Boolean>()
        withContext(Dispatchers.Main) {
            val transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .build()
            transformer.addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    deferred.complete(true)
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException,
                ) {
                    deferred.complete(false)
                }
            })
            val mediaItem = MediaItem.Builder()
                .setUri(uri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(startMs)
                        .setEndPositionMs(endMs)
                        .build(),
                )
                .build()
            val edited = EditedMediaItem.Builder(mediaItem)
                .setEffects(
                    Effects(
                        emptyList(),
                        listOf(
                            Presentation.createForWidthAndHeight(
                                1080,
                                1920,
                                Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP,
                            ),
                            OverlayEffect(listOf(overlay)),
                        ),
                    ),
                )
                .build()
            transformer.start(edited, outFile.absolutePath)
        }
        return try {
            withTimeout(180_000) { deferred.await() } && outFile.exists() && outFile.length() > 0L
        } catch (_: Exception) {
            false
        }
    }

    private fun withAlpha(argb: Long, alpha: Float): Int {
        val a = (alpha.coerceIn(0f, 1f) * 255f).toInt().coerceIn(0, 255)
        return (a shl 24) or (argb.toInt() and 0x00FFFFFF)
    }

    private fun overlayFontFamily(font: String): String {
        return when (font) {
            "Georgia" -> "serif"
            "Courier New" -> "monospace"
            else -> "sans-serif"
        }
    }
}
