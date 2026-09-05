package com.saikou.sozo_tv.engine.player

import android.content.Context
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.lagradost.nicehttp.ignoreAllSSLErrors
import com.saikou.sozo_tv.utils.SOZO_USER_AGENT
import eu.kanade.tachiyomi.network.AndroidCookieJar
import eu.kanade.tachiyomi.network.interceptor.CloudflareInterceptor
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

@UnstableApi
class StreamHttp(context: Context) {

    @Volatile
    var headers: Map<String, String> = emptyMap()

    private val appContext = context.applicationContext

    private val cloudflare = CloudflareInterceptor(appContext, AndroidCookieJar()) { SOZO_USER_AGENT }

    val client: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(true).followSslRedirects(true)
        .connectionSpecs(
            listOf(ConnectionSpec.MODERN_TLS, ConnectionSpec.COMPATIBLE_TLS, ConnectionSpec.CLEARTEXT)
        )
        .addNetworkInterceptor { chain ->
            val original = chain.request()
            val current = headers
            val b = original.newBuilder()
            current.forEach { (k, v) -> b.header(k, v) }
            if (current.keys.none { it.equals("User-Agent", true) }) {
                b.header("User-Agent", SOZO_USER_AGENT)
            }
            val req = b.build()
            val resp = chain.proceed(req)
            if (!resp.isSuccessful) Log.w("PlayerHttp", "${resp.code} ${req.url}")
            resp
        }
        .connectTimeout(STREAM_TIMEOUT_S, TimeUnit.SECONDS)
        .readTimeout(STREAM_TIMEOUT_S, TimeUnit.SECONDS)
        .cookieJar(AndroidCookieJar())
        .addInterceptor(cloudflare)
        .ignoreAllSSLErrors()
        .build()

    val dataSourceFactory: DataSource.Factory =
        DefaultDataSource.Factory(appContext, OkHttpDataSource.Factory(client))

    val subtitleClient: OkHttpClient by lazy {
        client.newBuilder()
            .apply { networkInterceptors().clear() }
            .connectTimeout(SUBTITLE_TIMEOUT_S, TimeUnit.SECONDS)
            .readTimeout(SUBTITLE_TIMEOUT_S, TimeUnit.SECONDS)
            .build()
    }

    val proxyClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .followRedirects(true).followSslRedirects(true)
            .cookieJar(AndroidCookieJar())
            .addInterceptor(cloudflare)
            .connectTimeout(STREAM_TIMEOUT_S, TimeUnit.SECONDS)
            .readTimeout(STREAM_TIMEOUT_S, TimeUnit.SECONDS)
            .ignoreAllSSLErrors()
            .build()
    }

    private companion object {
        const val STREAM_TIMEOUT_S = 30L
        const val SUBTITLE_TIMEOUT_S = 10L
    }
}
