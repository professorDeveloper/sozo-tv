package com.saikou.sozo_tv.utils

import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.app.MyApp
import com.saikou.sozo_tv.data.model.SectionItem
import com.saikou.sozo_tv.domain.model.BannerItem
import com.saikou.sozo_tv.domain.model.Cast
import com.saikou.sozo_tv.domain.model.CategoryChannel
import com.saikou.sozo_tv.domain.model.CategoryChannelItem
import com.saikou.sozo_tv.domain.model.CategoryDetails
import com.saikou.sozo_tv.domain.model.ChannelResponseItem
import com.saikou.sozo_tv.domain.model.MainModel
import com.saikou.sozo_tv.domain.model.MySpinnerItem
import com.saikou.sozo_tv.presentation.screens.home.HomeAdapter


object LocalData {
    var trailer: String = ""
    var isHistoryItemClicked = false
    val channels = CategoryChannel(
        "Live Channels",
        arrayListOf(
            CategoryChannelItem(
                viewType = HomeAdapter.VIEW_CHANNEL_ITEM,
                content = ChannelResponseItem(
                    "Otaku Sign Tv Live",
                    "https://yt3.googleusercontent.com/Z71AZewPbU5qb2KRUtYu6XhlU-_-xxQe-imPVhxIdt89kr4DB4ePATn-9-6HZyTgnJyuUwv9Dg=s160-c-k-c0x00ffffff-no-rj",
                    "https://stmv1.srvif.com/anime/anime/playlist.m3u8",
                    "Brazil"
                )
            ),

            CategoryChannelItem(
                viewType = HomeAdapter.VIEW_CHANNEL_ITEM,
                content = ChannelResponseItem(
                    "Anime Tv",
                    "https://play-lh.googleusercontent.com/msLZnBFRuawR1g7CU-7Zs3jSlehS55R6Y_VFGgU4skS9eB9rkLu6U8m8UulhPWt0k4E",
                    "https://stmv1.srvif.com/loadingtv/loadingtv/playlist.m3u8", "Brazil"
                )
            ),

            CategoryChannelItem(
                viewType = HomeAdapter.VIEW_CHANNEL_ITEM,
                content = ChannelResponseItem(
                    "Smurf Tv",
                    "https://img.static-ottera.com/prod/tg/linear_channel/thumbnails/widescreen/I_D7jFgDr_J5j_1z020IVHed1PiIfFc1z0i99nKtd5g.jpg",
                    "https://d144py1prrd7ns.cloudfront.net/v1/manifest/3722c60a815c199d9c0ef36c5b73da68a62b09d1/cc-affg2ev32s0dq/e221d12d-3861-4ceb-accc-ebcfffa5f84b/1.m3u8",
                    "United State"
                )
            ),
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
}