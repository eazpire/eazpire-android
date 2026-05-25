package com.eazpire.creator.chat

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class EazyGuideElementContext(
    val guideKey: String? = null,
    val label: String? = null
)

data class EazyGuideScreenshotContext(
    val base64: String,
    val mime: String = "image/jpeg"
)

object EazyGuideModeStore {
    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    private val _toolClick = MutableStateFlow(true)
    val toolClick: StateFlow<Boolean> = _toolClick.asStateFlow()

    private val _toolScreenshot = MutableStateFlow(false)
    val toolScreenshot: StateFlow<Boolean> = _toolScreenshot.asStateFlow()

    private val _toolPrompt = MutableStateFlow(false)
    val toolPrompt: StateFlow<Boolean> = _toolPrompt.asStateFlow()

    private val _bubbleText = MutableStateFlow<String?>(null)
    val bubbleText: StateFlow<String?> = _bubbleText.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _promptText = MutableStateFlow("")
    val promptText: StateFlow<String> = _promptText.asStateFlow()

    var elementContext: EazyGuideElementContext? = null
        private set
    var screenshotContext: EazyGuideScreenshotContext? = null
        private set
    var chatUiOnly: Boolean = false
        private set

    fun enter(chatUiOnlyScope: Boolean = false) {
        chatUiOnly = chatUiOnlyScope
        _active.value = true
        _toolClick.value = true
        _toolScreenshot.value = false
        _toolPrompt.value = false
        elementContext = null
        screenshotContext = null
        _promptText.value = ""
        _bubbleText.value = "Guide Mode on! Long-press any element, drag a screenshot, or ask me."
    }

    fun exit() {
        _active.value = false
        _loading.value = false
        _bubbleText.value = null
        elementContext = null
        screenshotContext = null
        _promptText.value = ""
        chatUiOnly = false
    }

    fun toggleTool(tool: String) {
        when (tool) {
            "click" -> _toolClick.value = !_toolClick.value
            "screenshot" -> _toolScreenshot.value = !_toolScreenshot.value
            "prompt" -> _toolPrompt.value = !_toolPrompt.value
        }
        if (!_toolClick.value && !_toolScreenshot.value && !_toolPrompt.value) {
            _toolClick.value = true
        }
    }

    fun setPrompt(text: String) {
        _promptText.value = text
    }

    fun setElementContext(ctx: EazyGuideElementContext?) {
        elementContext = ctx
    }

    fun setScreenshotContext(ctx: EazyGuideScreenshotContext?) {
        screenshotContext = ctx
    }

    fun setBubble(text: String?, loading: Boolean = false) {
        _loading.value = loading
        _bubbleText.value = text
    }
}
