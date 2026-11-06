package com.saikou.sozo_tv.domain.player

import java.net.URI

object ExternalPlaybackRules {
    fun isPlayerPackage(packageName: String): Boolean =
        packageName.isNotBlank() && packageName != "com.android.tv.frameworkpackagestubs"

    fun canOpen(url: String, drm: String?, requiresProxy: Boolean): Boolean {
        if (!drm.isNullOrBlank() || requiresProxy) return false
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        return uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank() &&
            uri.host.lowercase() !in setOf("127.0.0.1", "localhost", "::1", "[::1]")
    }
    /** Alternating header names and values is the external player's headers intent convention. */
    fun headerArray(headers: Map<String, String>): Array<String> = headers.flatMap { listOf(it.key, it.value) }.toTypedArray()
}
