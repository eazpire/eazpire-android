package com.eazpire.creator.ui.designrequest

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

data class DesignRequestOpen(
    val query: String = "",
    val imageUrl: String? = null,
    val parentId: String? = null,
    val source: String = "search",
)

object DesignRequestUiTrigger {
    private val _open = MutableStateFlow<DesignRequestOpen?>(null)
    val open: StateFlow<DesignRequestOpen?> = _open.asStateFlow()

    private val _generateTick = MutableStateFlow(0)
    val generateTick: StateFlow<Int> = _generateTick.asStateFlow()

    fun openSheet(spec: DesignRequestOpen) {
        _open.value = spec
    }

    fun close() {
        _open.value = null
    }

    fun openGenerator() {
        _generateTick.value = _generateTick.value + 1
    }
}

object DesignRequestPendingStore {
    private const val PREFS = "eaz_design_request"
    private const val KEY = "pending"

    fun save(context: Context, spec: DesignRequestOpen) {
        val json = JSONObject()
            .put("query", spec.query)
            .put("imageUrl", spec.imageUrl ?: "")
            .put("parentId", spec.parentId ?: "")
            .put("source", spec.source)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, json.toString())
            .apply()
    }

    fun take(context: Context): DesignRequestOpen? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY, null) ?: return null
        prefs.edit().remove(KEY).apply()
        return try {
            val o = JSONObject(raw)
            DesignRequestOpen(
                query = o.optString("query"),
                imageUrl = o.optString("imageUrl").takeIf { it.isNotBlank() },
                parentId = o.optString("parentId").takeIf { it.isNotBlank() },
                source = o.optString("source", "search"),
            )
        } catch (_: Exception) {
            null
        }
    }
}
