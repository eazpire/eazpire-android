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

    private val _bubblePages = MutableStateFlow<List<EazyGuidePage>>(emptyList())
    val bubblePages: StateFlow<List<EazyGuidePage>> = _bubblePages.asStateFlow()

    private val _bubblePageIndex = MutableStateFlow(0)
    val bubblePageIndex: StateFlow<Int> = _bubblePageIndex.asStateFlow()

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
        setBubblePages(
            listOf(
                EazyGuidePage(
                    category = "Guide Mode",
                    body = "Guide Mode on! Tap a control for a tip, or ask me about your selection."
                )
            )
        )
    }

    fun exit() {
        _active.value = false
        _loading.value = false
        clearBubble()
        elementContext = null
        screenshotContext = null
        _promptText.value = ""
        chatUiOnly = false
    }

    fun toggleTool(tool: String) {
        when (tool) {
            "click" -> {
                _toolClick.value = true
                _toolScreenshot.value = false
            }
            "screenshot" -> {
                _toolScreenshot.value = true
                _toolClick.value = false
            }
            "prompt" -> _toolPrompt.value = !_toolPrompt.value
        }
        if (!_toolClick.value && !_toolScreenshot.value) {
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

    fun setBubblePages(pages: List<EazyGuidePage>, loading: Boolean = false) {
        _loading.value = loading
        _bubblePages.value = pages
        _bubblePageIndex.value = 0
    }

    fun setBubble(text: String?, loading: Boolean = false) {
        val pages = if (text.isNullOrBlank()) emptyList() else EazyGuideRegistry.pagesFromPlainText(text)
        setBubblePages(pages, loading)
    }

    fun clearBubble() {
        _bubblePages.value = emptyList()
        _bubblePageIndex.value = 0
        _loading.value = false
    }

    fun setBubblePageIndex(index: Int) {
        val max = (_bubblePages.value.size - 1).coerceAtLeast(0)
        _bubblePageIndex.value = index.coerceIn(0, max)
    }
}
