package com.saikou.sozo_tv.data.repository

import android.util.Log
import com.animestudios.animeapp.GetMyInfoQuery
import com.apollographql.apollo3.ApolloClient
import com.google.firebase.database.FirebaseDatabase
import com.saikou.sozo_tv.data.local.pref.AuthPrefKeys
import com.saikou.sozo_tv.data.local.pref.PreferenceManager
import kotlinx.coroutines.tasks.await

/**
 * TV AuthRepository (QR pairing):
 * - receives AniList token from mobile via Firebase pairing
 * - saves token locally
 * - fetches Viewer via Apollo
 * - ensures TV user exists in Firebase
 */
class AuthRepository(
    private val prefs: PreferenceManager,
    private val apolloClient: ApolloClient,
    private val db: FirebaseDatabase
) {
    suspend fun handleTokenFromPairing(token: String): Result {
        return try {
            prefs.putString(AuthPrefKeys.ANILIST_TOKEN, token)

            val viewer = fetchViewer()
                ?: return Result.Error("Viewer is null (invalid token or server issue)")

            val aniId = viewer.id
            val name = viewer.name
            val avatarUrl = viewer.avatar?.large

            prefs.putString(AuthPrefKeys.ANILIST_ANI_ID, aniId.toString())

            ensureTvUserExists(
                aniId = aniId,
                name = name,
                avatarUrl = avatarUrl
            )

            Result.Success(aniId)
        } catch (e: Exception) {
            Log.e("AuthRepository", "handleTokenFromPairing error", e)
            Result.Error(e.message ?: "Pairing auth error")
        }
    }

    private suspend fun fetchViewer(): GetMyInfoQuery.Viewer? {
        val res = apolloClient.query(GetMyInfoQuery()).execute()
        return res.data?.Viewer
    }

    private suspend fun ensureTvUserExists(aniId: Int, name: String, avatarUrl: String?) {
        val now = System.currentTimeMillis()
        val ref = db.reference.child("TV_USERS").child(aniId.toString())
        val snap = ref.get().await()

        if (!snap.exists()) {
            val data: Map<String, Any> = mapOf(
                "ani_id" to aniId,
                "name" to name,
                "avatar" to (avatarUrl ?: ""),
                "platform" to "tv",
                "created_at" to now,
                "last_login" to now
            )
            ref.setValue(data).await()
        } else {
            val update: Map<String, Any> = mapOf(
                "name" to name,
                "avatar" to (avatarUrl ?: ""),
                "last_login" to now
            )
            ref.updateChildren(update).await()
        }
    }

    sealed class Result {
        data class Success(val aniId: Int) : Result()
        data class Error(val message: String) : Result()
    }
}

