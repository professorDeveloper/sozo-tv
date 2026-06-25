package com.saikou.sozo_tv.engine.server

import android.net.Uri
import com.saikou.sozo_tv.BuildConfig
import java.net.HttpURLConnection
import java.net.URL

class ApisozoClient(private val baseUrl: String = BuildConfig.APISOZO_BASE_URL) {

    fun get(path: String, query: Map<String, String?> = emptyMap()): String? {
        val url = buildUrl(path, query)
        return try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = true
                connectTimeout = 20000
                readTimeout = 30000
                setRequestProperty("User-Agent", UA)
                setRequestProperty("Accept", "application/json")
            }
            val code = conn.responseCode
            if (code in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                runCatching { conn.errorStream?.close() }
                null
            }
        } catch (t: Throwable) {
            null
        }
    }

    private fun buildUrl(path: String, query: Map<String, String?>): String {
        val builder = Uri.parse(baseUrl + path).buildUpon()
        query.forEach { (k, v) -> if (!v.isNullOrEmpty()) builder.appendQueryParameter(k, v) }
        return builder.build().toString()
    }

    companion object {
        private const val UA =
            "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"
    }
}
