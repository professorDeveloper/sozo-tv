package com.saikou.sozo_tv.components

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerControlView
import androidx.media3.ui.PlayerView
import com.saikou.sozo_tv.R


class PlayerTvView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : PlayerView(context, attrs, defStyle) {

    val controller: PlayerControlView
        @OptIn(UnstableApi::class)
        get() = PlayerView::class.java.getDeclaredField("controller").let {
            it.isAccessible = true
            it.get(this) as PlayerControlView
        }

    private var lastFocusedView: View? = null

    private companion object {
        const val SEEK_STEP_MS = 10_000L
    }

    @OptIn(UnstableApi::class)
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val player = player ?: return super.dispatchKeyEvent(event)

        if (player.isCommandAvailable(Player.COMMAND_GET_CURRENT_MEDIA_ITEM) && player.isPlayingAd) {
            return super.dispatchKeyEvent(event)
        }

//        if (!controller.isVisible && event.action == KeyEvent.ACTION_DOWN) {
//            when (event.keyCode) {
//                KeyEvent.KEYCODE_DPAD_CENTER,
//                KeyEvent.KEYCODE_ENTER -> {
//                    // Controllerni ko'rsatish
//                    showController()
//
//                    // Focus ni pause/play buttoniga o'tkazish
//                    post {
//                        val playPauseButton = findPlayPauseButton()
//                        if (playPauseButton != null) {
//                            playPauseButton.requestFocus()
//                        } else {
//                            // Agar play/pause button topilmasa, controllerdagi birinchi focusable elementga focus qilish
//                            val firstFocusable = findFirstFocusableView(controller)
//                            firstFocusable?.requestFocus()
//                        }
//                    }
//                    return true
//                }
//            }
//        }

        if (controller.isVisible) {
            // Hozirgi focus qilingan viewni saqlash
            val currentFocus = controller.findFocus()
            if (currentFocus != null) {
                lastFocusedView = currentFocus
            }
            return super.dispatchKeyEvent(event)
        }

        // Controller hidden: LEFT/RIGHT are a transport control.
        //
        // Two things were wrong here. dispatchKeyEvent sees ACTION_DOWN *and* ACTION_UP, and
        // neither was filtered — so one press seeked twice, 20s instead of the intended 10s.
        // And nothing showed the controller afterwards, so the user was seeking blind with no
        // time bar, position or thumbnail to aim with.
        if (event.action != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event)

        return when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                seekByAndReveal(-SEEK_STEP_MS)
                true
            }

            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                seekByAndReveal(SEEK_STEP_MS)
                true
            }

            else -> super.dispatchKeyEvent(event)
        }
    }

    private fun seekByAndReveal(deltaMs: Long) {
        val p = player ?: return
        val duration = p.duration
        var target = p.currentPosition + deltaMs
        target = target.coerceAtLeast(0L)
        // duration is C.TIME_UNSET (negative) for a live stream — only clamp when known.
        if (duration > 0) target = target.coerceAtMost(duration)
        p.seekTo(target)
        showController()
    }

    /**
     * The controller's play/pause control.
     *
     * The app's own controller layouts declare `exo_play_pause_container` (the focusable
     * FrameLayout) and `exo_play_paused` (the icon inside it) — NOT media3's
     * `exo_play_pause`. Looking only for the media3 id meant this always returned null, so
     * every fallback that depended on it silently did nothing. Try ours first, then media3's
     * for any stock layout, then give up to the caller's own fallback.
     */
    private fun findPlayPauseButton(): View? = try {
        controller.findViewById<View?>(R.id.exo_play_pause_container)
            ?: controller.findViewById(androidx.media3.ui.R.id.exo_play_pause)
    } catch (e: Exception) {
        null
    }

    private fun findFirstFocusableView(viewGroup: View): View? {
        if (viewGroup.isFocusable) {
            return viewGroup
        }

        if (viewGroup is android.view.ViewGroup) {
            for (i in 0 until viewGroup.childCount) {
                val child = viewGroup.getChildAt(i)
                val focusable = findFirstFocusableView(child)
                if (focusable != null) {
                    return focusable
                }
            }
        }

        return null
    }

    /**
     * Restores focus when the controller comes back after its auto-hide.
     *
     * This used to be an `onVisibilityChanged` override guarded on
     * `changedView == controller`, which can never be true: Android dispatches visibility
     * changes DOWN the tree, and `controller` is a child of this view — a parent is never
     * notified about a child. The whole restore was dead code, so after the 5s auto-hide the
     * focused button went GONE, focus was dropped to the root, and the next D-pad press only
     * brought the bar back with nothing highlighted. Listening on the controller itself is
     * the event that actually fires.
     */
    @OptIn(UnstableApi::class)
    private fun installControllerFocusRestore() {
        controller.addVisibilityListener { visibility ->
            if (visibility != View.VISIBLE) return@addVisibilityListener
            post {
                val last = lastFocusedView
                if (last != null && last.isShown && last.isFocusable && last.requestFocus()) {
                    return@post
                }
                if (findPlayPauseButton()?.requestFocus() == true) return@post
                findFirstFocusableView(controller)?.requestFocus()
            }
        }
    }

    @OptIn(UnstableApi::class)
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        installControllerFocusRestore()
    }
}
