package com.eazpire.creator.ui.account.wardrobe

/**
 * Slot configuration – matches theme/assets/wardrobe.js SLOT_CONFIG
 */
data class WardrobeSlot(
    val key: String,
    val label: String,
    val iconRes: String // icon identifier for drawable or composable
)

val WARDROBE_SLOTS = listOf(
    WardrobeSlot("head", "Kopf", "head"),
    WardrobeSlot("upper_body", "Oberkörper", "upper"),
    WardrobeSlot("layer", "Layer / Jacke", "layer"),
    WardrobeSlot("pants", "Hose", "pants"),
    WardrobeSlot("feet", "Füße", "feet"),
    WardrobeSlot("socks", "Socken", "socks"),
    WardrobeSlot("accessory_1", "Accessoire 1", "acc1"),
    WardrobeSlot("accessory_2", "Accessoire 2", "acc2"),
    WardrobeSlot("one_piece", "One-Piece / Ganzkörper", "onepiece")
)

val ONE_PIECE_CONFLICTS = listOf("upper_body", "pants", "layer")
