package com.lagradost.cloudstream3

import android.content.Context
import com.google.gson.Gson
import com.lagradost.cloudstream3.utils.DataStore

/**
 * Minimal clean-room re-declaration of CloudStream's `CloudStreamApp` (formerly
 * `AcraApplication`), which lives in CloudStream's **app** module and is therefore missing
 * from the published `library` artifact. A `.cs3` referencing it fails with
 * `NoClassDefFoundError: Lcom/lagradost/cloudstream3/CloudStreamApp;`.
 *
 * We are not the application class, so nothing extends this — [attach] wires the host's
 * context in at startup instead. Only the two companion members the installed plugins link
 * against are declared, and their JVM signatures must stay exactly as they are:
 * `getContext()Landroid/content/Context;` and `setKey(Ljava/lang/String;Ljava/lang/Object;)V`.
 */
class CloudStreamApp {
    companion object {
        private val gson = Gson()

        @Volatile
        private var appContext: Context? = null

        /** Wire the host's application context in; called once from `MyApp.onCreate`. */
        fun attach(context: Context) {
            appContext = context.applicationContext
        }

        val context: Context? get() = appContext

        fun <T> setKey(path: String, value: T) {
            val ctx = appContext ?: return
            DataStore.getSharedPrefs(ctx).edit().putString(path, gson.toJson(value)).apply()
        }

        fun getKey(path: String): String? {
            val ctx = appContext ?: return null
            return DataStore.getSharedPrefs(ctx).getString(path, null)
        }
    }
}
