package com.eazpire.creator.ar.poster

/**
 * Real-world poster size in meters (width × height) for AR placement.
 */
data class PosterPhysicalSize(
    val widthM: Float,
    val heightM: Float,
)

object PosterArDimensions {

    private val INCH_TO_M = 0.0254f
    private val CM_TO_M = 0.01f

    private val SIZE_INCH_PATTERN = Regex(
        """(\d+(?:[.,]\d+)?)\s*(?:[\"″]|in)?\s*[x×]\s*(\d+(?:[.,]\d+)?)""",
        RegexOption.IGNORE_CASE,
    )

    private val SIZE_CM_PATTERN = Regex(
        """(\d+(?:[.,]\d+)?)\s*cm\s*[x×]\s*(\d+(?:[.,]\d+)?)\s*cm""",
        RegexOption.IGNORE_CASE,
    )

    /** Known photopaper poster sizes (width × height in cm). */
    private val KNOWN_CM = mapOf(
        "a4-vertical" to (21.0f to 29.7f),
        "a3-vertical" to (29.7f to 42.0f),
        "a2-vertical" to (42.0f to 59.4f),
        "a1-vertical" to (59.4f to 84.1f),
        "a0-vertical" to (84.1f to 118.9f),
    )

    fun parseMeters(sizeLabel: String): PosterPhysicalSize {
        val normalized = sizeLabel.trim()
        if (normalized.isBlank()) return PosterPhysicalSize(0.3f, 0.4f)

        val slug = normalized.lowercase()
            .replace(Regex("[\"″]"), "")
            .replace(Regex("\\s+"), "-")
            .replace(Regex("[()]+"), "")
        KNOWN_CM[slug]?.let { (wCm, hCm) ->
            return PosterPhysicalSize(wCm * CM_TO_M, hCm * CM_TO_M)
        }

        SIZE_CM_PATTERN.find(normalized)?.let { match ->
            val w = match.groupValues[1].replace(',', '.').toFloatOrNull()
            val h = match.groupValues[2].replace(',', '.').toFloatOrNull()
            if (w != null && h != null && w > 0f && h > 0f) {
                return PosterPhysicalSize(w * CM_TO_M, h * CM_TO_M)
            }
        }

        SIZE_INCH_PATTERN.find(normalized)?.let { match ->
            val w = match.groupValues[1].replace(',', '.').toFloatOrNull()
            val h = match.groupValues[2].replace(',', '.').toFloatOrNull()
            if (w != null && h != null && w > 0f && h > 0f) {
                return PosterPhysicalSize(w * INCH_TO_M, h * INCH_TO_M)
            }
        }

        // Fallback: medium poster
        return PosterPhysicalSize(29.7f * CM_TO_M, 42.0f * CM_TO_M)
    }
}
