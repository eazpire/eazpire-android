package com.eazpire.creator.i18n

import androidx.compose.runtime.compositionLocalOf

/**
 * CompositionLocal for TranslationStore – allows any composable to access t().
 * Provide via CompositionLocalProvider(TranslationStore provides translationStore)
 */
val LocalTranslationStore = compositionLocalOf<TranslationStore?> { null }
