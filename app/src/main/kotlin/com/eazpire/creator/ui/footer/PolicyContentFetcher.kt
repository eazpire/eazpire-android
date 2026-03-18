package com.eazpire.creator.ui.footer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.concurrent.TimeUnit

/**
 * Fetches policy/page HTML and extracts the main content body for native display.
 * Matches web eaz-terms-popup: only policy content, no full page chrome.
 */
object PolicyContentFetcher {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /**
     * Fetch URL and extract content body. Returns null on failure.
     * Selectors tried (in order): Shopify policy, Shopify page, generic main content.
     * @param darkMode when true, applies dark theme styling for Creator area
     */
    suspend fun fetchContent(url: String, darkMode: Boolean = false): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null
            val html = response.body?.string() ?: return@withContext null
            extractBody(html, url, darkMode)
        } catch (_: Exception) {
            null
        }
    }

    private fun extractBody(html: String, baseUrl: String, darkMode: Boolean = false): String? {
        val doc: Document = try {
            Jsoup.parse(html, baseUrl)
        } catch (_: Exception) {
            return null
        }

        // Shopify policy pages: .shopify-policy__body or .shopify-policy__container
        var body = doc.selectFirst(".shopify-policy__body")?.html()
            ?: doc.selectFirst(".shopify-policy__container")?.html()

        // Shopify page template: .page-content, .rte, main
        if (body.isNullOrBlank()) {
            body = doc.selectFirst(".page-content")?.html()
                ?: doc.selectFirst(".rte")?.html()
                ?: doc.selectFirst("main")?.html()
        }

        // Fallback: first content div
        if (body.isNullOrBlank()) {
            body = doc.selectFirst("#MainContent")?.html()
                ?: doc.selectFirst("[role=main]")?.html()
                ?: doc.selectFirst(".content")?.html()
        }

        if (body.isNullOrBlank()) return null

        // Wrap in styled container matching web eaz-terms-modal__policy-content
        return wrapWithStyles(body, darkMode)
    }

    private fun wrapWithStyles(innerHtml: String, darkMode: Boolean = false): String {
        val orange = "#F97316"
        val textPrimary = if (darkMode) "#e5e7eb" else "#1a1a1a"
        val textStrong = if (darkMode) "#f3f4f6" else "#0a0a0a"
        val borderColor = if (darkMode) "rgba(255,255,255,0.12)" else "rgba(0,0,0,0.06)"
        val tableBorder = if (darkMode) "rgba(255,255,255,0.12)" else "rgba(0,0,0,0.08)"
        val thBg = if (darkMode) "rgba(255,255,255,0.06)" else "rgba(0,0,0,0.03)"
        val bodyBg = if (darkMode) "#0A0514" else "#ffffff"
        return """
<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>
body { margin: 0; padding: 24px 16px; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; font-size: 15px; line-height: 1.7; color: $textPrimary; background: $bodyBg; }
h1, h2 { font-size: 1.15rem; font-weight: 700; color: $textStrong; margin: 28px 0 12px; padding-bottom: 8px; border-bottom: 1px solid $borderColor; }
h1:first-child, h2:first-child { margin-top: 0; }
h3 { font-size: 1rem; font-weight: 600; color: $textStrong; margin: 20px 0 8px; }
p { margin: 0 0 12px; }
a { color: $orange; text-decoration: underline; text-underline-offset: 2px; }
ul, ol { margin: 0 0 12px; padding-left: 24px; }
li { margin-bottom: 4px; }
strong { font-weight: 600; color: $textStrong; }
table { width: 100%; border-collapse: collapse; margin: 16px 0; font-size: 14px; }
th, td { padding: 10px 14px; border: 1px solid $tableBorder; text-align: left; }
th { background: $thBg; font-weight: 600; }
</style>
</head>
<body>
<div class="policy-content">$innerHtml</div>
</body>
</html>
        """.trimIndent()
    }
}
