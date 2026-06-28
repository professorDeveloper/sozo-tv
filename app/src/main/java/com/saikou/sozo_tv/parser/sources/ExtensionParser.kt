package com.saikou.sozo_tv.parser.sources

import androidx.media3.common.MimeTypes
import com.saikou.sozo_tv.data.extensions.ExtensionEngine
import com.saikou.sozo_tv.data.model.hianime.MegaTrack
import com.saikou.sozo_tv.parser.base.BaseParser
import com.saikou.sozo_tv.parser.models.AudioType
import com.saikou.sozo_tv.parser.models.Data
import com.saikou.sozo_tv.parser.models.EpisodeData
import com.saikou.sozo_tv.parser.models.ShowResponse
import com.saikou.sozo_tv.parser.models.Video
import com.saikou.sozo_tv.parser.models.VideoOption
import com.saikou.sozo_tv.utils.LocalData

/**
 * Adapts the Aniyomi/CloudStream [ExtensionEngine] to the app's [BaseParser]
 * contract, so the existing episode screen + series player drive extension content
 * without any UI changes:
 *
 *  - [search]          -> engine.search    -> ShowResponse(link = "<provider>+<url>")
 *  - [loadEpisodes]    -> engine.load       -> EpisodeData(session = "<provider>+<mediaRef>")
 *  - [getEpisodeVideo] -> engine.loadLinks  -> one VideoOption per quality
 *
 * **Provider is encoded into every id** (show link + episode session). This makes
 * the id self-describing, so playback resolves the *content's own* provider rather
 * than the globally-active one. Without this, continue-watching an item from
 * provider A after switching the active source to B would call B with A's mediaRef
 * and fail. The active provider is only a fallback for legacy/un-encoded ids.
 */
class ExtensionParser : BaseParser() {

    override val name: String = "Extensions"

    private val engine get() = ExtensionEngine.shared
    private fun activeProvider(): String? = engine.getActiveProvider()

    private fun tag(provider: String?, value: String): String =
        if (provider.isNullOrEmpty()) value else "$provider$SEP$value"

    /** Returns (provider?, value) — provider is null for legacy un-encoded ids. */
    private fun untag(value: String): Pair<String?, String> {
        val i = value.indexOf(SEP)
        return if (i >= 0) value.substring(0, i) to value.substring(i + SEP.length) else null to value
    }

    override suspend fun search(query: String): List<ShowResponse> {
        val page = engine.search(null, query) ?: return emptyList()
        return page.items.map {
            ShowResponse(
                name = it.title,
                link = tag(it.provider, it.contentUrl),
                coverUrl = it.thumbnail ?: LocalData.anime404,
            )
        }
    }

    override suspend fun loadEpisodes(id: String, page: Int, showResponse: ShowResponse): EpisodeData? {
        val (encoded, url) = untag(id)
        val p = encoded ?: activeProvider() ?: return null
        val detail = engine.load(p, url) ?: return null
        val list = detail.episodes.mapIndexed { index, ep ->
            Data(
                episode = if (ep.episode > 0) ep.episode else index + 1,
                episode2 = ep.episode,
                title = ep.label,
                session = tag(p, ep.mediaRef),
                snapshot = ep.image ?: detail.thumbnail ?: LocalData.anime404,
                season = ep.season ?: 1,
                serverId = p,
            )
        }
        return EpisodeData(
            current_page = 1,
            data = list,
            from = 1,
            last_page = 1,
            next_page_url = null,
            per_page = list.size.coerceAtLeast(1),
            prev_page_url = null,
            to = list.size,
            total = list.size,
        )
    }

    override suspend fun getEpisodeVideo(id: String, epId: String, epNum: Int): List<VideoOption> {
        val (encoded, mediaRef) = untag(epId)
        val p = encoded ?: activeProvider() ?: return emptyList()
        val media = engine.loadLinks(p, mediaRef)
        val tracks = media.subtitles.map { MegaTrack(it.file, it.label, "captions") }
        return media.sources.map { s ->
            VideoOption(
                videoUrl = s.videoUrl,
                fansub = s.host ?: "",
                resolution = s.quality,
                audioType = AudioType.SUB,
                quality = s.quality,
                isActive = s.isDefault,
                mimeTypes = if (s.type == "hls") MimeTypes.APPLICATION_M3U8 else MimeTypes.VIDEO_MP4,
                fullText = s.quality,
                tracks = tracks,
                // Merge media-level headers (Referer/User-Agent the extractor often sets globally)
                // with the per-source ones (source overrides). Using only s.headers dropped the
                // media-level headers and caused HTTP 403 on hosts that gate on Referer.
                headers = media.headers + s.headers,
            )
        }
    }

    override suspend fun extractVideo(url: String): Video = Video(url, arrayListOf(), emptyMap())

    companion object {
        // Separator between the encoded provider id and the value. U+0001 (SOH)
        // never appears in URLs / mediaRefs, so the split is collision-free. Built
        // from the char code to avoid an invisible control char in the source file.
        private val SEP: String = Char(1).toString()

        /** Build the provider-encoded content link that [loadEpisodes] decodes, so a
         *  caller holding an exact (provider, url) can open it directly without a search. */
        fun encodeLink(provider: String, url: String): String =
            if (provider.isEmpty()) url else "$provider$SEP$url"
    }
}
