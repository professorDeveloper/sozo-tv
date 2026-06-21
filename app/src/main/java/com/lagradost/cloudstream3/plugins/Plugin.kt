package com.lagradost.cloudstream3.plugins

import android.content.Context
import android.content.res.Resources

/**
 * Minimal clean-room re-declaration of CloudStream's `Plugin` base class.
 *
 * CloudStream extensions (`.cs3`) extend `com.lagradost.cloudstream3.plugins.Plugin`
 * (with `load(context)` + `resources`). That class lives in CloudStream's **app**
 * module, NOT in the published `library` artifact — the library only ships
 * `BasePlugin`. Rather than vendor the whole app module, we provide a compatible
 * `Plugin` here (same package + signature, extending the library's `BasePlugin`)
 * so:
 *   1. our PluginHost compiles, and
 *   2. at runtime a loaded plugin class can resolve its superclass against this
 *      one (the PathClassLoader parent = app classLoader, which contains this).
 *
 * `registerMainAPI()` / `filename` are inherited from the library `BasePlugin`.
 * `registerVideoClickAction(VideoClickAction)` is intentionally omitted (it would
 * drag in the app module's `actions` package); the rare plugins that call it will
 * fail to load — acceptable for v1 (vendor VideoClickAction later if needed).
 */
abstract class Plugin : BasePlugin() {
    /** Called when the plugin is loaded. Default delegates to the no-arg load(). */
    @Throws(Throwable::class)
    open fun load(context: Context) {
        load()
    }

    /** Populated when the plugin declared `requiresResources` in its gradle. */
    var resources: Resources? = null

    /** Optional settings hook some plugins set. */
    var openSettings: ((context: Context) -> Unit)? = null
}
