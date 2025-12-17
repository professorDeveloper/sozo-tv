package com.saikou.sozo_tv.di

import androidx.room.Room
import com.google.firebase.database.FirebaseDatabase
import com.saikou.sozo_tv.data.local.database.AppDatabase
import com.saikou.sozo_tv.data.local.pref.PreferenceManager
import com.saikou.sozo_tv.data.repository.CategoriesRepositoryImpl
import com.saikou.sozo_tv.data.repository.CharacterBookmarkRepositoryImpl
import com.saikou.sozo_tv.data.repository.DetailRepositoryImpl
import com.saikou.sozo_tv.data.repository.EpisodeRepositoryImpl
import com.saikou.sozo_tv.data.repository.HomeRepositoryImpl
import com.saikou.sozo_tv.data.repository.ImdbHomeRepositoryImpl
import com.saikou.sozo_tv.data.repository.MovieBookmarkRepositoryImpl
import com.saikou.sozo_tv.data.repository.SearchRepositoryImpl
import com.saikou.sozo_tv.data.repository.SharedPrefsSettingsRepository
import com.saikou.sozo_tv.data.repository.WatchHistoryRepositoryImpl
import com.saikou.sozo_tv.domain.preference.UserPreferenceManager
import com.saikou.sozo_tv.domain.repository.CategoriesRepository
import com.saikou.sozo_tv.domain.repository.CharacterBookmarkRepository
import com.saikou.sozo_tv.domain.repository.DetailRepository
import com.saikou.sozo_tv.domain.repository.EpisodeRepository
import com.saikou.sozo_tv.domain.repository.HomeRepository
import com.saikou.sozo_tv.domain.repository.TMDBHomeRepository
import com.saikou.sozo_tv.domain.repository.MovieBookmarkRepository
import com.saikou.sozo_tv.domain.repository.SearchRepository
import com.saikou.sozo_tv.domain.repository.SettingsRepository
import com.saikou.sozo_tv.domain.repository.WatchHistoryRepository
import com.saikou.sozo_tv.presentation.activities.UpdateViewModel
import com.saikou.sozo_tv.presentation.viewmodel.AdultPlayerViewModel
import com.saikou.sozo_tv.presentation.viewmodel.BookmarkViewModel
import com.saikou.sozo_tv.presentation.viewmodel.CastDetailViewModel
import com.saikou.sozo_tv.presentation.viewmodel.CategoriesViewModel
import com.saikou.sozo_tv.presentation.viewmodel.DetailViewModel
import com.saikou.sozo_tv.presentation.viewmodel.EpisodeViewModel
import com.saikou.sozo_tv.presentation.viewmodel.HomeViewModel
import com.saikou.sozo_tv.presentation.viewmodel.LiveTvViewModel
import com.saikou.sozo_tv.presentation.viewmodel.NewsViewModel
import com.saikou.sozo_tv.presentation.viewmodel.PlayViewModel
import com.saikou.sozo_tv.presentation.viewmodel.SearchViewModel
import com.saikou.sozo_tv.presentation.viewmodel.SettingsViewModel
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
    single { get<AppDatabase>().tvDao() }
    single { get<AppDatabase>().watchHistoryDao() }
    single { get<AppDatabase>().characterDao() }
    factory { UserPreferenceManager(androidContext()) }
    single<HomeRepository> {
        HomeRepositoryImpl(jikanApiService = get(), apolloClient = get())
    }
    single<EpisodeRepository> {
        EpisodeRepositoryImpl(api = get())
    }
    single<SettingsRepository> {
        SharedPrefsSettingsRepository(PreferenceManager())
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
    viewModel { HomeViewModel(repo = get(), imdbRepo = get()) }
    viewModel { TvGardenViewModel(get()) }
    viewModel { EpisodeViewModel(watchHistoryRepository = get(), repo = get()) }
    viewModel { WrongTitleViewModel(get()) }
    viewModel { UpdateViewModel() }
    viewModel { SettingsViewModel(get()) }
    viewModel { LiveTvViewModel(dao = get()) }
    viewModel { AdultPlayerViewModel() }
    viewModel { SplashViewModel(firebaseService = get()) }
    viewModel { PlayViewModel( watchHistoryRepository = get()) }
    viewModel { DetailViewModel(repo = get(), bookmarkRepo = get()) }
    viewModel { CategoriesViewModel(repo = get()) }
    viewModel { SearchViewModel(repo = get()) }
    viewModel { CastDetailViewModel(repo = get(), bookmarkRepo = get()) }
    viewModel {
        BookmarkViewModel(
            bookmarkRepository = get(),
            characterRepo = get(),
            channelDao = get()
        )
    }
    viewModel { NewsViewModel(get()) }
}

val firebaseModule = module {
    single<FirebaseDatabase> {
        ; FirebaseDatabase.getInstance("https://sozo-app-a36e6-default-rtdb.asia-southeast1.firebasedatabase.app/")
    }
    single { FirebaseService(get()) }
}