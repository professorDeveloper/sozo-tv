package com.lagradost.cloudstream3.utils

import android.content.Context
import androidx.appcompat.app.AlertDialog

/**
 * Odds and ends from CloudStream's **app** module that `.cs3` plugins link against.
 *
 * ## Why this file exists
 *
 * This app embeds `com.github.recloudstream.cloudstream:library`, the plugin-facing half of
 * CloudStream — `MainAPI`, `APIHolder`, the extractors, `BasePlugin`. Plugins are compiled
 * against the whole CloudStream APP, so they are free to reference anything in it, and several
 * of the largest do. None of those classes ship in `library`.
 *
 * Dalvik resolves a class the first time a code path touches it, so a plugin that mentions one
 * of them anywhere on its load path dies with `NoClassDefFoundError` inside
 * [com.saikou.sozo_tv.engine.cloudstream.PluginHost.loadCs3], which returns an empty provider
 * list. The plugin then downloads, installs, registers nothing and never appears in the Sources
 * screen. Diffing all 80 plugins of the phisher repo against the built APK, five died this way:
 * StreamPlay, TorraStream, Ultima, Anichi and StremioX — three of which were on a hardcoded
 * "unsupported" denylist for exactly this reason.
 *
 * ## What these classes promise
 *
 * Existence, and nothing else. Every member reports empty or no-ops, deliberately: this app has
 * its own AniList and MyAnimeList integration (`data/remote/anilist`, `data/remote/mal`) and its
 * own watch history, and a plugin writing through a second invisible account would put a
 * viewer's list in two places that disagree. A plugin that merely mentions these keeps working;
 * one that depends on them for results gets nothing instead of a crash.
 *
 * Every signature here was read off the plugins' own dex method tables rather than copied from
 * CloudStream, so what is declared is what plugins actually call. Both halves matter: a missing
 * class is a `NoClassDefFoundError`, a class whose method has the wrong descriptor is a
 * `NoSuchMethodError`, and the two are equally fatal.
 *
 * The rest live in [com.lagradost.cloudstream3.MainActivity],
 * [com.lagradost.cloudstream3.plugins.PluginManager],
 * [com.lagradost.cloudstream3.plugins.RepositoryManager],
 * [com.lagradost.cloudstream3.syncproviders.SyncAPI] and
 * [com.lagradost.cloudstream3.ui.home.HomeViewModel].
 */

/**
 * CloudStream's event bus. Plugins subscribe with `+=` and publish with `invoke`.
 *
 * Nothing in this app fires the events on [com.lagradost.cloudstream3.MainActivity], so a
 * subscriber is simply never called. That is the truthful answer here: CloudStream loads every
 * plugin at startup and then announces it, while this app loads a plugin lazily when its
 * provider is first used, so there is no moment "all plugins are loaded" would describe.
 */
class Event<T> {
    private val listeners = mutableListOf<(T) -> Unit>()

    operator fun invoke(value: T) {
        // Copied before iterating: a one-shot subscriber unsubscribing itself while being
        // notified is the ordinary case, and would otherwise mutate the list mid-iteration.
        listeners.toList().forEach { runCatching { it(value) } }
    }

    operator fun plusAssign(listener: (T) -> Unit) {
        listeners += listener
    }
}

/**
 * CloudStream's localisable string. Plugins hold these as list titles and render them with
 * [asString].
 */
open class UiText(private val value: String = "") {
    open fun asString(context: Context?): String = value

    override fun toString(): String = value
}

/**
 * CloudStream's thread-safe list. `APIHolder.allProviders` is one upstream, so a plugin that
 * walks the provider list names this type in its own signatures.
 *
 * Backed by a synchronized list rather than a no-op: a plugin that adds to one expects to read
 * back what it added, and an always-empty collection would be a silent wrong answer rather than
 * a missing feature.
 */
class AtomicMutableList<T>(
    initial: Collection<T> = emptyList(),
) : MutableList<T> by java.util.Collections.synchronizedList(ArrayList(initial))

object AppContextUtils {
    /**
     * Ignored.
     *
     * CloudStream calls this to park the remote's focus on a dialog's default button. This app
     * does its own D-pad focus handling (see the focus guards in the Sources screen), and a
     * plugin moving focus from inside a callback would fight it — the visible result being
     * focus jumping while somebody is holding a direction on the remote.
     */
    fun AlertDialog.setDefaultFocus(index: Int = 0) {
        // Deliberately empty; see above.
    }
}

object DataStoreHelper {
    /** One entry in CloudStream's "continue watching" row. */
    data class ResumeWatchingResult(
        val id: Int? = null,
        val parentId: Int? = null,
    )
}
