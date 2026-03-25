package com.eazpire.creator.ui.creator

/**
 * Opens [CreatorGeneratorScreen] with data loaded from [designId] (native Remix / Generate New).
 * @param mode `remix` | `regenerate` — same intent as web remix_type
 */
data class GeneratorPrefillRequest(
    val designId: String,
    val mode: String
)
