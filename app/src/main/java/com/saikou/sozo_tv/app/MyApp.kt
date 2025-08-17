package com.saikou.sozo_tv.app

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import com.bugsnag.android.Bugsnag
import com.bugsnag.android.performance.BugsnagPerformance
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
//import com.ipsat.ipsat_tv.di.NetworkModule
//import com.ipsat.ipsat_tv.di.firebaseModule
//import com.ipsat.ipsat_tv.di.koinModule
import com.jakewharton.threetenabp.AndroidThreeTen
import com.saikou.sozo_tv.di.NetworkModule
import com.saikou.sozo_tv.di.koinModule

class MyApp : Application() {
    @SuppressLint("StaticFieldLeak")
    companion object {
        lateinit var context: Context

    }

    override fun onCreate() {
        super.onCreate()
        context = this@MyApp
        Bugsnag.start(this)
        AndroidThreeTen.init(this) // Initialize ThreeTenABP
        BugsnagPerformance.start(this)
        startKoin {
            androidContext(this@MyApp)
            androidLogger()
            modules(NetworkModule, koinModule)
        }

    }
}