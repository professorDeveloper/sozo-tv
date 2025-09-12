package com.saikou.sozo_tv.di

import android.content.Context
import com.apollographql.apollo3.ApolloClient
import com.apollographql.apollo3.network.okHttpClient
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.saikou.sozo_tv.data.remote.JikanApiService
import com.saikou.sozo_tv.domain.preference.EncryptedPreferencesManager
import com.saikou.sozo_tv.domain.preference.UserPreferenceManager
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit


const val JIKAN_BASE_URL = "https://api.jikan.moe/"
const val BASE_URL = "https://graphql.anilist.co/"

val NetworkModule = module {
    single { EncryptedPreferencesManager(androidContext()) }
    single { createOkHttpClient(get(), androidContext()) }
    single { createRetrofit(get(), JIKAN_BASE_URL) }
    single { createService(get()) }
    single {
        ApolloClient.Builder()
            .serverUrl(BASE_URL)
            .okHttpClient(get())
            .build()
    }
    single { UserPreferenceManager(androidContext()) }
}

fun createOkHttpClient(pref: EncryptedPreferencesManager, context: Context): OkHttpClient {
    val httpLoggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    return OkHttpClient.Builder()
        .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(httpLoggingInterceptor)
        .retryOnConnectionFailure(true)
        .build()
}


fun createRetrofit(okHttpClient: OkHttpClient, url: String): Retrofit {
    val gson: Gson = GsonBuilder().create()

    return Retrofit.Builder()
        .baseUrl(url)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create(gson)).build()
}

fun createService(retrofit: Retrofit): JikanApiService {
    return retrofit.create(JikanApiService::class.java)

}
