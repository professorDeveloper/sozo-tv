package com.saikou.sozo_tv.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI

data class VttSpriteCue(
    val startMs: Long,
    val endMs: Long,
    val imageUrl: String,
    val x: Int? = null,
    val y: Int? = null,
    val w: Int? = null,
    val h: Int? = null
)

class VttSpriteThumbnailLoader(
    private val okHttp: OkHttpClient,
    private val headers: Map<String, String> = emptyMap()
) {
    @Volatile private var cues: List<VttSpriteCue> = emptyList()
    @Volatile private var baseVttUrl: String = ""

    private val spriteCache = LruCache<String, Bitmap>(6)
    private val cropCache = LruCache<String, Bitmap>(200)

    fun isLoaded(): Boolean = cues.isNotEmpty()

    fun clear() {
        cues = emptyList()
        baseVttUrl = ""
        spriteCache.evictAll()
        cropCache.evictAll()
    }

    @Throws(Exception::class)
    fun loadVtt(vttUrl: String) {
        baseVttUrl = vttUrl
        val vttText = fetchText(vttUrl) ?: ""
        cues = parseWebVtt(vttText, vttUrl)
    }
    @Throws(Exception::class)
    fun loadVttFromContent(vttContent: String, baseUrl: String) {
        baseVttUrl = baseUrl
        cues = parseWebVtt(vttContent, baseUrl)
    }
    /**
     * Main API: positionMs -> cropped Bitmap
     */
    @Throws(Exception::class)
    fun getThumbnail(positionMs: Long): Bitmap? {
        val localCues = cues
        if (localCues.isEmpty()) return null

        val cueIndex = findCueIndex(localCues, positionMs)
        if (cueIndex < 0) return null
        val cue = localCues[cueIndex]

        val absSpriteUrl = resolveUrl(baseVttUrl, cue.imageUrl)
        val cacheKey = "$cueIndex|$absSpriteUrl|${cue.x},${cue.y},${cue.w},${cue.h}"

        cropCache.get(cacheKey)?.let { return it }

        val sprite = loadSprite(absSpriteUrl) ?: return null

        val bmp = if (cue.x != null && cue.y != null && cue.w != null && cue.h != null) {
            val x = cue.x.coerceIn(0, sprite.width - 1)
            val y = cue.y.coerceIn(0, sprite.height - 1)
            val w = cue.w.coerceIn(1, sprite.width - x)
            val h = cue.h.coerceIn(1, sprite.height - y)
            Bitmap.createBitmap(sprite, x, y, w, h)
        } else {
            sprite
        }

        cropCache.put(cacheKey, bmp)
        return bmp
    }


    private fun fetchText(url: String): String? {
        val reqB = Request.Builder().url(url)
        headers.forEach { (k, v) -> reqB.header(k, v) }
        okHttp.newCall(reqB.build()).execute().use { resp ->
            if (!resp.isSuccessful) return null
            return resp.body?.string()
        }
    }

    private fun loadSprite(absUrl: String): Bitmap? {
        spriteCache.get(absUrl)?.let { return it }

        val reqB = Request.Builder().url(absUrl)
        headers.forEach { (k, v) -> reqB.header(k, v) }

        okHttp.newCall(reqB.build()).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val stream = resp.body?.byteStream() ?: return null

            val opts = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val bmp = BitmapFactory.decodeStream(stream, null, opts) ?: return null
            spriteCache.put(absUrl, bmp)
            return bmp
        }
    }

    private fun findCueIndex(list: List<VttSpriteCue>, positionMs: Long): Int {
        var lo = 0
        var hi = list.lastIndex
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val c = list[mid]
            when {
                positionMs < c.startMs -> hi = mid - 1
                positionMs >= c.endMs -> lo = mid + 1
                else -> return mid
            }
        }
        return -1
    }

    companion object {
        fun parseWebVtt(vtt: String, baseUrl: String): List<VttSpriteCue> {
            val lines = vtt.replace("\r", "").lines()
            val out = mutableListOf<VttSpriteCue>()

            var i = 0
            while (i < lines.size) {
                val line = lines[i].trim()
                if (line.contains("-->")) {
                    val timeParts = line.split("-->").map { it.trim() }
                    val start = parseTimeMs(timeParts[0])
                    val end = parseTimeMs(timeParts[1].substringBefore(" ").trim())

                    i++
                    while (i < lines.size && lines[i].trim().isEmpty()) i++
                    if (i >= lines.size) break

                    val imgLine = lines[i].trim()
                    val xywhToken = when {
                        imgLine.contains("#xywh=") -> "#xywh="
                        imgLine.contains("#xywh:") -> "#xywh:"
                        else -> null
                    }

                    val (imgUrl, xywh) = if (xywhToken != null) {
                        val p = imgLine.split(xywhToken)
                        p[0] to p.getOrNull(1)
                    } else imgLine to null

                    var x: Int? = null
                    var y: Int? = null
                    var w: Int? = null
                    var h: Int? = null

                    xywh?.split(",")?.mapNotNull { it.toIntOrNull() }?.let { nums ->
                        if (nums.size == 4) {
                            x = nums[0]; y = nums[1]; w = nums[2]; h = nums[3]
                        }
                    }

                    out += VttSpriteCue(
                        startMs = start,
                        endMs = end,
                        imageUrl = imgUrl,
                        x = x, y = y, w = w, h = h
                    )
                }
                i++
            }

            return out.sortedBy { it.startMs }
        }

        private fun parseTimeMs(s: String): Long {
            val parts = s.trim().split(":")
            val (h, m, secMs) = when (parts.size) {
                3 -> Triple(parts[0].toLong(), parts[1].toLong(), parts[2])
                2 -> Triple(0L, parts[0].toLong(), parts[1])
                else -> Triple(0L, 0L, parts.last())
            }
            val secParts = secMs.split(".")
            val sec = secParts[0].toLongOrNull() ?: 0L
            val ms = secParts.getOrNull(1)?.padEnd(3, '0')?.take(3)?.toLongOrNull() ?: 0L
            return (((h * 60 + m) * 60 + sec) * 1000) + ms
        }

        private fun resolveUrl(base: String, maybeRelative: String): String {
            return try {
                URI(base).resolve(maybeRelative).toString()
            } catch (_: Exception) {
                maybeRelative
            }
        }
    }
}
