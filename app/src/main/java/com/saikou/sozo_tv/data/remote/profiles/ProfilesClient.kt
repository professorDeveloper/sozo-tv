package com.saikou.sozo_tv.data.remote.profiles

import com.google.gson.Gson
import com.saikou.sozo_tv.data.remote.device.ApiResult
import com.saikou.sozo_tv.domain.model.HouseholdProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

data class ProfilesListing(
    val profiles: List<HouseholdProfile>,
    val activeProfileId: String?,
    val max: Int,
)

private data class ProfileDto(
    val id: String?, val name: String?, val avatar: String?, val color: String?,
    val isKids: Boolean = false, val isDefault: Boolean = false,
) {
    fun domain() = HouseholdProfile(
        id = id.orEmpty(), name = name.orEmpty(), avatar = avatar, color = color,
        isKids = isKids, isDefault = isDefault,
    )
}

private data class ListingDto(
    val profiles: List<ProfileDto> = emptyList(),
    val activeProfileId: String? = null,
    val max: Int = 4,
)
private data class ProfileEnvelope(val profile: ProfileDto?)
private data class ErrorEnvelope(val message: String?, val code: String?)

class ProfilesClient(
    private val client: OkHttpClient,
    private val gson: Gson,
    private val baseUrl: String,
    private val tokenProvider: suspend () -> String?,
) {
    suspend fun list(): ApiResult<ProfilesListing> = call("/auth/profiles", "GET") { raw ->
        val body = gson.fromJson(raw, ListingDto::class.java)
        ProfilesListing(body.profiles.map(ProfileDto::domain), body.activeProfileId, body.max.coerceAtMost(4))
    }

    suspend fun create(name: String, avatar: String?, color: String?, isKids: Boolean) =
        mutate("/auth/profiles", "POST", mapOf(
            "name" to name, "avatar" to avatar, "color" to color, "isKids" to isKids,
        ))

    suspend fun update(id: String, changes: Map<String, Any?>) =
        mutate("/auth/profiles/$id", "PATCH", changes)

    suspend fun delete(id: String): ApiResult<Unit> =
        call("/auth/profiles/$id", "DELETE", emptyMap<String, Any?>()) { Unit }

    private suspend fun mutate(path: String, method: String, body: Any): ApiResult<HouseholdProfile> =
        call(path, method, body) { raw ->
            gson.fromJson(raw, ProfileEnvelope::class.java).profile?.domain()
                ?: throw IllegalStateException("Profile response was empty")
        }

    private suspend fun <T> call(
        path: String,
        method: String,
        body: Any? = null,
        decode: (String) -> T,
    ): ApiResult<T> = withContext(Dispatchers.IO) {
        val token = runCatching { tokenProvider() }.getOrNull()
            ?: return@withContext ApiResult.Http(401, "Not signed in", null)
        try {
            val requestBody = body?.let { gson.toJson(it).toRequestBody(JSON) }
            val request = Request.Builder()
                .url(baseUrl.trimEnd('/') + path)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/json")
                .apply {
                    when (method) {
                        "GET" -> get()
                        "POST" -> post(requireNotNull(requestBody))
                        "PATCH" -> patch(requireNotNull(requestBody))
                        "DELETE" -> delete(requestBody)
                    }
                }
                .build()
            client.newCall(request).execute().use { response ->
                val raw = response.body.string()
                if (!response.isSuccessful) {
                    val error = runCatching { gson.fromJson(raw, ErrorEnvelope::class.java) }.getOrNull()
                    return@use ApiResult.Http(response.code, error?.message ?: error?.code, null)
                }
                ApiResult.Ok(decode(raw))
            }
        } catch (t: Throwable) {
            ApiResult.Network(t)
        }
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
