package com.saikou.sozo_tv.app

//import com.ipsat.ipsat_tv.di.NetworkModule
//import com.ipsat.ipsat_tv.di.firebaseModule
//import com.ipsat.ipsat_tv.di.koinModule
import android.app.Application
import android.content.Context
import com.bugsnag.android.Bugsnag
import com.bugsnag.android.performance.BugsnagPerformance
import com.google.firebase.FirebaseApp
import com.jakewharton.threetenabp.AndroidThreeTen
import com.saikou.sozo_tv.di.NetworkModule
import com.saikou.sozo_tv.di.firebaseModule
import com.saikou.sozo_tv.di.koinModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

/**
 * Main activity for SozoTv app.
 * @author Azamov
 * @version 2.3
 */

class MyApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        Bugsnag.start(this)
        AndroidThreeTen.init(this)
        FirebaseApp.initializeApp(this)
        BugsnagPerformance.start(this)
        startKoin {
            androidContext(this@MyApp)
            androidLogger()
            modules(NetworkModule, koinModule, firebaseModule)
        }

    }

    companion object {
        @JvmStatic
        lateinit var instance: MyApp
            private set

        @JvmStatic
        val context: Context
            get() = instance
    }
}