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
 * Moves D-pad focus to [target] once it is actually laid out.
 *
 * Focus cannot be requested during `onViewCreated`/`onBindViewHolder`: the view is not
 * attached or measured yet, `requestFocus()` returns false, and the screen opens with the
 * highlight wherever the framework happened to leave it — usually the first focusable in the
 * tree, which across this app is repeatedly a 20dp close "X". Posting defers to the first
 * frame, and the `isShown` guard keeps a dialog that was dismissed in the meantime from
 * stealing focus back.
 *
 * Returns nothing on purpose: the caller has no useful recovery, and the fallback below is
 * the recovery.
 */
fun View.requestInitialFocus(target: View = this) {
    post {
        if (!target.isShown) return@post
        if (target.isFocusable && target.requestFocus()) return@post
        // Container that is not itself focusable (the correct shape for a list): let the
        // framework pick its first focusable descendant instead of leaving focus adrift.
        (target as? android.view.ViewGroup)?.requestFocus(View.FOCUS_DOWN)
    }
}

/**
 * Runs [block] — a data-set swap, a visibility change, anything that can remove the focused
 * view — and makes sure focus survives it.
 *
 * Android does NOT reassign focus when the focused view is removed or hidden; from
 * targetSdk 26 onward it clears focus to the root instead. Every "the remote went dead after
 * the list refreshed" bug in this app is that, so the recovery is centralised here: remember
 * what was focused, run the mutation, and afterwards restore it or fall back to this
 * container's first focusable.
 */
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
