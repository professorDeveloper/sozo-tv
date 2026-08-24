package com.saikou.sozo_tv.app

import com.saikou.sozo_tv.utils.initSozoUserAgent
import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import com.bugsnag.android.Bugsnag
import com.bugsnag.android.performance.BugsnagPerformance
import com.google.firebase.FirebaseApp
import com.jakewharton.threetenabp.AndroidThreeTen
import com.lagradost.cloudstream3.CloudStreamApp
import com.saikou.sozo_tv.di.NetworkModule
import com.saikou.sozo_tv.di.firebaseModule
import com.saikou.sozo_tv.di.koinModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import com.saikou.sozo_tv.data.repository.AnilistRepository
import com.saikou.sozo_tv.data.repository.MalRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import org.koin.core.context.startKoin
import java.lang.ref.WeakReference

/**
 * Main activity for SozoTv app.
 * @author Azamov
 * @version 2.3
 */

class MyApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        // Before anything makes a request: adopt this device's own WebView
        // User-Agent. A Cloudflare managed challenge compares the header
        // against the engine behind it, so a hard-coded version that does not
        // match this device's WebView is a mismatch it never clears.
        initSozoUserAgent(this)
        Bugsnag.start(this)
        AndroidThreeTen.init(this)
        FirebaseApp.initializeApp(this)
        BugsnagPerformance.start(this)
        CloudStreamApp.attach(this)
        // The library's own WebViewResolver builds its offscreen WebView from
        // `com.lagradost.api.getContext()`, which is null until this is called —
        // so every plugin that resolves a stream through a WebView (and every
        // Cloudflare challenge that needs one) threw instead of loading.
        com.lagradost.api.setContext(java.lang.ref.WeakReference(this))
        trackForegroundActivity()
        startKoin {
            androidContext(this@MyApp)
            androidLogger()
            modules(NetworkModule, koinModule, firebaseModule)
        }
        warmTrackerLinks()

    }

    /**
     * Pulls both tracker links from the account at launch.
     *
     * For MyAnimeList this is also what renews the token — the backend refreshes
     * on read, so a TV that has been unplugged for a month comes back able to
     * write rather than failing on the first episode.
     */
    private fun warmTrackerLinks() {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { get<AnilistRepository>().refresh() }
            runCatching { get<MalRepository>().refresh() }
        }
    }

    /**
     * CloudStream hands a `.cs3`'s `load()` the foreground activity, and several plugins cast
     * that argument straight to `AppCompatActivity`. We load plugins from background work with
     * no activity in hand, so keep track of the resumed one for [PluginHost] to pass along.
     */
    private fun trackForegroundActivity() {
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                current = WeakReference(activity)
            }

            override fun onActivityDestroyed(activity: Activity) {
                if (current?.get() === activity) current = null
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        })
    }

    companion object {
        @JvmStatic
        lateinit var instance: MyApp
            private set

        @JvmStatic
        val context: Context
            get() = instance

        @Volatile
        private var current: WeakReference<Activity>? = null

        /** The resumed activity, or null while no screen is in the foreground. */
        @JvmStatic
        val currentActivity: Activity?
            get() = current?.get()
    }
}