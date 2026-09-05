package com.saikou.sozo_tv.data.remote.subtitles

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.saikou.sozo_tv.data.remote.device.ApiResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

class SubtitleSearchClient(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson,
    private val baseUrl: String,
) {

    suspend fun search(
        title: String,
        isSerial: Boolean,
        season: Int? = null,
        episode: Int? = null,
    ): ApiResult<List<OnlineSubtitle>> = withContext(Dispatchers.IO) {
        val cleaned = title.replace(BRACKETED, "").trim()
        if (cleaned.isEmpty()) return@withContext ApiResult.Ok(emptyList())

        val imdbId = resolveImdbId(cleaned, isSerial)
            ?: return@withContext ApiResult.Ok(emptyList())

        val url = "${baseUrl.trimEnd('/')}$PATH".toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter("id", imdbId)
            ?.addQueryParameter("type", if (isSerial) "tv" else "movie")
            ?.addQueryParameter("language", "")
            ?.apply {
                if (isSerial && season != null) addQueryParameter("season", season.toString())
                if (isSerial && episode != null) addQueryParameter("episode", episode.toString())
            }
            ?.build()
            ?: return@withContext ApiResult.Http(0, "Bad base URL", null)

        try {
            val request = Request.Builder()
                .url(url)
                .addHeader("Accept", "application/json")
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { resp ->
                val raw = resp.body.string()
                if (!resp.isSuccessful) {
                    return@use ApiResult.Http(resp.code, raw.take(200), null)
                }
                ApiResult.Ok(parseItems(raw))
            }
        } catch (t: Throwable) {
            ApiResult.Network(t)
        }
    }

    private fun resolveImdbId(title: String, series: Boolean): String? {
        val category = if (series) "series" else "movie"
        val url = "$CINEMETA/catalog/$category/top/search=${encode(title)}.json"
            .toHttpUrlOrNull() ?: return null
        return try {
            okHttpClient.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val metas = JsonParser.parseString(resp.body.string())
                    .asJsonObjectOrNull()
                    ?.getAsJsonArray("metas")
                    ?: return null
                metas.firstNotNullOfOrNull { element ->
                    element.asJsonObjectOrNull()
                        ?.get("id")?.asStringOrNull()
                        ?.takeIf { it.startsWith("tt") }
                }
            }
        } catch (_: Throwable) {
            null
        }
    }

    /** Internal so a test can drive it: which upstream answered decides the field names. */
    internal fun parseItems(raw: String): List<OnlineSubtitle> {
        val items = runCatching {
            JsonParser.parseString(raw).asJsonObjectOrNull()?.getAsJsonArray("items")
        }.getOrNull() ?: return emptyList()

        return items.mapNotNull { element ->
            val obj = element.asJsonObjectOrNull() ?: return@mapNotNull null
            val file = obj.firstString("file", "url") ?: return@mapNotNull null
            if (file.isBlank()) return@mapNotNull null
            val language = (obj.firstString("language", "lang") ?: "").uppercase()
            OnlineSubtitle(
                url = file,
                language = language,
                display = obj.firstString("label", "display") ?: language,
                fileName = obj.firstString("fileName", "file_name", "name") ?: "",
                format = obj.firstString("format", "ext") ?: "",
                hearingImpaired = obj.get("hearingImpaired")?.let {
                    runCatching { it.asBoolean }.getOrNull()
                } ?: false,
            )
        }
    }

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private fun com.google.gson.JsonElement.asJsonObjectOrNull(): JsonObject? =
        runCatching { asJsonObject }.getOrNull()

    private fun com.google.gson.JsonElement.asStringOrNull(): String? =
        runCatching { asString }.getOrNull()

    private fun JsonObject.firstString(vararg keys: String): String? {
        for (key in keys) {
            val value = get(key)?.asStringOrNull()
            if (!value.isNullOrBlank()) return value
        }
        return null
    }

    private companion object {
        const val PATH = "/contents/subtitles"
        const val CINEMETA = "https://v3-cinemeta.strem.io"

        val BRACKETED = Regex("""\(.*?\)""")
    }
}

data class OnlineSubtitle(
    val url: String,
    val language: String,
    val display: String,
    val fileName: String = "",
    val format: String = "",
    val hearingImpaired: Boolean = false,
) {
    val primaryLabel: String get() = fileName.ifBlank { display.ifBlank { language } }

    val detailLabel: String
        get() = buildList {
            if (language.isNotBlank()) add(language)
            if (display.isNotBlank() && !display.equals(language, ignoreCase = true)) add(display)
            if (format.isNotBlank()) add(format)
            if (hearingImpaired) add("CC")
        }.joinToString(" · ")
}
