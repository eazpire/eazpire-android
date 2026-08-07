package com.eazpire.creator.admin.cursoragent

import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mirrors ViewModel gate: FAB only when admin-cursor-me returns ok && admin.
 */
class AdminCursorMeParseTest {

    @Test
    fun adminTrue_showsFab() {
        val me = JSONObject("""{"ok":true,"admin":true,"cursor_configured":true,"via":"shopify"}""")
        val isAdmin = me.optBoolean("ok") && me.optBoolean("admin")
        assertTrue(isAdmin)
    }

    @Test
    fun adminFalse_hidesFab() {
        val me = JSONObject("""{"ok":true,"admin":false}""")
        val isAdmin = me.optBoolean("ok") && me.optBoolean("admin")
        assertFalse(isAdmin)
    }

    @Test
    fun missingAdmin_hidesFab() {
        val me = JSONObject("""{"ok":true}""")
        val isAdmin = me.optBoolean("ok") && me.optBoolean("admin")
        assertFalse(isAdmin)
    }

    @Test
    fun okFalse_hidesFab() {
        val me = JSONObject("""{"ok":false,"admin":true}""")
        val isAdmin = me.optBoolean("ok") && me.optBoolean("admin")
        assertFalse(isAdmin)
    }
}
