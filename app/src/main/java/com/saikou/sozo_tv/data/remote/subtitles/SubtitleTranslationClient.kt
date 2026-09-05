package com.saikou.sozo_tv.data.remote.subtitles

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.saikou.sozo_tv.data.remote.device.ApiResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class SubtitleQuota(
    val enabled: Boolean = false,
    val limit: Int = 0,
    val used: Int = 0,
    val remaining: Int = 0,
)

data class TranslatedSubtitle(
    val url: String,
    val cueCount: Int = 0,
    val provider: String = "",
    val cached: Boolean = false,
)

private data class TranslateRequest(
    val url: String,
    val targetLang: String,
    val from: String? = null,
    val title: String? = null,
)

private data class ApiMessage(@SerializedName("message") val message: String?)

class SubtitleTranslationClient(
    okHttpClient: OkHttpClient,
    private val gson: Gson,
    private val baseUrl: String,
    private val tokenProvider: suspend () -> String?,
) {

    private val client: OkHttpClient = okHttpClient.newBuilder()
        .readTimeout(TRANSLATE_TIMEOUT_S, TimeUnit.SECONDS)
        .callTimeout(TRANSLATE_TIMEOUT_S + 10, TimeUnit.SECONDS)
        .build()

    suspend fun quota(): ApiResult<SubtitleQuota> =
        when (val raw = send(PATH_QUOTA, null)) {
            is ApiResult.Ok -> parse(raw.body, SubtitleQuota::class.java)
            is ApiResult.Http -> raw
            is ApiResult.Network -> raw
        }

    suspend fun translate(
        url: String,
        targetLang: String,
        from: String? = null,
        title: String? = null,
    ): ApiResult<TranslatedSubtitle> {
        val payload = gson.toJson(TranslateRequest(url, targetLang, from, title))
        return when (val raw = send(PATH_TRANSLATE, payload)) {
            is ApiResult.Ok -> {
                val parsed = parse(raw.body, TranslatedSubtitle::class.java)
                if (parsed is ApiResult.Ok && parsed.body.url.isBlank()) {
                    ApiResult.Http(200, null, null)
                } else {
                    parsed
                }
            }

            is ApiResult.Http -> raw
            is ApiResult.Network -> raw
        }
    }

    private suspend fun send(path: String, body: String?): ApiResult<String> =
        withContext(Dispatchers.IO) {
            val token = runCatching { tokenProvider() }.getOrNull()
                ?: return@withContext ApiResult.Http(401, null, null)
            try {
                val builder = Request.Builder()
                    .url("${baseUrl.trimEnd('/')}$path")
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("Accept", "application/json")
                if (body == null) {
                    builder.get()
                } else {
                    builder.addHeader("Content-Type", "application/json")
                        .post(body.toRequestBody(JSON))
                }
                client.newCall(builder.build()).execute().use { resp ->
                    val raw = resp.body.string()
                    if (resp.isSuccessful) {
                        ApiResult.Ok(raw)
                    } else {
                        ApiResult.Http(
                            resp.code,
                            messageOf(raw),
                            resp.header("Retry-After")?.trim()?.toIntOrNull(),
                        )
                    }
                }
            } catch (t: Throwable) {
                ApiResult.Network(t)
            }
        }

    private fun <T : Any> parse(raw: String, type: Class<T>): ApiResult<T> {
        val parsed = runCatching { gson.fromJson(raw, type) }.getOrNull()
            ?: return ApiResult.Http(200, null, null)
        return ApiResult.Ok(parsed)
    }

    private fun messageOf(raw: String): String? =
        runCatching { gson.fromJson(raw, ApiMessage::class.java)?.message }.getOrNull()

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
        const val PATH_QUOTA = "/contents/subtitles/quota"
        const val PATH_TRANSLATE = "/contents/subtitles/translate"
        const val TRANSLATE_TIMEOUT_S = 90L
    }
}
