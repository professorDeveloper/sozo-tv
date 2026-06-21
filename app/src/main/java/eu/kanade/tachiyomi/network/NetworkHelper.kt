package eu.kanade.tachiyomi.network

import android.content.Context
import eu.kanade.tachiyomi.network.interceptor.CloudflareInterceptor
import eu.kanade.tachiyomi.network.interceptor.IgnoreGzipInterceptor
import eu.kanade.tachiyomi.network.interceptor.UncaughtExceptionInterceptor
import eu.kanade.tachiyomi.network.interceptor.UserAgentInterceptor
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

class NetworkHelper(context: Context) {

    val cookieJar = AndroidCookieJar()

    val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(2, TimeUnit.MINUTES)
        .cache(
            Cache(
                directory = File(context.cacheDir, "aniyomi_network_cache"),
                maxSize = 5L * 1024 * 1024,
            ),
        )
        .ignoreAllSSLErrors()
        .addInterceptor(UncaughtExceptionInterceptor())
        .addInterceptor(UserAgentInterceptor(::defaultUserAgentProvider))
        .addNetworkInterceptor(IgnoreGzipInterceptor())
        .addInterceptor(CloudflareInterceptor(context, cookieJar, ::defaultUserAgentProvider))
        .build()

    val downloadClient = client.newBuilder().callTimeout(20, TimeUnit.MINUTES).build()

    @Deprecated("The regular client handles Cloudflare by default")
    @Suppress("UNUSED")
    val cloudflareClient: OkHttpClient = client

    companion object {
        fun defaultUserAgentProvider(): String =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
    }
}

/**
 * Streaming extensions hit arbitrary hosts with frequently-broken TLS chains, and a
 * device clock that is even slightly off makes servers' stapled OCSP responses look
 * "out-of-date" (CertPathValidatorException). CloudStream and most extension apps
 * accept any chain for this reason; mirror that so a single bad-cert host can't take
 * down a whole provider's home/section/play request.
 */
private fun OkHttpClient.Builder.ignoreAllSSLErrors(): OkHttpClient.Builder {
    val trustAll = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
    val sslContext = SSLContext.getInstance("TLS").apply {
        init(null, arrayOf(trustAll), SecureRandom())
    }
    sslSocketFactory(sslContext.socketFactory, trustAll)
    hostnameVerifier { _, _ -> true }
    return this
}
