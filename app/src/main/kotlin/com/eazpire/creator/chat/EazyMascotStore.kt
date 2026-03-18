package com.eazpire.creator.chat

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.eazyMascotDataStore: DataStore<Preferences> by preferencesDataStore(name = "eazy_mascot")

/**
 * Store for Eazy mascot: position (x, y), docked state.
 * Mirrors web: localStorage eazy_mascot_position, eazy_mascot_docked.
 */
class EazyMascotStore(private val context: Context) {
    private val _isDocked = MutableStateFlow(false)
    val isDocked: StateFlow<Boolean> = _isDocked.asStateFlow()

    private val _positionX = MutableStateFlow<Float?>(null)
    private val _positionY = MutableStateFlow<Float?>(null)
    val positionX: StateFlow<Float?> = _positionX.asStateFlow()
    val positionY: StateFlow<Float?> = _positionY.asStateFlow()

    init {
        CoroutineScope(Dispatchers.IO).launch {
            context.eazyMascotDataStore.data.map { prefs ->
                _isDocked.value = prefs[DOCKED_KEY] ?: false
                val x = prefs[POS_X_KEY]
                val y = prefs[POS_Y_KEY]
                _positionX.value = x
                _positionY.value = y
            }.first()
        }
    }

    suspend fun setDocked(docked: Boolean) {
        _isDocked.value = docked
        context.eazyMascotDataStore.edit { it[DOCKED_KEY] = docked }
    }

    suspend fun setPosition(x: Float, y: Float) {
        _positionX.value = x
        _positionY.value = y
        context.eazyMascotDataStore.edit {
            it[POS_X_KEY] = x
            it[POS_Y_KEY] = y
        }
    }

    suspend fun loadFromStore() {
        context.eazyMascotDataStore.data.map { prefs ->
            _isDocked.value = prefs[DOCKED_KEY] ?: false
            _positionX.value = prefs[POS_X_KEY]
            _positionY.value = prefs[POS_Y_KEY]
        }.first()
    }

    fun setDockedSync(docked: Boolean) {
        _isDocked.value = docked
        CoroutineScope(Dispatchers.IO).launch {
            context.eazyMascotDataStore.edit { it[DOCKED_KEY] = docked }
        }
    }

    fun setPositionSync(x: Float, y: Float) {
        _positionX.value = x
        _positionY.value = y
        CoroutineScope(Dispatchers.IO).launch {
            context.eazyMascotDataStore.edit {
                it[POS_X_KEY] = x
                it[POS_Y_KEY] = y
            }
        }
    }

    /** Reset position and docked state – use when mascot is lost or invalid */
    fun resetSync() {
        _isDocked.value = false
        _positionX.value = null
        _positionY.value = null
        CoroutineScope(Dispatchers.IO).launch {
            context.eazyMascotDataStore.edit { prefs ->
                prefs.remove(DOCKED_KEY)
                prefs.remove(POS_X_KEY)
                prefs.remove(POS_Y_KEY)
            }
        }
    }

    companion object {
        private val DOCKED_KEY = booleanPreferencesKey("eazy_mascot_docked")
        private val POS_X_KEY = floatPreferencesKey("eazy_mascot_pos_x")
        private val POS_Y_KEY = floatPreferencesKey("eazy_mascot_pos_y")
    }
}
