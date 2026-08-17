package com.saikou.sozo_tv.di

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.saikou.sozo_tv.BuildConfig
import com.saikou.sozo_tv.data.local.pref.DeviceSessionStore
import com.saikou.sozo_tv.data.extensions.ExtensionEngine
import com.saikou.sozo_tv.data.remote.device.DeviceAuthClient
import com.saikou.sozo_tv.data.remote.history.WatchHistorySyncClient
import com.saikou.sozo_tv.data.remote.lists.UserListsClient
import com.saikou.sozo_tv.data.repository.DeviceAuthRepository
import com.saikou.sozo_tv.data.repository.UserListsRepository
import com.saikou.sozo_tv.data.repository.WatchHistorySyncRepository
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.util.concurrent.TimeUnit

val NetworkModule = module {

    // Aniyomi/CloudStream extension engine — the app's content backend.
    single { ExtensionEngine.shared }

    single<Gson> { GsonBuilder().create() }

    // Device sign-in. Deliberately on its OWN client — see createAuthOkHttpClient().
    single { DeviceSessionStore(androidContext()) }
    single(named("authOkHttp")) { createAuthOkHttpClient() }
    single {
        DeviceAuthClient(
            okHttpClient = get(named("authOkHttp")),
            gson = get(),
            baseUrl = BuildConfig.SOZO_API_BASE_URL,
        )
    }
    single { DeviceAuthRepository(client = get(), store = get()) }

    // Curated lists (Watch Later / Watched) — the first authenticated API the TV
    // calls. Shares the auth client (platform TLS, no body logging) because it
    // sends a bearer token; the token is read per-request through
    // DeviceAuthRepository so refreshes are picked up mid-session.
    single {
        UserListsClient(
            okHttpClient = get(named("authOkHttp")),
            gson = get(),
            baseUrl = BuildConfig.SOZO_API_BASE_URL,
            tokenProvider = { get<DeviceAuthRepository>().accessToken() },
        )
    }
    single { UserListsRepository(context = androidContext(), client = get()) }

    // Watch history sync. Same auth transport and same per-request token as the
    // lists above; the difference is that history is written by the player
    // rather than by the user, so it pushes on its own schedule.
    single {
        WatchHistorySyncClient(
            okHttpClient = get(named("authOkHttp")),
            gson = get(),
            baseUrl = BuildConfig.SOZO_API_BASE_URL,
            tokenProvider = { get<DeviceAuthRepository>().accessToken() },
        )
    }
    single {
        WatchHistorySyncRepository(
            context = androidContext(),
            client = get(),
            local = get(),
        )
    }

}

/**
 * Auth transport. Platform TLS on purpose: no trust manager override, no hostname verifier
 * override, and no body logging — /auth/device/token returns the access AND refresh tokens in
 * its body, so this must never share the app's trust-all, log-everything content client.
 *
 * Read timeout sits above the server's own 15s request timeout so a 504 arrives as a real
 * response (retryable) rather than as a client-side abort.
 */
fun createAuthOkHttpClient(): OkHttpClient {
    val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
        else HttpLoggingInterceptor.Level.NONE
    }

    return OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .addInterceptor(loggingInterceptor)
        .retryOnConnectionFailure(true)
        .build()
}

