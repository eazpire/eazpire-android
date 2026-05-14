package com.eazpire.creator.shop.sidebar

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** Web parity: [theme/assets/eaz-redesign-sidebar.js] `eaz_sidebar_hidden`. */
data class SidebarHiddenState(
    val containers: Set<String>,
    val categories: Set<String>,
    val midcategories: Set<String>,
) {
    fun hiddenCategoryBadgeCount(): Int = categories.size + midcategories.size

    fun toJsonObject(): JSONObject =
        JSONObject()
            .put("containers", JSONArray(containers.toList()))
            .put("categories", JSONArray(categories.toList()))
            .put("midcategories", JSONArray(midcategories.toList()))

    fun toggledContainer(id: String): SidebarHiddenState {
        val m = containers.toMutableSet()
        if (!m.remove(id)) m.add(id)
        return copy(containers = m)
    }

    fun toggledCategory(id: String): SidebarHiddenState {
        val m = categories.toMutableSet()
        if (!m.remove(id)) m.add(id)
        return copy(categories = m)
    }

    fun toggledMid(id: String): SidebarHiddenState {
        val m = midcategories.toMutableSet()
        if (!m.remove(id)) m.add(id)
        return copy(midcategories = m)
    }

    fun removeMid(id: String) = copy(midcategories = midcategories - id)

    fun removeCategory(id: String) = copy(categories = categories - id)

    fun removeContainer(id: String) = copy(containers = containers - id)

    companion object {
        fun empty(): SidebarHiddenState = SidebarHiddenState(emptySet(), emptySet(), emptySet())

        fun fromJsonObject(o: JSONObject?): SidebarHiddenState {
            val root = o ?: return empty()
            return SidebarHiddenState(
                containers = readSet(root.optJSONArray("containers")),
                categories = readSet(root.optJSONArray("categories")),
                midcategories = readSet(root.optJSONArray("midcategories")),
            )
        }

        private fun readSet(arr: JSONArray?): Set<String> {
            if (arr == null) return emptySet()
            return buildSet {
                for (i in 0 until arr.length()) {
                    val v = arr.optString(i, "").trim()
                    if (v.isNotEmpty()) add(v)
                }
            }
        }

        /** Union: hidden if either marks it hidden */
        fun mergePreferRemote(local: SidebarHiddenState, remote: JSONObject?): SidebarHiddenState {
            if (remote == null || remote.length() == 0) return local
            val r = fromJsonObject(remote)
            return SidebarHiddenState(
                containers = local.containers + r.containers,
                categories = local.categories + r.categories,
                midcategories = local.midcategories + r.midcategories,
            )
        }
    }
}

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
            buildList(arr.length()) {
                for (i in 0 until arr.length()) {
                    val v = arr.optString(i, "").trim()
                    if (v.isNotEmpty()) add(v)
                }
            }
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
