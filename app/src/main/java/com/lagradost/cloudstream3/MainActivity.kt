package com.lagradost.cloudstream3

import com.lagradost.cloudstream3.utils.Event

/**
 * A stand-in for CloudStream's Activity, present only so plugins that reference it can be
 * loaded. See [com.lagradost.cloudstream3.utils.Event]'s file for why these exist.
 *
 * This is not an Activity and must never be started. This app's activity is
 * [com.saikou.sozo_tv.presentation.activities.MainActivity]; the class here shares only a name
 * with CloudStream's, in CloudStream's package, because that is the name plugins were compiled
 * against.
 *
 * The three events are the ones StreamPlay, StremioX and Ultima subscribe to. None of them is
 * ever fired: this app loads a plugin when its provider is first used rather than all of them at
 * startup, and its bookmarks and library are Room-backed with no CloudStream counterpart to
 * announce. A plugin that only subscribes keeps working; one that waits on an event to finish
 * its own setup will not, and that is worth knowing rather than papering over.
 */
class MainActivity {
    companion object {
        val afterPluginsLoadedEvent = Event<Boolean>()

        val bookmarksUpdatedEvent = Event<Boolean>()

        val reloadLibraryEvent = Event<Boolean>()
    }
}
