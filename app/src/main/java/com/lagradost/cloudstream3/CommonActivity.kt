package com.lagradost.cloudstream3

import android.app.Activity
import android.util.DisplayMetrics
import android.view.KeyEvent
import android.widget.Toast
import com.saikou.sozo_tv.app.MyApp

/**
 * Clean-room stand-in for CloudStream's app-module `CommonActivity`.
 *
 * Plugins are compiled against CloudStream's APP module, not just its library, and several of
 * them reach for this object the moment they need the foreground activity — most visibly when a
 * source hits a Cloudflare challenge and wants to put a WebView in front of the user. The
 * embedded `library` artifact does not ship it, so those plugins died at link time with
 * `NoClassDefFoundError: Lcom/lagradost/cloudstream3/CommonActivity;` and the source simply
 * looked broken (AnimePahe's home page came back empty for exactly this reason).
 *
 * An `object`, because plugin bytecode calls `CommonActivity.INSTANCE.getActivity()`.
 *
 * [activity] delegates to [MyApp.currentActivity] rather than keeping a reference of its own:
 * this app already tracks the resumed activity for [com.saikou.sozo_tv.engine.cloudstream.PluginHost],
 * and a second copy would only be a second thing to get out of date.
 *
 * Deliberately NOT provided, because each drags in machinery this app does not have:
 * `getCastSession` (Google Cast) and the `UiText` overloads (cloudstream3.ui.result.UiText).
 * A plugin that needs those will still fail to link — the same way [plugins.Plugin] documents
 * its own omission — and that is a smaller failure than pretending to support casting.
 */
object CommonActivity {

    /** The resumed activity, or null while none is. */
    @JvmStatic
    var activity: Activity?
        get() = MyApp.currentActivity
        set(_) {
            // One source of truth: MyApp's ActivityLifecycleCallbacks. Plugins that assign here
            // are telling us something we already know.
        }

    /** Upstream signature. A no-op for the same reason the setter above is. */
    @JvmStatic
    fun setActivityInstance(newActivity: Activity?) = Unit

    /** Key events upstream routes to the player; nothing here consumes them yet. */
    @JvmStatic
    var keyEventListener: ((Pair<KeyEvent?, Boolean>) -> Boolean)? = null

    @JvmStatic
    var isInPIPMode: Boolean = false

    @JvmStatic
    var isPipDesired: Boolean = false

    @JvmStatic
    fun showToast(message: String?, duration: Int? = null) =
        showToast(activity, message, duration)

    @JvmStatic
    fun showToast(act: Activity?, message: String?, duration: Int? = null) {
        val text = message?.takeIf { it.isNotBlank() } ?: return
        val host = act ?: activity ?: return
        host.runOnUiThread {
            Toast.makeText(host, text, duration ?: Toast.LENGTH_SHORT).show()
        }
    }

    @JvmStatic
    val displayMetrics: DisplayMetrics
        get() = MyApp.context.resources.displayMetrics

    @JvmStatic
    val screenWidth: Int
        get() = displayMetrics.widthPixels

    @JvmStatic
    val screenHeight: Int
        get() = displayMetrics.heightPixels
}
