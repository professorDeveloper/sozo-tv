package com.saikou.sozo_tv.engine.player

import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import com.saikou.sozo_tv.data.model.SubTitle
import com.saikou.sozo_tv.domain.player.SubtitleBody
import com.saikou.sozo_tv.domain.player.SubtitleNormalizer
import com.saikou.sozo_tv.utils.SOZO_USER_AGENT
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

@UnstableApi
class SubtitleSideloader(
    private val cacheDir: File,
    private val client: () -> OkHttpClient,
) {

    sealed class Outcome {
        data class Attached(val config: MediaItem.SubtitleConfiguration) : Outcome()
        data class Failed(val label: String) : Outcome()
        object None : Outcome()
    }

    private var cached: Pair<String, String>? = null
    private var written: File? = null

    @Synchronized
    fun prepare(chosen: SubTitle?, offsetMs: Long): Outcome {
        if (chosen == null || chosen.file.isBlank()) return Outcome.None
        return try {
            val body = body(chosen)
            val r = SubtitleNormalizer.normalize(body, offsetMs)
            if (r.cues == 0) throw IOException("no cues in body (${body.length} chars)")

            val ssa = r.kind == SubtitleNormalizer.Kind.SSA
            val out = File(cacheDir, "$FILE_PREFIX${System.currentTimeMillis()}.${if (ssa) "ass" else "vtt"}")
            out.writeText(r.text, Charsets.UTF_8)
            written?.takeIf { it != out }?.let { runCatching { it.delete() } }
            written = out

            val config = MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(out))
                .setMimeType(if (ssa) MimeTypes.TEXT_SSA else MimeTypes.TEXT_VTT)
                .setLabel(chosen.label.takeIf { it.isNotBlank() })
                .setLanguage(languageCodeOf(chosen.label))
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .setId(TRACK_ID)
                .build()
            Log.i("PlayerSubs", "sideloaded ${chosen.label} from ${chosen.file}")
            Outcome.Attached(config)
        } catch (e: Exception) {
            Log.e("PlayerSubs", "sideload failed for ${chosen.file}: ${e.message}", e)
            Outcome.Failed(chosen.label)
        }
    }

    private fun body(chosen: SubTitle): String {
        cached?.let { (url, body) -> if (url == chosen.file) return body }

        val retryReferer = originOf(chosen.file)
            ?.takeIf { chosen.headers.keys.none { k -> k.equals("referer", true) } }

        val bytes = fetch(chosen, referer = null)
            ?: retryReferer?.let { fetch(chosen, referer = it) }
            ?: throw IOException("subtitle host refused the request")

        val body = SubtitleBody.decode(bytes, chosen.label)
        cached = chosen.file to body
        return body
    }

    private fun fetch(chosen: SubTitle, referer: String?): ByteArray? {
        val request = Request.Builder()
            .url(chosen.file)
            .apply {
                chosen.headers.forEach { (k, v) -> header(k, v) }
                if (chosen.headers.keys.none { it.equals("user-agent", true) }) {
                    header("User-Agent", SOZO_USER_AGENT)
                }
                referer?.let { header("Referer", it) }
            }
            .build()

        client().newCall(request).execute().use { response ->
            if (response.code == 401 || response.code == 403) return null
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            return response.body.bytes()
        }
    }

    private fun originOf(url: String): String? = runCatching {
        val uri = Uri.parse(url)
        val scheme = uri.scheme ?: return@runCatching null
        val host = uri.host ?: return@runCatching null
        "$scheme://$host/"
    }.getOrNull()

    private fun languageCodeOf(label: String): String? {
        val head = label.trim().substringBefore(' ').substringBefore('-').lowercase()
        return head.takeIf { it.length in 2..3 && it.all { c -> c in 'a'..'z' } }
    }

    @Synchronized
    fun clear() {
        cacheDir.listFiles { f -> f.name.startsWith(FILE_PREFIX) }
            ?.forEach { runCatching { it.delete() } }
        written = null
        cached = null
    }

    companion object {
        const val TRACK_ID = "sozo-sideloaded-subtitle"
        private const val FILE_PREFIX = "sub_"
    }
}
