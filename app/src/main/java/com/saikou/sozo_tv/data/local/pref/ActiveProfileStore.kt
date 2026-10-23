package com.saikou.sozo_tv.data.local.pref

import android.content.Context
import com.google.gson.Gson
import com.saikou.sozo_tv.domain.model.HouseholdProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Device-local source of truth for the household member this TV is acting as.
 * It is deliberately separate from the account token: switching profiles must
 * never rotate or duplicate the device session.
 */
class ActiveProfileStore internal constructor(
    private val prefs: android.content.SharedPreferences,
    private val gson: Gson,
) {
    constructor(context: Context, gson: Gson) : this(
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE), gson,
    )
    private val _active = MutableStateFlow(read())

    val active: StateFlow<HouseholdProfile?> get() = _active
    fun current(): HouseholdProfile? = _active.value
    fun profileId(): String? = _active.value?.id
    fun namespace(): String? = _active.value?.localNamespace
    fun isKids(): Boolean = _active.value?.isKids == true

    @Volatile var revision: Long = 0
        private set

    data class Snapshot(val profile: HouseholdProfile?, val revision: Long)

    @Synchronized
    fun snapshot() = Snapshot(_active.value, revision)

    @Synchronized
    fun set(profile: HouseholdProfile?) {
        revision++
        if (profile == null) prefs.edit().remove(KEY_ACTIVE).commit()
        else prefs.edit().putString(KEY_ACTIVE, gson.toJson(profile)).commit()
        _active.value = profile
    }

    fun clear() = set(null)

    private fun read(): HouseholdProfile? = runCatching {
        prefs.getString(KEY_ACTIVE, null)?.let {
            gson.fromJson(it, HouseholdProfile::class.java)
        }
    }.getOrNull()

    private companion object {
        const val FILE = "sozo_household_profile"
        const val KEY_ACTIVE = "active"
    }
}
