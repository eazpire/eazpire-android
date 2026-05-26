package com.eazpire.creator.creatorcodes

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/** True when logged-in user is not a creator but has a pending sale/purchase code entitlement. */
object CreatorCodeAvailableHintStore {
    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    fun refreshFromResponse(data: JSONObject) {
        _active.value = !data.optBoolean("is_creator") && data.optBoolean("has_pending_entitlement")
    }

    fun clear() {
        _active.value = false
    }
}
