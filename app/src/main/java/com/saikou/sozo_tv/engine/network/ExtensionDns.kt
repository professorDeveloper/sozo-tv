package com.saikou.sozo_tv.engine.network

import android.content.Context
import android.util.Log
import okhttp3.Cache
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.io.File
import java.net.InetAddress

/**
 * Where the extension runtimes look names up.
 *
 * ## What this is, and what it is not
 *
 * It is not a VPN and it is not a proxy. The bytes still travel the same route
 * to the same servers, from the same address; the only thing that changes is
 * who answers the question "what is the address of this host". That is enough
 * for the common case — an ISP that blocks a site by refusing to resolve it —
 * and nothing at all for a block by address or by SNI.
 *
 * Saying so matters: somebody who picks Cloudflare here expecting the privacy
 * of a VPN has been misled by the app, and the setting's own description is the
 * only place that can tell them.
 *
 * ## Why it lives here rather than in one client
 *
 * Three runtimes make their own requests — Aniyomi and Manga share
 * [eu.kanade.tachiyomi.network.NetworkHelper], CloudStream has its own inside
 * the library — and the resolver has to be the same object for all of them or
 * the setting would apply to some sources and not others, with nothing on
 * screen to say which.
 *
 * The bootstrap client is deliberately plain: a DoH resolver has to reach its
 * own endpoint, and routing that lookup through itself is a loop.
 */
object ExtensionDns {

    private const val TAG = "ExtensionDns"

    /** The providers offered, by the id the app persists. */
    enum class Provider(val id: String, val url: String, val ips: List<String>) {
        cloudflare(
            "cloudflare",
            "https://cloudflare-dns.com/dns-query",
            listOf("1.1.1.1", "1.0.0.1", "2606:4700:4700::1111", "2606:4700:4700::1001"),
        ),
        google(
            "google",
            "https://dns.google/dns-query",
            listOf("8.8.8.8", "8.8.4.4", "2001:4860:4860::8888", "2001:4860:4860::8844"),
        ),
        ;

        companion object {
            fun byId(id: String?): Provider? =
                entries.firstOrNull { it.id == id?.trim()?.lowercase() }
        }
    }

    /**
     * Null means the system resolver, which is the default and the right answer
     * for most people.
     */
    @Volatile
    private var resolver: Dns? = null

    @Volatile
    private var selected: String = ""

    /** What the app last applied, for the settings screen to read back. */
    fun current(): String = selected

    fun initialize(context: Context) {
        apply(context, context.getSharedPreferences("extension_network", Context.MODE_PRIVATE).getString("dns", ""))
    }

    fun set(context: Context, id: String?): String = apply(context, id).also {
        context.getSharedPreferences("extension_network", Context.MODE_PRIVATE).edit().putString("dns", it).apply()
    }

    /**
     * Points every extension runtime at [id], or back at the system resolver
     * when it names no provider we know.
     *
     * Returns the id actually in force, so a stored value from a newer build —
     * or a typo — reports itself rather than silently doing nothing.
     */
    @Synchronized
    fun apply(context: Context, id: String?): String {
        val provider = Provider.byId(id)
        if (provider == null) {
            resolver = null
            selected = ""
            Log.i(TAG, "using the system resolver")
            return ""
        }
        resolver = try {
            build(context, provider)
        } catch (t: Throwable) {
            // A resolver we cannot construct must not take name resolution down
            // with it: the system one still works, and the alternative is an app
            // that cannot reach anything at all.
            Log.e(TAG, "could not build ${provider.id}, falling back to the system resolver", t)
            null
        }
        selected = if (resolver == null) "" else provider.id
        Log.i(TAG, "resolver=${selected.ifEmpty { "system" }}")
        return selected
    }

    /**
     * The [Dns] the runtimes install.
     *
     * Reads [resolver] on every lookup rather than capturing it, so changing the
     * setting takes effect on the next request instead of on the next launch —
     * the clients are built once, behind a `lazy`, and are not rebuilt.
     */
    val dns: Dns = Dns { hostname ->
        val active = resolver
        if (active == null) {
            Dns.SYSTEM.lookup(hostname)
        } else {
            try {
                active.lookup(hostname)
            } catch (t: Throwable) {
                // One failed DoH lookup is not a reason to fail the request.
                // The endpoint may be blocked exactly where the user needs it
                // least, and the system resolver is still there.
                Log.w(TAG, "doh lookup failed for $hostname: ${t.javaClass.simpleName}")
                Dns.SYSTEM.lookup(hostname)
            }
        }
    }

    private var bootstrapClient: OkHttpClient? = null

    private fun build(context: Context, provider: Provider): Dns {
        // Its own client, and a plain one. A DoH resolver has to reach its own
        // endpoint, and giving that client the same resolver is a loop.
        val bootstrap = bootstrapClient ?: OkHttpClient.Builder()
            .cache(
                Cache(
                    directory = File(context.cacheDir, "doh_cache"),
                    maxSize = 5L * 1024 * 1024,
                ),
            )
            .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
            .callTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .build().also { bootstrapClient = it }
        return DnsOverHttps.Builder()
            .client(bootstrap)
            .url(provider.url.toHttpUrl())
            // The endpoint's own addresses, so the first lookup does not need
            // the resolver it is trying to replace.
            .bootstrapDnsHosts(provider.ips.mapNotNull { hostAddress(it) })
            .includeIPv6(true)
            .build()
    }

    private fun hostAddress(ip: String): InetAddress? = try {
        InetAddress.getByName(ip)
    } catch (_: Throwable) {
        null
    }
}
