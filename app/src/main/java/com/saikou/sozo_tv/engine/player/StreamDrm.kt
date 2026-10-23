package com.saikou.sozo_tv.engine.player

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.LocalMediaDrmCallback
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import org.json.JSONArray
import org.json.JSONObject
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.decodeHex

/** Carries extractor DRM into both the media item and ClearKey session manager. */
@androidx.media3.common.util.UnstableApi
object StreamDrm {
    fun configure(item: MediaItem.Builder, json: String?): MediaItem.Builder {
        if (json.isNullOrBlank()) return item
        val obj = JSONObject(json)
        val scheme = when (obj.optString("scheme").lowercase()) {
            "widevine", "com.widevine.alpha" -> C.WIDEVINE_UUID
            "clearkey", "org.w3.clearkey" -> C.CLEARKEY_UUID
            "playready", "com.microsoft.playready" -> C.PLAYREADY_UUID
            else -> throw IllegalArgumentException("Unsupported DRM scheme")
        }
        val headers = obj.optJSONObject("licenseHeaders") ?: JSONObject()
        val config = MediaItem.DrmConfiguration.Builder(scheme)
            .setLicenseRequestHeaders(headers.keys().asSequence().associateWith { headers.getString(it) })
        obj.optString("licenseUrl").takeIf { it.isNotBlank() }?.let { config.setLicenseUri(it) }
        return item.setDrmConfiguration(config.build())
    }

    fun configure(factory: DefaultMediaSourceFactory, json: String?): DefaultMediaSourceFactory {
        if (json.isNullOrBlank()) return factory
        val obj = JSONObject(json)
        val keys = obj.optJSONObject("clearKeys") ?: return factory
        if (keys.length() == 0) return factory
        val response = clearKeyResponse(keys).toByteArray(Charsets.UTF_8)
        return factory.setDrmSessionManagerProvider {
            DefaultDrmSessionManager.Builder()
                .setUuidAndExoMediaDrmProvider(C.CLEARKEY_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
                .build(LocalMediaDrmCallback(response))
        }
    }

    internal fun clearKeyResponse(keys: JSONObject): String {
        fun encode(value: String): String {
            val raw = if (value.length % 2 == 0 && value.matches(Regex("[0-9a-fA-F]+")))
                value.decodeHex()
            else requireNotNull(value.decodeBase64()) { "Invalid ClearKey encoding" }
            return raw.base64Url().trimEnd('=')
        }
        val values = JSONArray()
        keys.keys().forEach { id -> values.put(JSONObject().put("kty", "oct")
            .put("kid", encode(id)).put("k", encode(keys.getString(id)))) }
        return JSONObject().put("keys", values).put("type", "temporary").toString()
    }
}
