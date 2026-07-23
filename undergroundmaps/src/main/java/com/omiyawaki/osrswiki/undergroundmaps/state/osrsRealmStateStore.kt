package com.omiyawaki.osrswiki.undergroundmaps.state

import android.content.Context
import androidx.core.content.edit
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class osrsRealmStateStore(context: Context) {
    private val preferences = context.getSharedPreferences(OSRS_STATE_PREFERENCES, Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun load(): osrsPersistedRealmState {
        val encoded = preferences.getString(OSRS_STATE_KEY, null) ?: return osrsPersistedRealmState()
        return runCatching { json.decodeFromString<osrsPersistedRealmState>(encoded) }
            .getOrDefault(osrsPersistedRealmState())
    }

    fun save(state: osrsRealmUiState) {
        preferences.edit {
            putString(OSRS_STATE_KEY, json.encodeToString(state.persisted()))
        }
    }

    companion object {
        private const val OSRS_STATE_PREFERENCES = "osrs_underground_realm_state"
        private const val OSRS_STATE_KEY = "realm_state_v1"
    }
}
