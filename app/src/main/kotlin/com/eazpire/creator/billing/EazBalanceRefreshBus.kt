package com.eazpire.creator.billing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Signals footer / settings panels to reload EAZ balance after debits or credits. */
object EazBalanceRefreshBus {
    private val _tick = MutableStateFlow(0)
    val tick: StateFlow<Int> = _tick.asStateFlow()

    fun requestRefresh() {
        _tick.value = _tick.value + 1
    }
}
