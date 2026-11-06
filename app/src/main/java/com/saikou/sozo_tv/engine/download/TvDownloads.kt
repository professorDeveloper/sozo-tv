package com.saikou.sozo_tv.engine.download

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheKeyFactory
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.offline.*
import com.saikou.sozo_tv.data.local.pref.ActiveProfileStore
import com.saikou.sozo_tv.data.model.VodMovieResponse
import com.saikou.sozo_tv.engine.player.LocalHlsProxy
import com.saikou.sozo_tv.engine.player.StreamHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.koin.core.context.GlobalContext
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Download state and bytes belong to Media3, so queues survive process death and can resume. */
@UnstableApi
object TvDownloads {
    private var manager: DownloadManager? = null
    private var cache: SimpleCache? = null
    private val executor = Executors.newFixedThreadPool(2)

    fun ownerToken(): Pair<String, Long> {
        val snapshot = GlobalContext.get().get<ActiveProfileStore>().snapshot()
        return (snapshot.profile?.id ?: "guest") to snapshot.revision
    }
    fun owner(): String = ownerToken().first

    @Synchronized
    private fun cache(context: Context): SimpleCache = cache ?: SimpleCache(
        File(context.applicationContext.filesDir, "offline-media"), NoOpCacheEvictor(),
        StandaloneDatabaseProvider(context.applicationContext),
    ).also { cache = it }

    @Synchronized
    fun manager(context: Context): DownloadManager = manager ?: run {
        val app = context.applicationContext
        val index = DefaultDownloadIndex(StandaloneDatabaseProvider(app), "tv_offline")
        DownloadManager(app, index, DownloaderFactory { request ->
            val metadata = DownloadMetadata.decode(request.data)
            val upstream = DownloadUpstream(app, metadata)
            val delegate = DefaultDownloaderFactory(
                cacheFactory(app, request.id).setUpstreamDataSourceFactory(upstream.factory), executor,
            ).createDownloader(request)
            object : Downloader {
                override fun download(listener: Downloader.ProgressListener?) {
                    try { delegate.download(listener) } finally { upstream.close() }
                }
                override fun cancel() { delegate.cancel(); upstream.close() }
                override fun remove() { try { delegate.remove() } finally { upstream.close() } }
            }
        }).apply {
            maxParallelDownloads = 2
            minRetryCount = 2
            resumeDownloads()
        }.also { manager = it }
    }

    fun cacheFactory(context: Context, id: String): CacheDataSource.Factory = CacheDataSource.Factory()
        .setCache(cache(context)).setCacheKeyFactory(CacheKeyFactory { spec ->
            "$id:${LocalHlsProxy.canonicalUpstreamUrl(spec.uri.toString()) ?: spec.uri}"
        }).setFlags(CacheDataSource.FLAG_BLOCK_ON_CACHE)

    /** A completed item plays exclusively from disk, never silently switching back to the network. */
    fun offlineFactory(context: Context, id: String): DataSource.Factory =
        cacheFactory(context, id).setUpstreamDataSourceFactory(null)

    suspend fun enqueue(context: Context, title: String, vod: VodMovieResponse): String = withContext(Dispatchers.Main) {
        DownloadRules.rejectReason(vod.urlobj, vod.drm, vod.audioTracks.isNotEmpty())?.let {
            throw IllegalArgumentException(it)
        }
        require(context.filesDir.usableSpace > 128L * 1024 * 1024) { "Not enough free space for a download." }
        val mime = when (vod.type.lowercase()) {
            "hls", "m3u8", MimeTypes.APPLICATION_M3U8 -> MimeTypes.APPLICATION_M3U8
            "dash", "mpd", MimeTypes.APPLICATION_MPD -> MimeTypes.APPLICATION_MPD
            else -> if (Uri.parse(vod.urlobj).path.orEmpty().endsWith(".m3u8")) MimeTypes.APPLICATION_M3U8
                else if (Uri.parse(vod.urlobj).path.orEmpty().endsWith(".mpd")) MimeTypes.APPLICATION_MPD else "video/mp4"
        }
        val ownerAtStart = ownerToken()
        val metadata = DownloadMetadata(title, ownerAtStart.first, vod.urlobj, mime, vod.header.toMap(),
            vod.localProxyJson, vod.requestTransformJson)
        val id = DownloadRules.id(metadata.owner, metadata.url)
        withContext(Dispatchers.IO) { cache(context) }
        val index = manager(context).downloadIndex
        val existing = withContext(Dispatchers.IO) { index.getDownload(id) }
        check(ownerToken() == ownerAtStart) { "Profile changed. Start the download again." }
        if (existing?.state == Download.STATE_COMPLETED) return@withContext id
        val upstream = DownloadUpstream(context.applicationContext, metadata)
        val pref = context.getSharedPreferences("playback_quality", Context.MODE_PRIVATE)
            .getString("preferred_resolution", "auto")
        val parameters = TrackSelectionParameters.Builder(context)
            .setForceHighestSupportedBitrate(pref != "lowest")
            .setForceLowestBitrate(pref == "lowest")
            .apply { pref?.toIntOrNull()?.takeIf { it > 0 }?.let { setMaxVideoSize(Int.MAX_VALUE, it) } }
            .build()
        val item = MediaItem.Builder().setUri(metadata.url).setMimeType(mime).build()
        val helper = DownloadHelper.forMediaItem(item, parameters, DefaultRenderersFactory(context),
            cacheFactory(context, id).setUpstreamDataSourceFactory(upstream.factory))
        try {
            val request = withTimeout(30_000) {
                suspendCancellableCoroutine<DownloadRequest> { continuation ->
                    helper.prepare(object : DownloadHelper.Callback {
                        override fun onPrepared(helper: DownloadHelper, tracksInfoAvailable: Boolean) {
                            if (continuation.isActive) continuation.resume(helper.getDownloadRequest(id, metadata.encode()))
                        }
                        override fun onPrepareError(helper: DownloadHelper, error: IOException) {
                            if (continuation.isActive) continuation.resumeWithException(error)
                        }
                    })
                }
            }
            // Profile switch while resolving must never queue this under another household member.
            check(ownerToken() == ownerAtStart) { "Profile changed. Start the download again in the selected profile." }
            DownloadService.sendAddDownload(context, TvDownloadService::class.java, request, true)
            DownloadService.sendResumeDownloads(context, TvDownloadService::class.java, true)
            id
        } finally { helper.release(); upstream.close() }
    }

    suspend fun list(context: Context): List<Download> {
        withContext(Dispatchers.IO) { cache(context) }
        val index = withContext(Dispatchers.Main) { manager(context).downloadIndex }
        val owner = ownerToken()
        val result = withContext(Dispatchers.IO) {
            index.getDownloads().use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        val download = cursor.download
                        if (runCatching { DownloadMetadata.decode(download.request.data).owner == owner.first }.getOrDefault(false)) add(download)
                    }
                }
            }
        }
        return if (ownerToken() == owner) result else emptyList()
    }

    fun title(download: Download): String = runCatching {
        DownloadMetadata.decode(download.request.data).title
    }.getOrDefault("Download")

    private suspend fun ownedDownload(context: Context, id: String): Download {
        val owner = ownerToken()
        val index = withContext(Dispatchers.Main) { manager(context).downloadIndex }
        val download = withContext(Dispatchers.IO) { index.getDownload(id) }
        check(download != null && DownloadMetadata.decode(download.request.data).owner == owner.first && ownerToken() == owner) {
            "This download is not available in the selected profile."
        }
        return download
    }

    suspend fun pause(context: Context, id: String, pause: Boolean) = withContext(Dispatchers.Main) {
        ownedDownload(context, id)
        DownloadService.sendSetStopReason(context, TvDownloadService::class.java, id, if (pause) 1 else 0, true)
        if (!pause) DownloadService.sendResumeDownloads(context, TvDownloadService::class.java, true)
    }

    suspend fun retry(context: Context, id: String) = withContext(Dispatchers.Main) {
        val download = ownedDownload(context, id)
        DownloadService.sendAddDownload(context, TvDownloadService::class.java, download.request, true)
        DownloadService.sendResumeDownloads(context, TvDownloadService::class.java, true)
    }

    suspend fun remove(context: Context, id: String) = withContext(Dispatchers.Main) {
        ownedDownload(context, id)
        DownloadService.sendRemoveDownload(context, TvDownloadService::class.java, id, false)
    }

    private class DownloadUpstream(context: Context, private val metadata: DownloadMetadata) {
        private val client: OkHttpClient = StreamHttp(context).proxyClient.newBuilder()
            .addInterceptor { chain ->
                val response = chain.proceed(chain.request())
                val type = response.header("Content-Type").orEmpty()
                val prefix = response.peekBody(512).string().trimStart().lowercase()
                if (type.contains("text/html", true) || prefix.startsWith("<!doctype html") || prefix.startsWith("<html")) {
                    response.close()
                    throw IOException("The source returned a web page instead of a video.")
                }
                response
            }.build()
        private val proxy = LocalHlsProxy(client)
        private val adaptive = metadata.mime == MimeTypes.APPLICATION_M3U8 || metadata.mime == MimeTypes.APPLICATION_MPD
        private val prefix by lazy {
            proxy.register(metadata.url, metadata.headers,
                metadata.localProxy?.let(::JSONObject) ?: JSONObject(),
                metadata.transform?.let(::JSONObject) ?: JSONObject()).substringBeforeLast('/') + "/"
        }
        val factory: DataSource.Factory = ResolvingDataSource.Factory(
            OkHttpDataSource.Factory(client).setDefaultRequestProperties(if (adaptive) emptyMap() else metadata.headers),
            object : ResolvingDataSource.Resolver {
                override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
                    if (!adaptive) return dataSpec
                    val original = LocalHlsProxy.canonicalUpstreamUrl(dataSpec.uri.toString()) ?: dataSpec.uri.toString()
                    val encoded = Base64.encodeToString(original.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
                    return dataSpec.withUri(Uri.parse(prefix + encoded))
                }
            },
        )
        fun close() { proxy.stop() }
    }
}
