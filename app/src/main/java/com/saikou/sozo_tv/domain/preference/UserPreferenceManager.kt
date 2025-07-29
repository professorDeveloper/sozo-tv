package com.saikou.sozo_tv.domain.preference

import android.content.Context
import androidx.preference.PreferenceManager
import java.util.UUID

class UserPreferenceManager(context: Context) {

    private val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)


    var userUID: String
        get() = sharedPref.getString("userUID", null) ?: generateAndSaveUID()
        set(value) {
            sharedPref.edit().putString("userUID", value).apply()
        }

    fun saveCurrPosition(position: Int, tokenKey: String) {
        sharedPref.edit().putInt(tokenKey, position).apply()
    }


    fun getCurrPosition(tokenKey: String): Int {
        return sharedPref.getInt(tokenKey, 1)
    }

    private fun generateAndSaveUID(): String {
        val newUID = UUID.randomUUID().toString()
        sharedPref.edit().putString("userUID", newUID).apply()
        return newUID
    }


}