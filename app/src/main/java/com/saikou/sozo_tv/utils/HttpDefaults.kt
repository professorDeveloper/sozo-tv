package com.saikou.sozo_tv.utils

import android.content.Context
import android.webkit.WebSettings

/**
 * The single User-Agent the whole app presents.
 *
 * Cloudflare binds cf_clearance to the exact User-Agent that solved the
 * challenge. The engines each carried their own constant — five different
 * strings — so a link resolved by one (WebView, NativeFetch, an Aniyomi
 * extension) and then played by another was sent with a UA the cookie was not
 * issued for, and the CDN answered 403 on the manifest. Sharing the cookie jar
 * with the player is only half the fix; the UA has to match too.
 *
 * One string, but not a *made-up* one. A managed challenge does not only read
 * the header: it compares what the header claims against what the engine
 * actually is — client hints, `navigator.userAgentData`, platform, engine
 * version. A hard-coded `Chrome/124` on a device whose WebView is 130-something
 * is exactly that kind of mismatch, and the challenge then never clears no
 * matter how long the WebView is left running. Measured against animepahe.pw,
 * which sits behind a managed challenge: a browser presenting a User-Agent that
 * matched its real platform cleared in ~4s, while every UA that misdescribed
 * the platform sat at "Just a moment..." until the watchdog gave up.
 *
 * So the value is this device's OWN WebView User-Agent, read once via
 * [initSozoUserAgent]. [SOZO_USER_AGENT_FALLBACK] applies only if that read
 * fails.
 */
const val SOZO_USER_AGENT_FALLBACK: String =
    "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

@Volatile
private var resolvedUserAgent: String = SOZO_USER_AGENT_FALLBACK

/** The agent every request in this app must carry. */
val SOZO_USER_AGENT: String
    get() = resolvedUserAgent

/** Whether the value in use came from the device rather than the fallback. */
val sozoUserAgentIsDeviceReported: Boolean
    get() = resolvedUserAgent != SOZO_USER_AGENT_FALLBACK

/**
 * Adopt the device's real WebView User-Agent. Call once, from Application.
 *
 * Safe to call more than once and safe to fail: on any error the fallback stays
 * in place and the app behaves exactly as it did before.
 */
fun initSozoUserAgent(context: Context) {
    val ua = runCatching { WebSettings.getDefaultUserAgent(context) }
        .getOrNull()
        ?.trim()
        ?: return
    // A WebView that reports something implausible is worse than the fallback:
    // the whole point is that the string matches the engine behind it.
    if (ua.length < 40 || !ua.startsWith("Mozilla/")) return
    // Verbatim, `; wv` and all.
    //
    // An earlier version stripped that token so the string would not announce
    // an embedded browser. That was the same mistake in a smaller costume: the
    // engine behind this really is an Android WebView, and a challenge that
    // compares the header against the engine sees a UA claiming plain Chrome
    // and a WebView answering. The whole point of reading the device's agent is
    // that it is TRUE — editing it puts the lie back.
    resolvedUserAgent = ua
}
