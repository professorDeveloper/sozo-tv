package com.saikou.sozo_tv.utils

import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.app.MyApp
import com.saikou.sozo_tv.data.local.pref.PreferenceManager
import com.saikou.sozo_tv.data.model.Channel
import com.saikou.sozo_tv.data.model.SectionItem
import com.saikou.sozo_tv.domain.model.BannerItem
import com.saikou.sozo_tv.domain.model.Cast
import com.saikou.sozo_tv.domain.model.CategoryDetails
import com.saikou.sozo_tv.domain.model.ChannelResponseItem
import com.saikou.sozo_tv.domain.model.GenreTmdbModel
import com.saikou.sozo_tv.domain.model.MainModel
import com.saikou.sozo_tv.domain.model.MySpinnerItem


object LocalData {
    var trailer: String = ""
    var isHistoryItemClicked = false
    const val IMDB_IMAGE_PATH = "https://image.tmdb.org/t/p/w500/"
    const val IMDB_BACKDROP_PATH = "https://image.tmdb.org/t/p/w1280/"
    val genreTmdb = ArrayList<GenreTmdbModel>()
    val customTv = arrayListOf(
        "Telemundo",
        "NASA",
        "English News",
    )
    val preferenceManager = PreferenceManager()
    val isAnimeEnabled = preferenceManager.isModeAnimeEnabled()
    val channelsd = arrayListOf(
        Channel(
            "0",
            "Hd Romen",
            arrayListOf("https://live-hls-viasat-secure-flus.cdnvideo.ru/viasat/Romantika_HD.smil/tracks-v1a1/mono.ts.m3u8?filter.tracks=v1v2v3a1&md5=rdVBBlP29FodZn8yAdHzGg&e=1759654086&hls_proxy_host=e2c000defa6aa845b219ba5ca0db8ad5"),
            arrayListOf(),
            "cdn",
            "cdn",
            true
        ),
        Channel(
            "0",
            "lubimoe-kino",
            arrayListOf("http://api.peers.tv/timeshift/favmovie/16/playlist.m3u8?token=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJhdWQiOiJhY2Nlc3MiLCJleHAiOjE3NTkyMDU3ODAsImlhdCI6MTc1OTExOTM4MCwiY2lkIjo2LCJ1aWQiOjk0ODcxNzUzOCwicmVnIjpmYWxzZSwiaXAiOjE1NTE3MDQzNTYsImFkdWx0IjpmYWxzZX0.aXpZsmnvRME-UQwL1Sf_KYgJNTk8_DfR3tU--mZb9JI&offset=3"),
            arrayListOf(),
            "lubimoe-kino",
            "lubimoe-kino",
            true
        )
    )


    val channelsByCategory: Map<String, List<Channel>> = mapOf(
        "NASA" to listOf(
            Channel(
                "1",
                "NASA TV ISS Views (480p) [Not 24/7]",
                listOf("http://iphone-streaming.ustream.tv/uhls/9408562/streams/live/iphone/playlist.m3u8"),
                emptyList(),
                "cdn",
                "nasa",
                true
            ),
            Channel(
                "2",
                "NASA TV Media (720p)",
                listOf("https://ntv2.akamaized.net/hls/live/2013923/NASA-NTV2-HLS/master.m3u8"),
                emptyList(),
                "cdn",
                "nasa",
                true
            ),
            Channel(
                "3",
                "NASA TV Public (720p)",
                listOf("https://ntv1.akamaized.net/hls/live/2014075/NASA-NTV1-HLS/master.m3u8"),
                emptyList(),
                "cdn",
                "nasa",
                true
            ),
            Channel(
                "4",
                "NASA TV UHD (2160p)",
                listOf("https://endpnt.com/hls/nasa4k60/playlist.m3u8"),
                emptyList(),
                "cdn",
                "nasa",
                true
            )
        ),
        "Telemundo" to listOf(
            Channel(
                "5",
                "Telemundo 1080",
                listOf("https://nbculocallive.akamaized.net/hls/live/2037499/puertorico/stream1/master.m3u8"),
                emptyList(),
                "cdn",
                "telemundo",
                true
            ),
            Channel(
                "6",
                "Telemundo old",
                listOf("https://service-stitcher.clusters.pluto.tv/stitch/hls/channel/5cf96cc422df39f1a338d165/master.m3u8?advertisingId=&appName=web&appStoreUrl=&appVersion=DNT&app_name=&architecture=&buildVersion=&deviceDNT=0&deviceId=5cf96cc422df39f1a338d165&deviceLat=&deviceLon=&deviceMake=web&deviceModel=web&deviceType=web&deviceVersion=DNT&includeExtendedEvents=false&marketingRegion=US&serverSideAds=false&sid=940&terminate=false&userId="),
                emptyList(),
                "cdn",
                "telemundo",
                true
            )
        ),
        "English News" to listOf(
            Channel(
                "7",
                "America's Voice (720p)",
                listOf("https://content.uplynk.com/channel/26bd482ffe364a1282bc3df28bd3c21f.m3u8"),
                emptyList(),
                "cdn",
                "news",
                true
            ),
            Channel(
                "8",
                "Black News Channel (720p)",
                listOf("https://blacknewschannel-vizio.amagi.tv/playlist.m3u8"),
                emptyList(),
                "cdn",
                "news",
                true
            ),
            Channel(
                "9",
                "CBC News (720p)",
                listOf("https://dai2.xumo.com/amagi_hls_data_xumo1212A-redboxcbcnews/CDN/playlist.m3u8"),
                emptyList(),
                "cdn",
                "news",
                true
            ),
            Channel(
                "10",
                "CBN News National (1080p)",
                listOf("https://bcovlive-a.akamaihd.net/re8d9f611ee4a490a9bb59e52db91414d/us-east-1/734546207001/playlist.m3u8"),
                emptyList(),
                "cdn",
                "news",
                true
            ),
            Channel(
                "11",
                "CBS 3 Omaha NE (KMTV-TV) (720p)",
                listOf("https://content.uplynk.com/4a09fbea28ef4f32bce095e9eae04bd8.m3u8"),
                emptyList(),
                "cdn",
                "news",
                true
            ),
            Channel(
                "12",
                "FOX 9 ST Paul Minneapolis MN (KMSP) (720p)",
                listOf("https://lnc-kmsp.tubi.video/index.m3u8"),
                emptyList(),
                "cdn",
                "news",
                true
            )
        )
    )

    var bookmark: Boolean = false
    var isBookmarkClicked: Boolean = false
    var characterBookmark: Boolean = false
    const val FILE_NAME_GENRES: String = "genres"
    var currentCategory = ""
    val anime404 =
        "https://c4.wallpaperflare.com/wallpaper/976/117/318/anime-girls-404-not-found-glowing-eyes-girls-frontline-wallpaper-preview.jpg"
    val genres = arrayListOf(
        "Action",
        "Adventure",
        "Comedy",
        "Drama",
        "Ecchi",
        "Fantasy",
//        "Hentai",
        "Horror",
        "Mahou Shoujo",
        "Mecha",
        "Music",
        "Mystery",
        "Psychological",
        "Romance",
        "Sci-Fi",
        "Slice of Life",
        "Sports",
        "Supernatural",
        "Thriller"
    )

    val mediaSortList = arrayListOf(
        "START_DATE",
        "START_DATE_DESC",
        "STATUS",
        "STATUS_DESC",
        "TITLE_ENGLISH",
        "TITLE_ENGLISH_DESC",
        "TITLE_NATIVE",
        "TITLE_NATIVE_DESC",
        "TITLE_ROMAJI",
        "TITLE_ROMAJI_DESC",
        "TRENDING",
        "TRENDING_DESC",
        "TYPE",
        "TYPE_DESC",
        "UPDATED_AT",
        "UPDATED_AT_DESC",
        "VOLUMES",
        "VOLUMES_DESC"
    )
    val sortSpinner = mediaSortList.map {
        MySpinnerItem(it)
    }

    val years = (1970 until 2025).map { MySpinnerItem(it.toString()) }.reversed().toMutableList()
    lateinit var sFocusedGenreClickListener: (String) -> Unit

    fun setFocusedGenreClickListener(listener: (String) -> Unit) {
        sFocusedGenreClickListener = listener

    }


    val sectionList = arrayListOf(
        SectionItem(MyApp.context.getString(R.string.my_info), R.drawable.ic_users),
        SectionItem(MyApp.context.getString(R.string.sources), R.drawable.ic_round_star_24),
        SectionItem(MyApp.context.getString(R.string.my_history), R.drawable.ic_time_history),
        SectionItem(MyApp.context.getString(R.string.bookmark), R.drawable.ic_bookmark),
        SectionItem(MyApp.context.getString(R.string.message_page), R.drawable.ic_chat),
    )
    lateinit var listenerItemCategory: (isAbout: CategoryDetails) -> Unit
    fun setonClickedListenerItemCategory(listener: (isAbout: CategoryDetails) -> Unit) {
        listenerItemCategory = listener
    }

    lateinit var listenerItemBanner: (isAbout: BannerItem) -> Unit
    fun setonClickedlistenerItemBanner(listener: (isAbout: BannerItem) -> Unit) {
        listenerItemBanner = listener
    }

    val recommendedMovies: MutableList<MainModel> = mutableListOf()
    val recommendedMoviesCast: MutableList<MainModel> = mutableListOf()
    val castList: MutableList<Cast> = mutableListOf()
    lateinit var focusChangedListenerPlayerg: (MainModel) -> Unit

    fun setFocusChangedListenerPlayer(listener: (MainModel) -> Unit) {
        focusChangedListenerPlayerg = listener
    }

    lateinit var channnelItemClickListener: (ChannelResponseItem) -> Unit
    fun setChannelItemClickListener(listener: (ChannelResponseItem) -> Unit) {
        channnelItemClickListener = listener
    }
}