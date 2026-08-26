package com.eazpire.creator.ui.creator

/**
 * Shared layout math for Designs Canvas text export.
 * Keep in sync with src/features/creator/designCanvasTextLayout.js
 */
object DesignCanvasTextLayout {
    const val EXPORT_SIZE = 4500
    const val REF_SIZE = 1000
    const val MARGIN_RATIO = 0.08f
    const val LINE_HEIGHT = 1.05f
    const val FALLBACK_EXPORT_SIZE = 2500

    fun exportFontPx(sizeKey: Int, canvasSize: Int = EXPORT_SIZE): Float {
        val key = if (sizeKey <= 0) 88 else sizeKey
        return key * (canvasSize.toFloat() / REF_SIZE)
    }

    fun viewerFontPx(stageWidth: Float, sizeKey: Int): Float {
        val key = if (sizeKey <= 0) 88 else sizeKey
        if (stageWidth <= 0f) return 24f
        return stageWidth * (key.toFloat() / REF_SIZE)
    }

    fun wrapLines(text: String, maxWidth: Float, measure: (String) -> Float): List<String> {
        val source = text
        val paragraphs = source.split("\n")
        val lines = mutableListOf<String>()
        val safeWidth = if (maxWidth > 0f) maxWidth else 1f
        for (para in paragraphs) {
            if (para.isEmpty()) {
                lines.add("")
                continue
            }
            val words = para.split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (words.isEmpty()) {
                lines.add("")
                continue
            }
            var current = ""
            for (word in words) {
                val trial = if (current.isEmpty()) word else "$current $word"
                if (current.isEmpty() || measure(trial) <= safeWidth) {
                    current = trial
                } else {
                    lines.add(current)
                    current = word
                }
            }
            if (current.isNotEmpty()) lines.add(current)
        }
        return lines
    }
}

enum class DesignCanvasFont(val id: String, val label: String) {
    ARIAL("arial", "Arial"),
    IMPACT("impact", "Impact"),
    GEORGIA("georgia", "Georgia"),
    TIMES("times", "Times"),
    VERDANA("verdana", "Verdana"),
    TREBUCHET("trebuchet", "Trebuchet"),
    COURIER("courier", "Courier"),
    PALATINO("palatino", "Palatino");

    companion object {
        fun fromId(id: String): DesignCanvasFont =
            entries.firstOrNull { it.id == id } ?: IMPACT
    }
}

enum class DesignCanvasAlign { LEFT, CENTER, RIGHT }

enum class DesignCanvasColor(val id: String, val argb: Long, val labelKey: String, val fallback: String) {
    WHITE("white", 0xFFF8FAFC, "creator.my_creations.canvas_color_white", "White"),
    BLACK("black", 0xFF111827, "creator.my_creations.canvas_color_black", "Black"),
    RED("red", 0xFFDC2626, "creator.my_creations.canvas_color_red", "Red"),
    NAVY("navy", 0xFF1E3A8A, "creator.my_creations.canvas_color_navy", "Navy"),
    GOLD("gold", 0xFFF59E0B, "creator.my_creations.canvas_color_gold", "Gold"),
    TEAL("teal", 0xFF0D9488, "creator.my_creations.canvas_color_teal", "Teal"),
    PINK("pink", 0xFFDB2777, "creator.my_creations.canvas_color_pink", "Pink"),
    CREAM("cream", 0xFFF5E6C8, "creator.my_creations.canvas_color_cream", "Cream");
}

val DESIGN_CANVAS_SIZE_KEYS = listOf(32, 48, 64, 88, 120)
