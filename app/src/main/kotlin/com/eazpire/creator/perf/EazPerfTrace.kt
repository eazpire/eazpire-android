package com.eazpire.creator.perf

import android.util.Log
import androidx.tracing.Trace
import com.eazpire.creator.BuildConfig
import com.eazpire.creator.EazpireAppTiming
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Lightweight cold-start and home-carousel performance tracing.
 *
 * Logcat filter: `adb logcat -s EazPerf`
 * Systrace: Android Studio Profiler → CPU → Record → search "EazPerf" / section names.
 */
object EazPerfTrace {

    const val TAG = "EazPerf"

    private val enabled = BuildConfig.EAZ_PERF_TRACE

    private val counters = ConcurrentHashMap<String, AtomicInteger>()
    private val sectionTotalsMs = ConcurrentHashMap<String, AtomicInteger>()
    private val milestones = ConcurrentHashMap<String, Long>()
    private val logFileRef = AtomicReference<File?>(null)

    fun init(context: android.content.Context) {
        if (!enabled) return
        logFileRef.set(File(context.applicationContext.getExternalFilesDir(null), "eaz-perf-baseline.log"))
        mark("perf_trace_init")
    }

    fun isEnabled(): Boolean = enabled

    /** Milestone since process start (ms). Visible in Logcat + optional file log. */
    fun mark(name: String, extras: Map<String, Any?> = emptyMap()) {
        if (!enabled) return
        val sinceProcessMs = msSinceProcessStart()
        milestones[name] = sinceProcessMs
        val extraText = formatExtras(extras)
        Log.i(TAG, "mark $name @ ${sinceProcessMs}ms$extraText")
        appendFile(buildLine("mark", name, sinceProcessMs, extras))
    }

    fun <T> measureSection(section: String, block: () -> T): T {
        if (!enabled) return block()
        Trace.beginSection(section)
        val startNs = System.nanoTime()
        return try {
            block()
        } finally {
            val durationMs = ((System.nanoTime() - startNs) / 1_000_000L).toInt()
            Trace.endSection()
            recordSection(section, durationMs)
            val sinceProcessMs = msSinceProcessStart()
            Log.i(TAG, "section $section ${durationMs}ms @ ${sinceProcessMs}ms")
            appendFile(buildLine("section", section, sinceProcessMs, mapOf("duration_ms" to durationMs)))
        }
    }

    suspend fun <T> measureSectionSuspend(section: String, block: suspend () -> T): T {
        if (!enabled) return block()
        Trace.beginSection(section)
        val startNs = System.nanoTime()
        return try {
            block()
        } finally {
            val durationMs = ((System.nanoTime() - startNs) / 1_000_000L).toInt()
            Trace.endSection()
            recordSection(section, durationMs)
            val sinceProcessMs = msSinceProcessStart()
            Log.i(TAG, "section $section ${durationMs}ms @ ${sinceProcessMs}ms")
            appendFile(buildLine("section", section, sinceProcessMs, mapOf("duration_ms" to durationMs)))
        }
    }

    fun incrementCounter(name: String, delta: Int = 1) {
        if (!enabled) return
        counters.computeIfAbsent(name) { AtomicInteger(0) }.addAndGet(delta)
    }

    fun resetHomeBootstrap() {
        if (!enabled) return
        counters.keys.filter { it.startsWith("home_") }.forEach { counters.remove(it) }
        sectionTotalsMs.keys.filter { it.startsWith("home.") }.forEach { sectionTotalsMs.remove(it) }
        mark("home_bootstrap_reset")
    }

    /** One-line summary after home feed bootstrap — compare runs before/after optimizations. */
    fun logHomeBootstrapSummary(trigger: String) {
        if (!enabled) return
        val sinceProcessMs = msSinceProcessStart()
        val apiCalls = counterValue("home_api_calls")
        val mockResolves = counterValue("mock_card_resolve")
        val mockMs = sectionTotalMs("mock.resolveCardImages")
        val tokenMs = sectionTotalMs("SecureTokenStore.init")
        val bootstrapMs = sectionTotalMs("home.bootstrap.initial")

        val summary = buildString {
            append("HOME_SUMMARY trigger=$trigger @ ${sinceProcessMs}ms")
            append(" | bootstrap=${bootstrapMs}ms")
            append(" | api_calls=$apiCalls")
            append(" | token_init=${tokenMs}ms")
            append(" | mock_resolves=$mockResolves")
            append(" | mock_resolve_ms=${mockMs}ms")
            milestones["home_first_content"]?.let { append(" | first_content=${it}ms") }
            milestones["home_interactive"]?.let { append(" | interactive=${it}ms") }
            milestones["main_first_compose"]?.let { append(" | first_compose=${it}ms") }
        }
        Log.i(TAG, summary)
        appendFile(
            JSONObject()
                .put("type", "home_summary")
                .put("trigger", trigger)
                .put("since_process_ms", sinceProcessMs)
                .put("bootstrap_ms", bootstrapMs)
                .put("api_calls", apiCalls)
                .put("token_init_ms", tokenMs)
                .put("mock_resolves", mockResolves)
                .put("mock_resolve_ms", mockMs)
                .toString()
        )
    }

    fun logColdStartSummary() {
        if (!enabled) return
        val ordered = listOf(
            "application_onCreate_start",
            "application_onCreate_end",
            "mainActivity_onCreate_start",
            "mainActivity_tokenStore_ready",
            "mainActivity_setContent",
            "main_first_compose",
            "home_bootstrap_start",
            "home_first_content",
            "home_bootstrap_end",
            "home_interactive",
        )
        val lines = ordered.mapNotNull { key ->
            milestones[key]?.let { "$key=${it}ms" }
        }
        val summary = "COLD_START_SUMMARY " + lines.joinToString(" | ")
        Log.i(TAG, summary)
        appendFile(
            JSONObject()
                .put("type", "cold_start_summary")
                .put("milestones", JSONObject(milestones.mapValues { it.value }))
                .toString()
        )
    }

    private fun recordSection(section: String, durationMs: Int) {
        sectionTotalsMs.computeIfAbsent(section) { AtomicInteger(0) }.addAndGet(durationMs)
    }

    private fun counterValue(name: String): Int = counters[name]?.get() ?: 0

    private fun sectionTotalMs(section: String): Int = sectionTotalsMs[section]?.get() ?: 0

    private fun msSinceProcessStart(): Long {
        val start = EazpireAppTiming.processStartMs
        return if (start > 0L) System.currentTimeMillis() - start else 0L
    }

    private fun formatExtras(extras: Map<String, Any?>): String {
        if (extras.isEmpty()) return ""
        return extras.entries.joinToString(prefix = " {", postfix = "}") { (k, v) -> "$k=$v" }
    }

    private fun buildLine(type: String, name: String, sinceProcessMs: Long, extras: Map<String, Any?>): String {
        return JSONObject()
            .put("type", type)
            .put("name", name)
            .put("since_process_ms", sinceProcessMs)
            .put("timestamp", System.currentTimeMillis())
            .apply { extras.forEach { (k, v) -> put(k, v?.toString() ?: "null") } }
            .toString()
    }

    private fun appendFile(line: String) {
        try {
            logFileRef.get()?.appendText(line + "\n")
        } catch (_: Exception) {
            /* optional file log */
        }
    }
}
