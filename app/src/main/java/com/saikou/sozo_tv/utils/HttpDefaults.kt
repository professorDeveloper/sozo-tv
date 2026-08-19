package com.saikou.sozo_tv.utils

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
 * This value is the one Aniyomi extensions already used, so existing clearances
 * keep working and the other callers move onto it.
 */
const val SOZO_USER_AGENT: String =
    "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
