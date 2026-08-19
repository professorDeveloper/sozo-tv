package com.saikou.sozo_tv.data.repository

import com.saikou.sozo_tv.data.extensions.ExtDetail
import com.saikou.sozo_tv.data.extensions.ExtensionContentRegistry
import com.saikou.sozo_tv.data.extensions.ExtensionEngine
import com.saikou.sozo_tv.data.extensions.toCast
import com.saikou.sozo_tv.data.extensions.toDetailModel
import com.saikou.sozo_tv.data.extensions.toMainModel
import com.saikou.sozo_tv.domain.model.Cast
import com.saikou.sozo_tv.domain.model.CastDetailModel
import com.saikou.sozo_tv.domain.model.DetailModel
import com.saikou.sozo_tv.domain.model.MainModel
import com.saikou.sozo_tv.domain.repository.DetailRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import java.util.concurrent.ConcurrentHashMap

/**
 * Detail/cast/relations all derive from a single `ExtensionEngine.load()` payload.
 * The synthetic media `id` is resolved back to its `(provider,url)` via
 * [ExtensionContentRegistry], loaded once, and cached so cast/relations re-use it.
 */
class DetailRepositoryImpl(
    private val engine: ExtensionEngine,
    private val links: AnilistLinkStore,
    private val sourceRegistry: AnilistSourceRegistry,
) : DetailRepository {

    private val cache = ConcurrentHashMap<Int, ExtDetail>()

    /**
     * The detail, the cast and the relations are three separate calls that all want the same
     * payload, and they are fired back to back. Caching the resolved value only helped the
     * second visit: within one open, all three missed the empty cache and ran a full load
     * each. Holding the in-flight job instead means the later two await the first.
     */
    private val inFlight = ConcurrentHashMap<Int, Deferred<ExtDetail?>>()

    /**
     * The shared load belongs to the repository, not to whichever caller happened to ask
     * first — otherwise that caller navigating away cancels the load the other two are
     * waiting on.
     */
    private val loadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private suspend fun loadDetail(id: Int): ExtDetail? {
        cache[id]?.let { return it }
        val job = inFlight.computeIfAbsent(id) {
            loadScope.async(start = CoroutineStart.LAZY) {
                val entry = ExtensionContentRegistry.resolve(id)
                    ?: return@async null
                val detail = engine.load(entry.provider, entry.url)
                    ?: return@async null
                cache[id] = detail
                recordAnilistId(detail)
                detail
            }
        }
        return try {
            job.await()
        } finally {
            inFlight.remove(id, job)
        }
    }

    private fun recordAnilistId(detail: ExtDetail) {
        val mediaId = detail.anilistId ?: return
        if (mediaId <= 0) return

        sourceRegistry.remember(detail.provider)
        if (links.mediaIdFor(detail.provider, detail.contentUrl) != null) return

        links.save(
            AnilistTitleLink(
                provider = detail.provider,
                contentId = detail.contentUrl,
                mediaId = mediaId,
                title = detail.title,
                coverImage = detail.thumbnail,
                totalEpisodes = detail.episodes.size.takeIf { it > 0 },
                linkedAt = System.currentTimeMillis(),
                auto = false,
            )
        )
    }

    private suspend fun detailResult(id: Int): Result<DetailModel> {
        return try {
            val detail = loadDetail(id)
                ?: return Result.failure(IllegalStateException("Content not found for this source."))
            Result.success(detail.toDetailModel(id))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun loadAnimeDetail(id: Int): Result<DetailModel> = detailResult(id)
    override suspend fun loadMovieDetail(id: Int): Result<DetailModel> = detailResult(id)
    override suspend fun loadSeriesDetail(id: Int): Result<DetailModel> = detailResult(id)

    override suspend fun loadRandomAnime(): Result<List<MainModel>> = Result.success(emptyList())

    override suspend fun loadCast(id: Int): Result<List<Cast>> {
        return try {
            val detail = loadDetail(id)
            Result.success(detail?.cast?.mapIndexed { i, c -> c.toCast(i) } ?: emptyList())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun loadCastMovieSeries(id: Int, isMovie: Boolean): Result<List<Cast>> =
        loadCast(id)

    override suspend fun loadAnimeRelations(id: Int): Result<List<MainModel>> {
        return try {
            val detail = loadDetail(id)
            Result.success(detail?.related?.map { it.toMainModel() } ?: emptyList())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun loadMovieOrSeriesRelations(id: Int, isMovie: Boolean): Result<List<MainModel>> =
        loadAnimeRelations(id)

    override suspend fun characterDetail(id: Int): Result<CastDetailModel> =
        Result.failure(UnsupportedOperationException("Character details are not available from extensions."))

    override suspend fun creditDetail(id: Int): Result<CastDetailModel> =
        Result.failure(UnsupportedOperationException("Credit details are not available from extensions."))
}
