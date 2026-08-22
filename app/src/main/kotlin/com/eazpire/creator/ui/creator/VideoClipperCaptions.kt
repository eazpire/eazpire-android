package com.eazpire.creator.ui.creator

data class ClipperWord(
    val text: String,
    val start: Double,
    val end: Double,
)

data class ClipperCaptionBlock(
    val start: Double,
    val end: Double,
    val lines: List<String>,
)

data class ClipperCaptionStyle(
    val enabled: Boolean = false,
    val wordsPer: Int = 6,
    val lines: Int = 2,
    val font: String = "Arial",
    val color: Long = 0xFFFFFFFF,
    val bgMode: String = "transparent",
    val bgColor: Long = 0xFF000000,
    val animation: String = "fade",
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val scale: Float = 1f,
    val rotation: Float = 0f,
)

internal object VideoClipperCaptions {
    fun buildBlocks(
        words: List<ClipperWord>,
        wordsPer: Int,
        lines: Int,
        start: Double,
        end: Double,
    ): List<ClipperCaptionBlock> {
        val per = wordsPer.coerceIn(1, 16)
        val lineCount = lines.coerceIn(1, 4)
        val inRange = words.filter { it.text.isNotBlank() && it.end > start && it.start < end }
        val out = mutableListOf<ClipperCaptionBlock>()
        var i = 0
        while (i < inRange.size) {
            val chunk = inRange.subList(i, kotlin.math.min(inRange.size, i + per))
            val perLine = kotlin.math.max(1, kotlin.math.ceil(chunk.size / lineCount.toDouble()).toInt())
            val lineTexts = mutableListOf<String>()
            for (line in 0 until lineCount) {
                val from = line * perLine
                if (from >= chunk.size) break
                val to = kotlin.math.min(chunk.size, from + perLine)
                lineTexts += chunk.subList(from, to).joinToString(" ") { it.text }
            }
            out += ClipperCaptionBlock(
                start = chunk.first().start,
                end = chunk.last().end,
                lines = lineTexts,
            )
            i += per
        }
        return out
    }

    fun atTime(blocks: List<ClipperCaptionBlock>, timeS: Double): ClipperCaptionBlock? {
        return blocks.firstOrNull { timeS >= it.start && timeS < it.end }
    }

    fun animProgress(block: ClipperCaptionBlock?, timeS: Double, animation: String): Float {
        if (block == null || animation == "none") return 1f
        val span = ((block.end - block.start) * 0.28).coerceIn(0.12, 0.32)
        return ((timeS - block.start) / span).toFloat().coerceIn(0f, 1f)
    }

    fun visibleText(block: ClipperCaptionBlock?, timeS: Double, animation: String): String {
        if (block == null) return ""
        val full = block.lines.joinToString("\n")
        if (animation != "typewriter") return full
        val p = animProgress(block, timeS, animation)
        val end = (full.length * p).toInt().coerceIn(0, full.length)
        return full.substring(0, end)
    }
}
