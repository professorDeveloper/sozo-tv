package com.saikou.sozo_tv.engine.server

import com.saikou.sozo_tv.utils.SOZO_USER_AGENT
import android.net.Uri
import com.saikou.sozo_tv.BuildConfig

class ApisozoClient(private val baseUrl: String = BuildConfig.APISOZO_BASE_URL) {

    fun get(path: String, query: Map<String, String?> = emptyMap()): String? {
        val url = buildUrl(path, query)
        return try {
            client.newCall(okhttp3.Request.Builder().url(url).header("User-Agent", UA)
                .header("Accept", "application/json").build()).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
        } catch (t: Throwable) {
            // Failure breadcrumb. Carry the message and the root cause: the exception type alone
            // cannot distinguish a dead server from a TLS/route problem, which is the whole
            // question when this fires.
            val root = generateSequence(t) { it.cause }.last()
            android.util.Log.w(
                "ApisozoClient",
                "GET $path failed: ${t.javaClass.simpleName}: ${t.message}" +
                        if (root !== t) " <- ${root.javaClass.simpleName}: ${root.message}" else ""
            )
            null
        }
    }

    private fun buildUrl(path: String, query: Map<String, String?>): String {
        val base = baseUrl.trimEnd('/')
        val suffix = if (path.startsWith("/")) path else "/$path"
        val builder = Uri.parse(base + suffix).buildUpon()
        query.forEach { (k, v) -> if (!v.isNullOrEmpty()) builder.appendQueryParameter(k, v) }
        return builder.build().toString()
    }

    companion object {
        private val UA get() = SOZO_USER_AGENT
        private val client = okhttp3.OkHttpClient.Builder()
            .dns(com.saikou.sozo_tv.engine.network.ExtensionDns.dns)
            .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .callTimeout(45, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }
}
