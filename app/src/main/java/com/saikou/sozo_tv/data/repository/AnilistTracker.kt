package com.saikou.sozo_tv.data.repository

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AnilistTracker(
    private val repository: AnilistRepository,
    private val links: AnilistLinkStore,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private val autoMatchFailed = HashSet<String>()

    val isConnected: Boolean get() = repository.isConnected

    private suspend fun ensureConnected() {
        if (repository.connection.value is AnilistConnection.Unknown) {
            runCatching { repository.refresh() }
        }
    }

    fun reportEpisodeAsync(
        provider: String,
        contentId: String,
        title: String,
        episodeNumber: Int,
    ): Job = scope.launch {
        runCatching { reportEpisode(provider, contentId, title, episodeNumber) }
            .onFailure { Log.w(TAG, "report failed: ${it.message}") }
    }

    suspend fun reportEpisode(
        provider: String,
        contentId: String,
        title: String,
        episodeNumber: Int,
    ): Int? = mutex.withLock {
        if (episodeNumber <= 0 || contentId.isBlank()) return null
        ensureConnected()
        if (!isConnected) return null

        val mediaId = resolveMediaId(provider, contentId, title) ?: return null
        return if (write(mediaId, episodeNumber)) mediaId else null
    }

    private suspend fun resolveMediaId(
        provider: String,
        contentId: String,
        title: String,
    ): Int? {
        links.mediaIdFor(provider, contentId)?.let { return it }

        val key = AnilistLinkStore.keyFor(provider, contentId)
        if (key in autoMatchFailed) return null

        val match = findExactMatch(title)
        if (match == null) {
            autoMatchFailed.add(key)
            return null
        }

        links.save(
            AnilistTitleLink(
                provider = provider,
                contentId = contentId,
                mediaId = match.id,
                title = match.displayTitle,
                coverImage = match.coverImage,
                totalEpisodes = match.episodes,
                linkedAt = System.currentTimeMillis(),
                auto = true,
            )
        )
        return match.id
    }

    suspend fun findExactMatch(title: String): com.saikou.sozo_tv.data.remote.anilist.AnilistMedia? {
        val wanted = normalizeTitle(title)
        if (wanted.isEmpty()) return null
        return runCatching {
            repository.search(title).firstOrNull { media ->
                media.searchTitles.any { normalizeTitle(it) == wanted }
            }
        }.onFailure { Log.w(TAG, "auto-match failed for \"$title\": ${it.message}") }
            .getOrNull()
    }

    private suspend fun write(mediaId: Int, episodeNumber: Int): Boolean = try {
        val state = repository.entryState(mediaId)
        if (state != null && episodeNumber <= state.progress) {
            false
        } else {
            val result = repository.saveProgress(
                mediaId = mediaId,
                progress = episodeNumber,
                status = repository.statusFor(state?.status, episodeNumber, state?.totalEpisodes),
            )
            Log.d(TAG, "media $mediaId -> episode ${result.progress} (${result.status})")
            true
        }
    } catch (t: Throwable) {
        Log.w(TAG, "write failed for media $mediaId: ${t.message}")
        false
    }

    companion object {
        private const val TAG = "AniListTracker"

        fun normalizeTitle(raw: String): String {
            var s = raw.lowercase().trim()
            NOISE.forEach { s = it.replace(s, " ") }
            s = PUNCTUATION.replace(s, " ")
            return s.trim().replace(WHITESPACE, " ")
        }

        private val NOISE = listOf(
            Regex("""\[[^]]*]"""),
            Regex("""\([^)]*\)"""),
            Regex("""\b(1080p|720p|480p|4k|hd|fhd|bluray|bd|web-?dl|hevc|x26[45])\b"""),
            Regex("""\b(sub|dub|subbed|dubbed|uzbek|uzbekcha|tarjima|rus|russian)\b"""),
        )

        private val PUNCTUATION = Regex("""[.,:;!?'"`~@#${'$'}%^&*_+=<>/\\|{}\[\]()·・…—–-]+""")
        private val WHITESPACE = Regex("""\s+""")
    }
}
