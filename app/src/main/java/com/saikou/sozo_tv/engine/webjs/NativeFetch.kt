package com.saikou.sozo_tv.engine.webjs

import com.saikou.sozo_tv.utils.SOZO_USER_AGENT
import android.content.Context
import com.lagradost.nicehttp.ignoreAllSSLErrors
import eu.kanade.tachiyomi.network.AndroidCookieJar
import eu.kanade.tachiyomi.network.interceptor.CloudflareInterceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * The HTTP bridge the JS extractors call through `window.dartFetch`. Mirrors soplay's DartFetch:
 * a shared cookie jar plus a transparent Cloudflare solver so a challenged content site is bypassed
 * inside the single fetch (the extractor JS only ever sees real HTML/JSON, never a challenge page).
 */
class NativeFetch(context: Context) {

    // Global WebView CookieManager — shared with CloudflareInterceptor's solver WebView so a solved
    // cf_clearance is automatically sent on the retried/subsequent requests.
    private val cookieJar = AndroidCookieJar()

    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        // Bound a single stalled response so one stuck fetch can't consume the whole provider budget.
        .callTimeout(45, TimeUnit.SECONDS)
        .cookieJar(cookieJar)
        // Content CDNs like uzmovi.net serve an incomplete cert chain (missing intermediate);
        // Android's OkHttp doesn't AIA-fetch it, so the page GET dies with "chain validation
        // failed" and the extractor sees no HTML → "no playable HLS source". The player/proxy
        // clients already trust-all for the same reason; the resolver fetch must match, or
        // on-device resolution (uzmovi/uzdown) never even reaches the proxy. Mirrors soplay's
        // DartFetch, whose Dio accepts any cert.
        .ignoreAllSSLErrors()
        .addInterceptor(CloudflareInterceptor(context.applicationContext, cookieJar) { UA })
        .build()

    fun execute(reqJson: String): String {
        var loggedUrl = ""
        return try {
            val req = JSONObject(reqJson)
            val url = req.optString("url")
            loggedUrl = url
            if (url.isEmpty()) return errorJson()
            val method = req.optString("method", "GET").uppercase()
            val headers = req.optJSONObject("headers")
            val builder = Request.Builder().url(url)
            var contentType: String? = null
            var hasUa = false
            headers?.keys()?.forEach { k ->
                val v = headers.optString(k)
                builder.header(k, v)
                if (k.equals("content-type", true)) contentType = v
                if (k.equals("user-agent", true)) hasUa = true
            }
            // Many sites block requests with no UA; give a browser one when the extractor omits it.
            if (!hasUa) builder.header("User-Agent", UA)
            val bodyRaw = if (req.isNull("body")) null else req.opt("body")
            val body = when {
                bodyRaw == null -> null
                bodyRaw is JSONObject || bodyRaw is JSONArray ->
                    bodyRaw.toString().toRequestBody((contentType ?: "application/json").toMediaTypeOrNull())
                else -> bodyRaw.toString().toRequestBody(contentType?.toMediaTypeOrNull())
            }
            if (method == "GET" || method == "HEAD") {
                builder.method(method, null)
            } else {
                builder.method(method, body ?: "".toRequestBody(null))
            }
            client.newCall(builder.build()).execute().use { resp ->
                val text = resp.body?.string() ?: ""
                android.util.Log.i("NativeFetch", "$method $url -> ${resp.code} (${text.length}b)")
                val respHeaders = JSONObject()
                resp.headers.names().forEach { n ->
                    respHeaders.put(n.lowercase(), resp.headers.values(n).joinToString(","))
                }
                val ct = resp.header("content-type") ?: ""
                JSONObject().apply {
                    put("status", resp.code)
                    put("headers", respHeaders)
                    put("data", decodeBody(text, ct))
                }.toString()
            }
        } catch (t: Throwable) {
            android.util.Log.w("NativeFetch", "fetch $loggedUrl failed: ${t.javaClass.simpleName}: ${t.message}")
            errorJson()
        }
    }

    private fun decodeBody(text: String, contentType: String): Any {
        if (text.isEmpty()) return text
        if (contentType.contains("application/json", true)) {
            val obj = runCatching { JSONObject(text) }.getOrNull()
            if (obj != null) return obj
            val arr = runCatching { JSONArray(text) }.getOrNull()
            if (arr != null) return arr
        }
        return text
    }

    private fun errorJson(): String = JSONObject().apply {
        put("status", 0)
        put("data", JSONObject.NULL)
        put("headers", JSONObject())
    }.toString()

    companion object {
        private val UA get() = SOZO_USER_AGENT
    }
}
