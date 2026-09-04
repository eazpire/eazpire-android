package com.eazpire.shared.switcher

import com.eazpire.shared.EazpireApps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSwitchHelperTest {

    @Test
    fun handoffUri_withoutToken() {
        val uri = AppSwitchHelper.handoffUriString(EazpireApps.Target.SHOP)
        assertEquals("eazpire-shop://auth/handoff", uri)
    }

    @Test
    fun handoffUri_withToken() {
        val uri = AppSwitchHelper.handoffUriString(EazpireApps.Target.CREATOR, "tok-abc")
        assertTrue(uri.startsWith("eazpire-creator://auth/handoff?"))
        assertTrue(uri.contains("exchange_token=tok-abc"))
    }

    @Test
    fun playStoreUri_usesPackageId() {
        val uri = AppSwitchHelper.playStoreUriString(EazpireApps.Target.SHOP)
        assertTrue(uri.contains("com.eazpire.shop"))
        assertTrue(uri.startsWith("market://"))
    }

    @Test
    fun handoffUri_encodesToken() {
        val uri = AppSwitchHelper.handoffUriString(EazpireApps.Target.SHOP, "a b")
        assertTrue(uri.contains("exchange_token=a+b") || uri.contains("exchange_token=a%20b"))
        assertFalse(uri.contains("exchange_token=a b"))
    }

    @Test
    fun siblingPromo_hasDistinctTitles() {
        assertTrue(SiblingAppPromo.title(EazpireApps.Target.SHOP).contains("Shop"))
        assertTrue(SiblingAppPromo.title(EazpireApps.Target.CREATOR).contains("Creator"))
    }
}
