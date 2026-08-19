package com.saikou.sozo_tv.utils

import android.view.View
import android.view.animation.DecelerateInterpolator

/**
 * Unified D-pad focus feedback for Android TV.
 *
 * The codebase previously hand-rolled focus feedback in every adapter via
 * `AnimationUtils.loadAnimation(context, R.anim.zoom_in / zoom_out)` with
 * inconsistent scales per screen (1.05f here, 1.07f there, 1.1f elsewhere) and
 * `fillAfter = true` (which leaves a stale transform behind on recycle). This
 * helper replaces all of that with ONE consistent, recycle-safe scale-up + z-lift.
 *
 * Pair it with a `state_focused` background selector (which draws the ring / fill)
 * for the full "pop + ring" TV focus effect used across the app.
 *
 * Usage — call once per focusable item (ViewHolder init or right after bind):
 * ```
 * holder.itemView.applyTvFocusScale()          // default 1.08x
 * card.applyTvFocusScale(scale = 1.12f)         // stronger pop for big cards
 * ```
 *
 * For the scaled view not to be clipped by its neighbours, the parent
 * RecyclerView / container should set `android:clipChildren="false"` and
 * `android:clipToPadding="false"`.
 */
private val TV_FOCUS_INTERPOLATOR = DecelerateInterpolator()

fun View.applyTvFocusScale(
    scale: Float = 1.08f,
    durationMs: Long = 150L,
    focusedZ: Float = 12f,
    onFocusChanged: ((view: View, hasFocus: Boolean) -> Unit)? = null,
) {
    // Reset any stale transform (important because RecyclerView reuses views).
    scaleX = 1f
    scaleY = 1f
    translationZ = 0f
    setOnFocusChangeListener { v, hasFocus ->
        v.animate()
            .scaleX(if (hasFocus) scale else 1f)
            .scaleY(if (hasFocus) scale else 1f)
            .translationZ(if (hasFocus) focusedZ else 0f)
            .setDuration(durationMs)
            .setInterpolator(TV_FOCUS_INTERPOLATOR)
            .start()
        onFocusChanged?.invoke(v, hasFocus)
    }
}

/**
 * A one-shot permission for the next screen to take focus.
 *
 * Set when the user activates a navigation item, which is the one case where focus is meant to
 * leave the rail and land in the page. Everything else that loads while the rail is focused has
 * no such permission. Expires on its own so a navigation that never arrives cannot leave the
 * next unrelated load holding it.
 */
object TvNavFocus {
    private const val WINDOW_MS = 2_000L

    @Volatile
    private var requestedAt = 0L

    fun handOff() {
        requestedAt = android.os.SystemClock.uptimeMillis()
    }

    fun consume(): Boolean {
        val at = requestedAt
        if (at == 0L) return false
        requestedAt = 0L
        return android.os.SystemClock.uptimeMillis() - at <= WINDOW_MS
    }
}

/**
 * Places focus on [target] when the screen has nothing better to do with it.
 *
 * Screens call this the moment their data lands, which is not necessarily the moment the user
 * is looking at them: while a page loads, the user is often on the navigation rail. This used
 * to yank focus out of the rail and into the page mid-keypress. Focus is only taken when
 * nothing holds it, or when what holds it already sits on this screen's own path — an
 * ancestor, which is the fragment container being refined inward, or a descendant, which is
 * this screen re-asserting its own selection.
 */
fun View.requestInitialFocus(target: View = this) {
    post {
        if (!target.isShown) return@post
        val current = target.rootView.findFocus()
        if (!TvNavFocus.consume() &&
            current != null &&
            current !== target &&
            !current.isDescendantOf(target) &&
            !target.isDescendantOf(current)
        ) return@post
        if (target.isFocusable && target.requestFocus()) return@post
        (target as? android.view.ViewGroup)?.requestFocus(View.FOCUS_DOWN)
    }
}

fun View.isDescendantOf(ancestor: View): Boolean {
    var parent = this.parent
    while (parent != null) {
        if (parent === ancestor) return true
        parent = parent.parent
    }
    return false
}

fun View.keepFocusAlive(block: () -> Unit) {
    val previouslyFocused = findFocus()
    block()
    post {
        if (previouslyFocused?.isShown == true && previouslyFocused.requestFocus()) return@post
        if (!isShown) return@post
        if (isFocusable && requestFocus()) return@post
        (this as? android.view.ViewGroup)?.requestFocus(View.FOCUS_DOWN)
    }
}
