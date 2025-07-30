package com.saikou.sozo_tv.di

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.saikou.sozo_tv.data.remote.JikanApiService
import com.saikou.sozo_tv.domain.preference.EncryptedPreferencesManager
import com.saikou.sozo_tv.domain.preference.UserPreferenceManager
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit


const val JIKAN_BASE_URL = "https://api.jikan.moe/"
val NetworkModule = module {
    // Preferences
    single { UserPreferenceManager(androidContext()) }
    single { EncryptedPreferencesManager(androidContext()) }

    // OkHttpClient
    single(named("default_okhttp")) { createOkHttpClient(get(), androidContext()) }

    // Retrofit instances
    single(named("jikan_retrofit")) { createRetrofit(get(named("default_okhttp")), JIKAN_BASE_URL) }
//    single(named("anilist_retrofit")) { createRetrofit(get(named("default_okhttp")), ANILIST_BASE_URL) }
//    single(named("kitsu_retrofit")) { createRetrofit(get(named("default_okhttp")), KITSU_BASE_URL) }

    // API Services
    single<JikanApiService>(named("jikan_api")) {
        get<Retrofit>(named("jikan_retrofit")).create(
            JikanApiService::class.java
        )
    }
//    single<AnilistApiService>(named("anilist_api")) { get<Retrofit>(named("anilist_retrofit")).create(AnilistApiService::class.java) }
//    single<KitsuApiService>(named("kitsu_api")) { get<Retrofit>(named("kitsu_retrofit")).create(KitsuApiService::class.java) }
}

fun createOkHttpClient(pref: EncryptedPreferencesManager, context: Context): OkHttpClient {

    val httpLoggingInterceptor = HttpLoggingInterceptor()
    httpLoggingInterceptor.level = HttpLoggingInterceptor.Level.BODY
    return OkHttpClient.Builder()
        .connectionPool(ConnectionPool(1, 1, TimeUnit.NANOSECONDS)) // Keep-Alive ni o‘chiradi
        .connectTimeout(60, TimeUnit.SECONDS) // Default: 10s
        .readTimeout(60, TimeUnit.SECONDS)    // Default: 10s
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(httpLoggingInterceptor)
        .build()
}

fun createRetrofit(okHttpClient: OkHttpClient, url: String): Retrofit {
    val gson: Gson = GsonBuilder().create()

    return Retrofit.Builder()
        .baseUrl(url)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create(gson)).build()
}



