package com.saikou.sozo_tv.domain.preference

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class EncryptedPreferencesManager(context: Context) {

    private val prefsFileName = "secure_prefs"
    private val tokenKey = "auth_token"


    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()


    private val encryptedPreferences = EncryptedSharedPreferences.create(
        context,
        prefsFileName,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )


    fun saveSubCode(subCode: String) {
        encryptedPreferences.edit().putString(tokenKey, subCode).apply()
    }


    fun getSubCode(): String? {
        return encryptedPreferences.getString(tokenKey, null)
    }

    fun saveUID(uid: String) {
        encryptedPreferences.edit().putString("userUID", uid).apply()
    }

    fun getUID(): String? {
        return encryptedPreferences.getString("userUID", null)
    }

    fun clearUID() {
        encryptedPreferences.edit().remove("userUID").apply()
    }

    fun clearToken() {
        encryptedPreferences.edit().remove(tokenKey).apply()
    }
}