package com.eazpire.creator.ui.creator

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import java.io.ByteArrayOutputStream

object DesignCanvasExport {
    fun typefaceFor(font: DesignCanvasFont): Typeface {
        val family = when (font) {
            DesignCanvasFont.COURIER -> "monospace"
            DesignCanvasFont.GEORGIA,
            DesignCanvasFont.TIMES,
            DesignCanvasFont.PALATINO -> "serif"
            DesignCanvasFont.IMPACT -> "sans-serif-black"
            else -> "sans-serif"
        }
        val style = if (font == DesignCanvasFont.IMPACT) Typeface.NORMAL else Typeface.BOLD
        return Typeface.create(family, style)
    }

    fun renderPng(
        text: String,
        font: DesignCanvasFont,
        sizeKey: Int,
        colorArgb: Int,
        align: DesignCanvasAlign,
    ): ByteArray {
        val trimmed = text.trim()
        require(trimmed.isNotEmpty()) { "empty" }
        return try {
            renderAt(DesignCanvasTextLayout.EXPORT_SIZE, trimmed, font, sizeKey, colorArgb, align)
        } catch (_: OutOfMemoryError) {
            renderAt(DesignCanvasTextLayout.FALLBACK_EXPORT_SIZE, trimmed, font, sizeKey, colorArgb, align)
        }
    }

    private fun renderAt(
        canvasSize: Int,
        text: String,
        font: DesignCanvasFont,
        sizeKey: Int,
        colorArgb: Int,
        align: DesignCanvasAlign,
    ): ByteArray {
        val bmp = Bitmap.createBitmap(canvasSize, canvasSize, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bmp)
            val fontPx = DesignCanvasTextLayout.exportFontPx(sizeKey, canvasSize)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colorArgb
                typeface = typefaceFor(font)
                textSize = fontPx
                textAlign = when (align) {
                    DesignCanvasAlign.LEFT -> Paint.Align.LEFT
                    DesignCanvasAlign.RIGHT -> Paint.Align.RIGHT
                    DesignCanvasAlign.CENTER -> Paint.Align.CENTER
                }
            }
            val maxWidth = canvasSize * (1f - 2f * DesignCanvasTextLayout.MARGIN_RATIO)
            val lines = DesignCanvasTextLayout.wrapLines(text, maxWidth) { paint.measureText(it) }
            val lineH = fontPx * DesignCanvasTextLayout.LINE_HEIGHT
            val totalH = lines.size * lineH
            val x = when (align) {
                DesignCanvasAlign.LEFT -> canvasSize * DesignCanvasTextLayout.MARGIN_RATIO
                DesignCanvasAlign.RIGHT -> canvasSize * (1f - DesignCanvasTextLayout.MARGIN_RATIO)
                DesignCanvasAlign.CENTER -> canvasSize / 2f
            }
            val startY = canvasSize / 2f - totalH / 2f + lineH / 2f
            lines.forEachIndexed { i, line ->
                canvas.drawText(line, x, startY + i * lineH, paint)
            }
            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            return out.toByteArray()
        } finally {
            if (!bmp.isRecycled) bmp.recycle()
        }
    }
}
