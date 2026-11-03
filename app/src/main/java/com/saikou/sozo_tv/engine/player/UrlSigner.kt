package com.saikou.sozo_tv.engine.player

import com.saikou.sozo_tv.engine.network.ExtensionDns
import com.saikou.sozo_tv.utils.SOZO_USER_AGENT
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

/**
 * A source's file that the device has to sign for itself before playing. Port of the phone app's
 * url_signer.dart.
 *
 * asilmedia's fayllar1 hands out a file only through a signed URL whose token is bound to the
 * User-Agent and IP that asked for it, so the server cannot sign on the TV's behalf. It sends
 * where to ask, under which query parameter the file goes, and with what headers; the answer is
 * a JSON map from the file as sent to its signed URL.
 */
class UrlSigner(
    val url: String,
    val param: String,
    val headers: Map<String, String> = emptyMap(),
) {

    /**
     * The signed URL for [file], asked with [playerHeaders]' User-Agent, which the token is bound
     * to. Null when the host would not sign it; the caller then plays the file as it was.
     */
    fun sign(file: String, playerHeaders: Map<String, String>, client: OkHttpClient = defaultClient): String? {
        val ua = playerHeaders.entries.firstOrNull { it.key.equals("User-Agent", true) }?.value
            ?: SOZO_USER_AGENT
        val key = "$file\u0000$ua"
        val now = System.currentTimeMillis()
        synchronized(cache) {
            cache[key]?.takeIf { now - it.second < KEEP_MS }?.let { return it.first }
        }
        return runCatching {
            val request = Request.Builder().url(requestUrl(file)).apply {
                headers.forEach { (k, v) -> header(k, v) }
                header("User-Agent", ua)
            }.build()
            client.newCall(request).execute().use { res ->
                if (res.code != 200) return null
                signedUrlFrom(res.body?.string().orEmpty())
            }
        }.getOrNull()?.also { signed ->
            synchronized(cache) {
                if (cache.size > 64) cache.clear()
                cache[key] = signed to now
            }
        }
    }

    /** Sent decoded, as the page itself sends it: the host keys its answer by the file as asked. */
    internal fun requestUrl(file: String): String =
        url.toHttpUrl().newBuilder()
            .setQueryParameter(param, URLDecoder.decode(file.replace("+", "%2B"), "UTF-8"))
            .build().toString()

    companion object {
        private const val KEEP_MS = 20 * 60 * 1000L

        // Tokens expire, so a retry after that must ask again.
        private val cache = HashMap<String, Pair<String, Long>>()

        private val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .dns(ExtensionDns.dns)
                .callTimeout(8, TimeUnit.SECONDS)
                .build()
        }

        fun fromJson(json: String?): UrlSigner? {
            if (json.isNullOrBlank()) return null
            val o = runCatching { JSONObject(json) }.getOrNull() ?: return null
            val url = o.opt("url") as? String
            val param = o.opt("param") as? String
            if (url.isNullOrEmpty() || param.isNullOrEmpty()) return null
            val raw = o.optJSONObject("headers")
            val headers = raw?.keys()?.asSequence()
                ?.filter { !raw.isNull(it) }
                ?.associateWith { raw.get(it).toString() }
                .orEmpty()
            return UrlSigner(url, param, headers)
        }

        internal fun signedUrlFrom(body: String): String? {
            val signed = when (val v = runCatching { JSONTokener(body).nextValue() }.getOrNull()) {
                is JSONObject -> v.keys().asSequence().map { v.opt(it) }.filterIsInstance<String>().firstOrNull()
                is JSONArray -> (0 until v.length()).map { v.opt(it) }.filterIsInstance<String>().firstOrNull()
                is String -> v
                else -> null
            }
            return signed?.takeIf { it.startsWith("http") }
        }

        internal fun clearCache() = synchronized(cache) { cache.clear() }
    }
}
