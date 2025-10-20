package com.saikou.sozo_tv.di

import android.annotation.SuppressLint
import android.util.Log
import com.apollographql.apollo3.ApolloClient
import com.apollographql.apollo3.network.okHttpClient
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.saikou.sozo_tv.data.remote.ImdbService
import com.saikou.sozo_tv.data.remote.JikanApiService
import com.saikou.sozo_tv.domain.preference.EncryptedPreferencesManager
import com.saikou.sozo_tv.domain.preference.UserPreferenceManager
import com.saikou.sozo_tv.utils.Security
import okhttp3.ConnectionPool
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

const val JIKAN_BASE_URL = "https://api.jikan.moe/"
const val BASE_URL = "https://graphql.anilist.co/"
const val TMDB_BASE_URL = "https://api.themoviedb.org/3/"

val NetworkModule = module {
    single { EncryptedPreferencesManager(androidContext()) }
    single { createOkHttpClient() }
    single { createRetrofit(get(), JIKAN_BASE_URL) }
    single { createService(get()) }


    single(named("tmdbRetrofit")) { createTmdbClient() }

    single<ImdbService> {
        get<Retrofit>(qualifier = named("tmdbRetrofit"))
            .create(ImdbService::class.java)
    }

    single {
        ApolloClient.Builder()
            .serverUrl(BASE_URL)
            .okHttpClient(get())
            .build()
    }
    single { UserPreferenceManager(androidContext()) }
}

fun createOkHttpClient(): OkHttpClient {
    val httpLoggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    val trustAllCerts = arrayOf<TrustManager>(
        @SuppressLint("CustomX509TrustManager")
        object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
    )

    val sslContext = SSLContext.getInstance("SSL")
    sslContext.init(null, trustAllCerts, SecureRandom())
    val sslSocketFactory = sslContext.socketFactory

    return OkHttpClient.Builder()
        .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(httpLoggingInterceptor)
        .retryOnConnectionFailure(true)
        .sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
        .hostnameVerifier { _, _ -> true }
        .build()
}

fun createTmdbClient(): Retrofit {
    val gson = GsonBuilder().create()

    val tmdbInterceptor = Interceptor { chain ->
        val token = Security.getToken()
//        Log.d("GGG", "createTmdbClient:$token ")
        val newRequest = chain.request().newBuilder()
            .addHeader("Authorization", "Bearer $token")
            .addHeader("accept", "application/json")
            .build()
        chain.proceed(newRequest)
    }

    val okHttpClient = createOkHttpClient().newBuilder()
        .addInterceptor(tmdbInterceptor)
        .build()

    return Retrofit.Builder()
        .baseUrl(TMDB_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
}

fun createRetrofit(okHttpClient: OkHttpClient, url: String): Retrofit {
    val gson: Gson = GsonBuilder().create()

    return Retrofit.Builder()
        .baseUrl(url)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
}

fun createService(retrofit: Retrofit): JikanApiService {
    return retrofit.create(JikanApiService::class.java)
}
