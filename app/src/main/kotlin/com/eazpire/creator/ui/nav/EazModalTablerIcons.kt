package com.eazpire.creator.ui.nav

/**
 * Tabler outline icons for modal navigation — paths from theme/snippets/eaz-modal-tabler-icon.liquid
 */
object EazModalTablerIcons {

    val paths: Map<String, List<String>> = mapOf(
        "user" to listOf(
            "M8 7a4 4 0 1 0 8 0a4 4 0 0 0 -8 0",
            "M6 21v-2a4 4 0 0 1 4 -4h4a4 4 0 0 1 4 4v2",
        ),
        "ruler-measure" to listOf(
            "M19.875 12c.621 0 1.125 .512 1.125 1.143v5.714c0 .631 -.504 1.143 -1.125 1.143h-15.875c-.621 0 -1.125 -.512 -1.125 -1.143v-5.714c0 -.631 .504 -1.143 1.125 -1.143h15.75",
            "M9 12v2", "M6 12v3", "M12 12v3", "M15 12v2", "M18 12v1",
        ),
        "catalog" to listOf(
            "M4 4m0 2a2 2 0 0 1 2 -2h12a2 2 0 0 1 2 2v12a2 2 0 0 1 -2 2h-12a2 2 0 0 1 -2 -2",
            "M9 4l0 16", "M15 4l0 16", "M4 12l16 0",
        ),
        "hanger" to listOf(
            "M12 6.5l-4 -1.5", "M12 6.5l4 -1.5", "M12 6.5v4",
            "M6 7.5l-2 5.5a1 1 0 0 0 1 1h14a1 1 0 0 0 1 -1l-2 -5.5", "M6 7.5h12",
        ),
        "photo" to listOf(
            "M15 8h.01",
            "M3 6a3 3 0 0 1 3 -3h12a3 3 0 0 1 3 3v12a3 3 0 0 1 -3 3h-12a3 3 0 0 1 -3 -3v-12",
            "M3 16l5 -5c.928 -.893 2.072 -.893 3 0l5 5",
            "M14 14l1 -1c.928 -.893 2.072 -.893 3 0l3 3",
        ),
        "brush" to listOf(
            "M3 21l1.65 -3.8a9 9 0 1 1 3.4 -2.9l-5.05 .9",
            "M11 12l2 2", "M11 12l2 -2",
        ),
        "users" to listOf(
            "M9 7m-4 0a4 4 0 1 0 8 0a4 4 0 0 0 -8 0",
            "M3 21v-2a4 4 0 0 1 4 -4h4a4 4 0 0 1 4 4v2",
            "M16 3.13a4 4 0 0 1 0 7.75",
            "M21 21v-2a4 4 0 0 0 -3 -3.85",
        ),
        "heart" to listOf(
            "M19.5 12.572l-7.5 7.428l-7.5 -7.428a5 5 0 1 1 7.5 -6.566a5 5 0 1 1 7.5 6.572",
        ),
        "wallet" to listOf(
            "M17 8v-3a1 1 0 0 0 -1 -1h-10a2 2 0 0 0 0 4h12a1 1 0 0 1 1 1v3",
            "M17 8h2a2 2 0 0 1 2 2v6a2 2 0 0 1 -2 2h-2",
            "M17 12h.01",
        ),
        "bell" to listOf(
            "M10 5a2 2 0 1 1 4 0a7 7 0 0 1 4 12v3a1 1 0 0 1 -1 1h-4a1 1 0 0 1 -1 -1v-3a7 7 0 0 1 4 -12",
            "M9 17h6",
        ),
        "layout-dashboard" to listOf(
            "M4 4h6v8h-6z", "M4 16h6v4h-6z",
            "M14 12h6v8h-6z", "M14 4h6v4h-6z",
        ),
        "shopping-cart" to listOf(
            "M6 19m-2 0a2 2 0 1 0 4 0a2 2 0 1 0 -4 0",
            "M17 19m-2 0a2 2 0 1 0 4 0a2 2 0 1 0 -4 0",
            "M17 17h-11v-13h-2", "M6 5l14 1l-1 7h-13",
        ),
        "cash-banknote" to listOf(
            "M12 12m-3 0a3 3 0 1 0 6 0a3 3 0 1 0 -6 0",
            "M18 6v13a1 1 0 0 1 -1 1h-12a1 1 0 0 1 -1 -1v-13a1 1 0 0 1 1 -1h12a1 1 0 0 1 1 1",
            "M12 8v4", "M6 10h12",
        ),
        "send" to listOf(
            "M10 14l11 -3l-1.25 -5.75",
            "M10 14l-1 -5", "M10 14l-3 3",
            "M10 14v5l-2 -2", "M14 9l-5 5", "M5 10l5 -5",
        ),
    )

    /** LoyaliTee brand icon — theme/snippets/eaz-icons.liquid #eaz-icon-loyalitee */
    val loyaliteePaths: List<String> = listOf(
        "M6 3.5h8l1.5 3H4.5L6 3.5z",
        "M5 6.5h10l-1.2 9.5H6.2L5 6.5z",
        "M14.5 14.5m-3.25 0a3.25 3.25 0 1 0 6.5 0a3.25 3.25 0 1 0 -6.5 0",
        "M13.25 14.5h2.5", "M14.5 13.25v2.5",
    )

    fun iconNameForTab(tabId: String): String = when (tabId.lowercase()) {
        "profile-settings" -> "user"
        "size-ai" -> "ruler-measure"
        "product-catalogue" -> "catalog"
        "wardrobe" -> "hanger"
        "ask-team" -> "users"
        "mockups" -> "photo"
        "my-creations" -> "brush"
        "community", "network" -> "users"
        "interests" -> "heart"
        "balance-payouts", "store-credit" -> "wallet"
        "notifications" -> "bell"
        "gift-cards" -> "gift"
        "promo-codes" -> "tag"
        "overview" -> "layout-dashboard"
        "earnings" -> "shopping-cart"
        "payouts" -> "cash-banknote"
        "request" -> "send"
        else -> "user"
    }

    fun pathsForIcon(iconName: String): List<String> =
        paths[iconName] ?: EazNavTablerIcons.paths[iconName].orEmpty()
}
