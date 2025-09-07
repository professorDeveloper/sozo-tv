package com.saikou.sozo_tv.utils

import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.app.MyApp
import com.saikou.sozo_tv.data.model.SectionItem
import com.saikou.sozo_tv.domain.model.BannerItem
import com.saikou.sozo_tv.domain.model.Cast
import com.saikou.sozo_tv.domain.model.CategoryDetails
import com.saikou.sozo_tv.domain.model.MainModel
import com.saikou.sozo_tv.domain.model.MySpinnerItem


object LocalData {
    var trailer: String = ""
    var bookmark: Boolean = false
    var isBookmarkClicked: Boolean = false
    var characterBookmark: Boolean = false
    const val FILE_NAME_GENRES: String = "genres"
    var isHistoryItemClicked = false
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
        "Hentai",
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
        SectionItem(MyApp.context.getString(R.string.exit), R.drawable.ic_exit),
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