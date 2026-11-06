package com.saikou.sozo_tv.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.saikou.sozo_tv.data.local.pref.ActiveProfileStore
import com.saikou.sozo_tv.data.local.database.ProfileDatabaseProvider
import com.saikou.sozo_tv.data.remote.device.ApiResult
import com.saikou.sozo_tv.data.remote.profiles.ProfilesClient
import com.saikou.sozo_tv.domain.model.HouseholdProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class HouseholdProfilesState(
    val profiles: List<HouseholdProfile> = emptyList(),
    val active: HouseholdProfile? = null,
    val max: Int = 4,
    val loading: Boolean = false,
    val error: String? = null,
)

/** Owns list/selection/cache semantics; screens only render [state] and call commands. */
class HouseholdProfileRepository(
    context: Context,
    private val client: ProfilesClient,
    private val activeStore: ActiveProfileStore,
    private val gson: Gson,
    private val databases: ProfileDatabaseProvider,
) {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(readCache())
    val state: StateFlow<HouseholdProfilesState> get() = _state

    suspend fun refresh(): Boolean {
        _state.value = _state.value.copy(loading = true, error = null)
        return when (val result = client.list()) {
            is ApiResult.Ok -> {
                val profiles = result.body.profiles.filter { it.id.isNotBlank() }
                val stored = activeStore.current()
                val active = profiles.firstOrNull { it.id == stored?.id }
                    ?: profiles.firstOrNull { it.id == result.body.activeProfileId }
                    ?: profiles.firstOrNull { it.isDefault }
                    ?: profiles.firstOrNull()
                activeStore.set(active)
                _state.value = HouseholdProfilesState(profiles, active, result.body.max)
                persist(profiles, result.body.max)
                true
            }
            is ApiResult.Http -> fail(result.message ?: "Profiles could not be loaded")
            is ApiResult.Network -> fail(result.cause.message ?: "Network error")
        }
    }

    fun activate(profile: HouseholdProfile) {
        activeStore.set(profile)
        _state.value = _state.value.copy(active = profile, error = null)
    }

    suspend fun create(name: String, avatar: String?, color: String?, kids: Boolean): Boolean =
        when (val result = client.create(name.trim(), avatar, color, kids)) {
            is ApiResult.Ok -> {
                val list = _state.value.profiles + result.body
                _state.value = _state.value.copy(profiles = list, error = null)
                persist(list, _state.value.max)
                true
            }
            is ApiResult.Http -> fail(result.message ?: "Profile could not be created")
            is ApiResult.Network -> fail(result.cause.message ?: "Network error")
        }

    suspend fun update(id: String, changes: Map<String, Any?>): Boolean =
        when (val result = client.update(id, changes)) {
            is ApiResult.Ok -> {
                val list = _state.value.profiles.map { if (it.id == id) result.body else it }
                if (_state.value.active?.id == id) activeStore.set(result.body)
                _state.value = _state.value.copy(
                    profiles = list,
                    active = if (_state.value.active?.id == id) result.body else _state.value.active,
                    error = null,
                )
                persist(list, _state.value.max)
                true
            }
            is ApiResult.Http -> fail(result.message ?: "Profile could not be updated")
            is ApiResult.Network -> fail(result.cause.message ?: "Network error")
        }

    suspend fun delete(id: String): Boolean = when (val result = client.delete(id)) {
        is ApiResult.Ok -> {
            _state.value.profiles.firstOrNull { it.id == id }?.let(databases::delete)
            val list = _state.value.profiles.filterNot { it.id == id }
            val active = if (_state.value.active?.id == id) list.firstOrNull { it.isDefault } else _state.value.active
            if (_state.value.active?.id == id) activeStore.set(active)
            _state.value = _state.value.copy(profiles = list, active = active, error = null)
            persist(list, _state.value.max)
            true
        }
        is ApiResult.Http -> fail(result.message ?: "Profile could not be deleted")
        is ApiResult.Network -> fail(result.cause.message ?: "Network error")
    }

    fun clear() {
        databases.deleteAll(_state.value.profiles)
        prefs.edit().clear().apply()
        activeStore.clear()
        _state.value = HouseholdProfilesState()
    }

    private fun fail(message: String): Boolean {
        _state.value = _state.value.copy(loading = false, error = message)
        return false
    }

    private fun persist(profiles: List<HouseholdProfile>, max: Int) {
        prefs.edit().putString(KEY_LIST, gson.toJson(profiles)).putInt(KEY_MAX, max).apply()
    }

    private fun readCache(): HouseholdProfilesState = runCatching {
        val type = object : TypeToken<List<HouseholdProfile>>() {}.type
        val list: List<HouseholdProfile> = gson.fromJson(prefs.getString(KEY_LIST, "[]"), type)
        val active = activeStore.current()?.let { a -> list.firstOrNull { it.id == a.id } ?: a }
        HouseholdProfilesState(list, active, prefs.getInt(KEY_MAX, 4))
    }.getOrDefault(HouseholdProfilesState(active = activeStore.current()))

    private companion object {
        const val FILE = "sozo_household_profiles"
        const val KEY_LIST = "profiles"
        const val KEY_MAX = "max"
    }
}
