package com.saikou.sozo_tv.utils

import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.app.MyApp
import com.saikou.sozo_tv.data.model.SectionItem
import com.saikou.sozo_tv.domain.model.BannerItem
import com.saikou.sozo_tv.domain.model.Cast
import com.saikou.sozo_tv.domain.model.CategoryDetails
import com.saikou.sozo_tv.domain.model.MainModel
import com.saikou.sozo_tv.domain.model.MySpinnerItem

//
//import com.ipsat.ipsat_tv.R
//import com.ipsat.ipsat_tv.app.MyApp
//import com.ipsat.ipsat_tv.data.local.entity.WatchHistoryEntity
//import com.ipsat.ipsat_tv.data.model.CategoryDetails
//import com.ipsat.ipsat_tv.data.model.CategoryTabItem
//import com.ipsat.ipsat_tv.data.model.EventModelItem
//import com.ipsat.ipsat_tv.data.model.SectionItem
//import com.ipsat.ipsat_tv.data.remote.Backdrop
//import com.ipsat.ipsat_tv.data.remote.CastItem
//import com.ipsat.ipsat_tv.domain.MySpinnerItem
//import com.ipsat.ipsat_tv.domain.model.BannerItem
//import com.ipsat.ipsat_tv.domain.model.ChannelCategoryItem
//import com.ipsat.ipsat_tv.domain.model.Item0
//import com.ipsat.ipsat_tv.domain.model.Movie
//import com.ipsat.ipsat_tv.domain.model.SeriesDetailResponse
//import com.ipsat.ipsat_tv.domain.model.category.CategoryItem
//import com.ipsat.ipsat_tv.domain.model.category.ChannelItem
//
object LocalData {
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
}//

//    var seriesId: Int = -1
//    var currentEpp: Item0? = null
//    var isHistoryItemClicked = false
//    var isSeries = false
//    var epList = ArrayList<Item0>()
//    var categoryList = ArrayList<ChannelCategoryItem>()
//    val eventList = ArrayList<EventModelItem>()
//    var backdropEpList = ArrayList<Backdrop>()
//    var itemMovieWatch: WatchHistoryEntity? = null
//    var isBookmarkClicked = false
//
//    val recommendedMovies: MutableList<Movie> = mutableListOf()
//

//
//    lateinit var itemChangedForPlayBtng: () -> Unit
//    fun setItemChangedForPlayBtn(listener: () -> Unit) {
//        itemChangedForPlayBtng = listener
//    }
//
//    var isAboutFilmSelected = true
//
//    lateinit var backgroundBlurChangedListenerg: (BannerItem) -> Unit
//    fun setBackgroundBlurChangedListener(listener: (BannerItem) -> Unit) {
//        backgroundBlurChangedListenerg = listener
//    }
//
//    val sectionList = arrayListOf(
//        SectionItem(MyApp.context.getString(R.string.my_info), R.drawable.ic_users),
//        SectionItem(MyApp.context.getString(R.string.my_history), R.drawable.ic_time_history),
//        SectionItem(MyApp.context.getString(R.string.bookmark), R.drawable.ic_bookmark),
//        SectionItem(MyApp.context.getString(R.string.message_page), R.drawable.ic_chat),
//        SectionItem(MyApp.context.getString(R.string.exit), R.drawable.ic_exit),
//    )
//
//    val categories = listOf(
//        CategoryItem("Barchasi", 0),
//        CategoryItem("Yangiliklar", 1),
//        CategoryItem("Sport", 2),
//        CategoryItem("Film", 3),
//        CategoryItem("Musiqa", 4),
//        CategoryItem("Yoshlar uchun", 5),
//        CategoryItem("O'zbek", 6)
//    )
//
//    val channels = listOf(
//        ChannelItem(
//            name = "Milliy TV",
//            time = "12:30",
//            image = "https://files.itv.uz/uploads/iptv/channels/2023/06/02//fa77872772e7f7406369bd0fe4e48e47-q-500x500.jpeg",
//            currentShow = "Kino",
//        ),
//        ChannelItem(
//            name = "Sport TV",
//            time = "00:01",
//            image = "https://files.itv.uz/uploads/iptv/channels/2020/06/17//f79dce2ae2bd9ef84b6888f226fbb293-q-500x500.jpeg",
//            currentShow = "Boks",
//        ),
//        ChannelItem(
//            name = "Sevimli",
//            time = "10:01",
//            image = "https://files.itv.uz/uploads/iptv/channels/2022/11/30//4b9b4faa5635e282bb1f3ece579c5fea-q-500x500.jpeg",
//            currentShow = "Iqror t/s",
//        ),
//
//        ChannelItem(
//            name = "Milliy TV",
//            time = "12:30",
//            image = "https://files.itv.uz/uploads/iptv/channels/2023/06/02//fa77872772e7f7406369bd0fe4e48e47-q-500x500.jpeg",
//            currentShow = "Kino",
//        ),
//
//        ChannelItem(
//            name = "Sport TV",
//            time = "00:01",
//            image = "https://files.itv.uz/uploads/iptv/channels/2020/06/17//f79dce2ae2bd9ef84b6888f226fbb293-q-500x500.jpeg",
//            currentShow = "Boks",
//        ),
//        ChannelItem(
//            name = "Sevimli",
//            time = "10:01",
//            image = "https://files.itv.uz/uploads/iptv/channels/2022/11/30//4b9b4faa5635e282bb1f3ece579c5fea-q-500x500.jpeg",
//            currentShow = "Iqror t/s",
//        ),
//
//        )
//
//    val categoriesTop = arrayListOf(
//        CategoryTabItem(page = 0, "All"),
//        CategoryTabItem(1, "Comedy"),
//        CategoryTabItem(2, "Thriller"),
//        CategoryTabItem(3, "Adventure"),
//        CategoryTabItem(4, "Mystery"),
//        CategoryTabItem(5, "Crime"),
//        CategoryTabItem(6, "Biography"),
//        CategoryTabItem(7, "Family"),
//        CategoryTabItem(8, "Documentary"),
//        CategoryTabItem(9, "History"),
//        CategoryTabItem(10, "War"),
//        CategoryTabItem(11, "Science Fiction"),
//        CategoryTabItem(12, "Fantasy"),
//        CategoryTabItem(13, "Western"),
//        CategoryTabItem(14, "Animation"),
//        CategoryTabItem(15, "Action"),
//        CategoryTabItem(16, "Horror"),
//        CategoryTabItem(17, "Drama"),
//        CategoryTabItem(18, "Romance"),
//        CategoryTabItem(19, "Adventure")
//    )
//    val years = (1970 until 2025).map { MySpinnerItem(it.toString()) }.reversed().toMutableList()
//    val country = arrayListOf(
//        "United States",
//        "United Kingdom",
//        "India",
//        "Germany",
//        "France",
//        "Spain",
//        "Italy",
//        "Japan",
//        "South Korea",
//        "Mexico",
//        "Russia",
//        "Turkey",
//        "Netherlands",
//        "Belgium",
//        "Switzerland",
//    )
//
//    val categoriesTabMovie = categoriesTop
//
//    val countrySpinner = country.map {
//        MySpinnerItem(it)
//    }
//
//}