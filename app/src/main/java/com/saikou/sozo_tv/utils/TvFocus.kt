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
