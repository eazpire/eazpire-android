package com.eazpire.creator.i18n

/**
 * Interpolates count placeholders in UI strings from D1 / theme locales.
 * Handles {{ count }}, %count%, {count}, __COUNT__, and mistranslated bare "count".
 */
fun formatCountLabel(template: String, count: Int): String {
    val value = count.toString()
    var out = template
        .replace(Regex("""\{\{\s*count\s*\}\}""", RegexOption.IGNORE_CASE), value)
        .replace("%count%", value, ignoreCase = true)
        .replace("{count}", value, ignoreCase = true)
        .replace("__COUNT__", value)
    if (out.contains("count", ignoreCase = true)) {
        out = out.replace(Regex("""(?<![\w])count(?![\w])""", RegexOption.IGNORE_CASE), value)
    }
    return out
}

fun TranslationStore.formatCount(key: String, count: Int, default: String): String =
    formatCountLabel(t(key, default), count)
