package com.saikou.sozo_tv.engine.webjs

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class NativeFetch {

    private val cookies = ConcurrentHashMap<String, MutableList<Cookie>>()

    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .cookieJar(object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, list: List<Cookie>) {
                val store = cookies.getOrPut(url.host) { mutableListOf() }
                list.forEach { c ->
                    store.removeAll { it.name == c.name }
                    store.add(c)
                }
            }

            override fun loadForRequest(url: HttpUrl): List<Cookie> = cookies[url.host] ?: emptyList()
        })
        .build()

    fun execute(reqJson: String): String {
        return try {
            val req = JSONObject(reqJson)
            val url = req.optString("url")
            if (url.isEmpty()) return errorJson()
            val method = req.optString("method", "GET").uppercase()
            val headers = req.optJSONObject("headers")
            val builder = Request.Builder().url(url)
            var contentType: String? = null
            headers?.keys()?.forEach { k ->
                val v = headers.optString(k)
                builder.header(k, v)
                if (k.equals("content-type", true)) contentType = v
            }
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
}
