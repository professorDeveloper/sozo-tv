package com.saikou.sozo_tv.data.repository

import com.saikou.sozo_tv.data.local.pref.PreferenceManager

/**
 * AniList QR pairing/sign-in was removed with the GraphQL backend. Kept as a thin
 * compile-compatible shim for the (now unused) QR login screen — it no longer talks
 * to AniList.
 */
class AuthRepository(
    private val prefs: PreferenceManager,
) {
    suspend fun handleTokenFromPairing(token: String): Result {
        return Result.Error("Sign-in is disabled in this build.")
    }

    sealed class Result {
        data class Success(val aniId: Int) : Result()
        data class Error(val message: String) : Result()
    }
}
