package com.saikou.sozo_tv.di

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.saikou.sozo_tv.BuildConfig
import com.saikou.sozo_tv.data.local.pref.DeviceSessionStore
import com.saikou.sozo_tv.data.extensions.ExtensionEngine
import com.saikou.sozo_tv.data.remote.anilist.AnilistGraphQlClient
import com.saikou.sozo_tv.data.remote.anilist.AnilistLinkClient
import com.saikou.sozo_tv.data.remote.mal.MalApiClient
import com.saikou.sozo_tv.data.remote.mal.MalLinkClient
import com.saikou.sozo_tv.data.repository.MalLinkStore
import com.saikou.sozo_tv.data.repository.MalRepository
import com.saikou.sozo_tv.data.repository.MalTracker
import com.saikou.sozo_tv.data.remote.device.DeviceAuthClient
import com.saikou.sozo_tv.data.remote.device.DeviceTokenAuthenticator
import okhttp3.Authenticator
import com.saikou.sozo_tv.data.remote.subtitles.SubtitleSearchClient
import com.saikou.sozo_tv.data.remote.subtitles.SubtitleTranslationClient
import com.saikou.sozo_tv.data.remote.version.AppVersionClient
import com.saikou.sozo_tv.data.remote.history.WatchHistorySyncClient
import com.saikou.sozo_tv.data.remote.lists.UserListsClient
import com.saikou.sozo_tv.data.repository.AnilistLinkStore
import com.saikou.sozo_tv.data.repository.AnilistRepository
import com.saikou.sozo_tv.data.repository.AnilistSourceRegistry
import com.saikou.sozo_tv.data.repository.AnilistTracker
import com.saikou.sozo_tv.data.repository.DeviceAuthRepository
import com.saikou.sozo_tv.data.remote.remote.RemoteControlClient
import com.saikou.sozo_tv.data.repository.RemoteControlManager
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
    // The refresher is resolved lazily inside the lambda on purpose: DeviceAuthRepository
    // needs DeviceAuthClient, which needs this very client. Resolving at call time instead
    // of at construction breaks that cycle.
    single(named("authOkHttp")) {
        createAuthOkHttpClient(
            authenticator = DeviceTokenAuthenticator {
                get<DeviceAuthRepository>().refreshNow()
            },
        )
    }
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

    // The phone's remote. Same auth client and per-request token as the other
    // authenticated APIs, so a mid-session refresh is picked up on reconnect.
    single {
        RemoteControlClient(
            okHttpClient = get(named("authOkHttp")),
            gson = get(),
            baseUrl = BuildConfig.SOZO_API_BASE_URL,
            tokenProvider = { get<DeviceAuthRepository>().accessToken() },
        )
    }
    single { RemoteControlManager(client = get(), store = get()) }

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

    single {
        AnilistLinkClient(
            okHttpClient = get(named("authOkHttp")),
            gson = get(),
            baseUrl = BuildConfig.SOZO_API_BASE_URL,
            tokenProvider = { get<DeviceAuthRepository>().accessToken() },
        )
    }
    single { AnilistGraphQlClient(okHttpClient = get(named("authOkHttp"))) }
    single { AnilistLinkStore(context = androidContext()) }
    single { AnilistSourceRegistry(context = androidContext()) }
    single { AnilistRepository(linkClient = get(), api = get(), links = get()) }
    single { AnilistTracker(repository = get(), links = get()) }

    // MyAnimeList. No connect flow here: the link is made on the phone and this
    // device inherits it from the account, exactly as AniList does.
    //
    // MalTracker holds the AniList side as a MATCHER — AniList search and media
    // reads need no token, and AniList publishes the MAL id for the same entry,
    // so a title matched once serves both trackers instead of being matched
    // twice, which is also matched wrong twice.
    single {
        MalLinkClient(
            okHttpClient = get(named("authOkHttp")),
            gson = get(),
            baseUrl = BuildConfig.SOZO_API_BASE_URL,
            tokenProvider = { get<DeviceAuthRepository>().accessToken() },
        )
    }
    single { MalApiClient(okHttpClient = get(named("authOkHttp"))) }
    single { MalLinkStore(context = androidContext()) }
    single { MalRepository(linkClient = get(), api = get(), links = get()) }
    single {
        MalTracker(
            repository = get(),
            links = get(),
            anilistLinks = get(),
            anilistTracker = get(),
            anilistApi = get(),
        )
    }

    single {
        AppVersionClient(
            okHttpClient = get(named("authOkHttp")),
            gson = get(),
            baseUrl = BuildConfig.SOZO_API_BASE_URL,
        )
    }

    single {
        SubtitleSearchClient(
            okHttpClient = get(named("authOkHttp")),
            gson = get(),
            baseUrl = BuildConfig.SOZO_API_BASE_URL,
        )
    }
    single {
        SubtitleTranslationClient(
            okHttpClient = get(named("authOkHttp")),
            gson = get(),
            baseUrl = BuildConfig.SOZO_API_BASE_URL,
            tokenProvider = { get<DeviceAuthRepository>().accessToken() },
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
fun createAuthOkHttpClient(authenticator: Authenticator? = null): OkHttpClient {
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
        .apply { if (authenticator != null) authenticator(authenticator) }
        .build()
}

