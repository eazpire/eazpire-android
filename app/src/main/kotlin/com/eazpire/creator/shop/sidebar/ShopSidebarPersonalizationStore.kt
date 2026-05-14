package com.eazpire.creator.shop.sidebar

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** Web parity: [theme/assets/eaz-redesign-sidebar.js] `eaz_sidebar_hidden`. */
data class SidebarHiddenState(
    val containers: MutableSet<String>,
    val categories: MutableSet<String>,
    val midcategories: MutableSet<String>,
) {
    fun hiddenCategoryBadgeCount(): Int = categories.size + midcategories.size

    fun toJsonObject(): JSONObject =
        JSONObject()
            .put("containers", JSONArray(containers.toList()))
            .put("categories", JSONArray(categories.toList()))
            .put("midcategories", JSONArray(midcategories.toList()))

    companion object {
        fun empty() = SidebarHiddenState(mutableSetOf(), mutableSetOf(), mutableSetOf())

        fun fromJsonObject(o: JSONObject?): SidebarHiddenState {
            val s = empty()
            val root = o ?: return s
            readArray(root.optJSONArray("containers"), s.containers)
            readArray(root.optJSONArray("categories"), s.categories)
            readArray(root.optJSONArray("midcategories"), s.midcategories)
            return s
        }

        private fun readArray(arr: JSONArray?, into: MutableSet<String>) {
            if (arr == null) return
            for (i in 0 until arr.length()) {
                val v = arr.optString(i, "").trim()
                if (v.isNotEmpty()) into.add(v)
            }
        }

        fun mergePreferRemote(local: SidebarHiddenState, remote: JSONObject?): SidebarHiddenState {
            if (remote == null) return local
            val r = fromJsonObject(remote)
            return SidebarHiddenState(
                (local.containers + r.containers).toMutableSet(),
                (local.categories + r.categories).toMutableSet(),
                (local.midcategories + r.midcategories).toMutableSet(),
            )
        }
    }
}

/**
 * Persists grid sidebar personalization (web `localStorage` keys).
 * Uses [SharedPreferences] separate file from legacy [SidebarVisibilityStore].
 */
class ShopSidebarPersonalizationStore(context: Context) {
    private val p: SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun loadHidden(): SidebarHiddenState {
        val raw = p.getString(KEY_HIDDEN, null) ?: return SidebarHiddenState.empty()
        return try {
            SidebarHiddenState.fromJsonObject(JSONObject(raw))
        } catch (_: Exception) {
            SidebarHiddenState.empty()
        }
    }

    fun saveHidden(state: SidebarHiddenState) {
        p.edit().putString(KEY_HIDDEN, state.toJsonObject().toString()).apply()
    }

    fun getEyeRevealHiddenItems(): Boolean =
        p.getBoolean(KEY_EYE_REVEAL, true)

    fun setEyeRevealHiddenItems(reveal: Boolean) {
        p.edit().putBoolean(KEY_EYE_REVEAL, reveal).apply()
    }

    fun loadSectionOrder(): List<String> {
        val raw = p.getString(KEY_ORDER, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList(arr.length()) { i ->
                arr.optString(i, "").trim()
            }.filter { it.isNotEmpty() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveSectionOrder(order: List<String>) {
        val arr = JSONArray()
        order.forEach { arr.put(it) }
        p.edit().putString(KEY_ORDER, arr.toString()).apply()
    }

    companion object {
        private const val PREFS = "eazpire_shop_sidebar_v2"
        private const val KEY_HIDDEN = "eaz_sidebar_hidden"
        private const val KEY_ORDER = "eaz_sidebar_order"
        private const val KEY_EYE_REVEAL = "eaz_sidebar_eye_reveal_hidden"
    }
}
