package com.saikou.sozo_tv.di

import androidx.room.Room
import com.google.firebase.database.FirebaseDatabase
import com.saikou.sozo_tv.data.local.dao.WatchHistoryDao
import com.saikou.sozo_tv.data.local.database.AppDatabase
import com.saikou.sozo_tv.data.repository.CategoriesRepositoryImpl
import com.saikou.sozo_tv.data.repository.CharacterBookmarkRepositoryImpl
import com.saikou.sozo_tv.data.repository.DetailRepositoryImpl
import com.saikou.sozo_tv.data.repository.HomeRepositoryImpl
import com.saikou.sozo_tv.data.repository.ImdbHomeRepositoryImpl
import com.saikou.sozo_tv.data.repository.MovieBookmarkRepositoryImpl
import com.saikou.sozo_tv.data.repository.SearchRepositoryImpl
import com.saikou.sozo_tv.data.repository.WatchHistoryRepositoryImpl
import com.saikou.sozo_tv.domain.preference.UserPreferenceManager
import com.saikou.sozo_tv.domain.repository.CategoriesRepository
import com.saikou.sozo_tv.domain.repository.CharacterBookmarkRepository
import com.saikou.sozo_tv.domain.repository.DetailRepository
import com.saikou.sozo_tv.domain.repository.HomeRepository
import com.saikou.sozo_tv.domain.repository.TMDBHomeRepository
import com.saikou.sozo_tv.domain.repository.MovieBookmarkRepository
import com.saikou.sozo_tv.domain.repository.SearchRepository
import com.saikou.sozo_tv.domain.repository.WatchHistoryRepository
import com.saikou.sozo_tv.presentation.activities.UpdateViewModel
import com.saikou.sozo_tv.presentation.viewmodel.AdultPlayerViewModel
import com.saikou.sozo_tv.presentation.viewmodel.BookmarkViewModel
import com.saikou.sozo_tv.presentation.viewmodel.CastDetailViewModel
import com.saikou.sozo_tv.presentation.viewmodel.CategoriesViewModel
import com.saikou.sozo_tv.presentation.viewmodel.EpisodeViewModel
import com.saikou.sozo_tv.presentation.viewmodel.HomeViewModel
import com.saikou.sozo_tv.presentation.viewmodel.NewsViewModel
import com.saikou.sozo_tv.presentation.viewmodel.PlayViewModel
import com.saikou.sozo_tv.presentation.viewmodel.SearchViewModel
import com.saikou.sozo_tv.presentation.viewmodel.SplashViewModel
import com.saikou.sozo_tv.presentation.viewmodel.TvGardenViewModel
import com.saikou.sozo_tv.presentation.viewmodel.WrongTitleViewModel
import com.saikou.sozo_tv.services.FirebaseService
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val koinModule = module {
    single {
        Room.databaseBuilder(
            androidApplication(), AppDatabase::class.java, "movie_database"
        ).build()
    }
    single { get<AppDatabase>().movieDao() }
    single { get<AppDatabase>().watchHistoryDao() }
    single { get<AppDatabase>().characterDao() }
    factory { UserPreferenceManager(androidContext()) }
    single<HomeRepository> {
        HomeRepositoryImpl(jikanApiService = get(), apolloClient = get())
    }
    single<TMDBHomeRepository> {
        ImdbHomeRepositoryImpl(api = get())
    }
    single<MovieBookmarkRepository> {
        MovieBookmarkRepositoryImpl(dao = get())
    }

    single<WatchHistoryRepository> {
        WatchHistoryRepositoryImpl(watchHistoryDao = get())
    }

    single<CharacterBookmarkRepository> {
        CharacterBookmarkRepositoryImpl(dao = get())
    }
    single<SearchRepository> {
        SearchRepositoryImpl(apolloClient = get(), api = get())
    }
    single<CategoriesRepository> {
        CategoriesRepositoryImpl(apolloClient = get(), api = get())
    }
    single<DetailRepository> {
        DetailRepositoryImpl(client = get(), api = get())
    }
//    single<ProfileRepository> {
//        ProfileRepositoryImpl(service = get(), pref = get())
//    }klklkkkkjkjgjbhg  bgbnhjbgjbjhnhubetew
//
//    single<LiveTvRepository> {
//        LiveTvRepositoryImpl(api = get(), pref = get())
//    }
//    single<WatchHistoryRepository> {
//        WatchHistoryRepositoryImpl(watchHistoryDao = get())
//    }
//
//
    viewModel { HomeViewModel(repo = get(), imdbRepo = get()) }
    viewModel { TvGardenViewModel() }
    viewModel { EpisodeViewModel(watchHistoryRepository = get()) }
    viewModel { WrongTitleViewModel() }
    viewModel { UpdateViewModel() }
    viewModel { AdultPlayerViewModel() }
    viewModel { SplashViewModel(firebaseService = get()) }
    viewModel { PlayViewModel(repo = get(), bookmarkRepo = get(), watchHistoryRepository = get()) }
    viewModel { CategoriesViewModel(repo = get()) }
    viewModel { SearchViewModel(repo = get()) }
    viewModel { CastDetailViewModel(repo = get(), bookmarkRepo = get()) }
    viewModel { BookmarkViewModel(bookmarkRepository = get(), characterRepo = get()) }
    viewModel { NewsViewModel(get()) }

//    viewModel {
//        PlayViewModel(
//            homeRepository = get(),
//            watchHistoryRepository = get(),
//            movieRepository = get(),
//            liveTvRepository = get()
//        )
//    }
//    viewModel { SplashViewModel(repo = get(), pref = get(), firebaseService = get()) }
//    viewModel { UpdateViewModel() }
//    viewModel { ProfileViewModel(repo = get()) }
//    viewModel { CategoryViewModel(repo = get()) }
//    viewModel { HomeViewModel(repo = get(), liveTvUseCase = get()) }
//    viewModel { BookmarkViewModel(bookmarkRepository = get(), homeRepo = get()) }

}

val firebaseModule = module {
    single<FirebaseDatabase> {;        FirebaseDatabase.getInstance("https://sozo-app-a36e6-default-rtdb.asia-southeast1.firebasedatabase.app/")
    }
    single { FirebaseService(get()) }
}