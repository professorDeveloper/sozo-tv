package com.saikou.sozo_tv.components

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import android.view.ViewParent
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerControlView
import androidx.media3.ui.PlayerView
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.presentation.screens.play.TrailerPlayerScreen

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
    private var focusRestoreInstalled = false

    var onNextEpisode: (() -> Unit)? = null
    var onPreviousEpisode: (() -> Unit)? = null

    private var seekRunDirection = 0
    private var seekRunCount = 0
    private var seekRunLastAt = 0L

    private companion object {
        const val SEEK_STEP_MS = 10_000L

        val SEEK_LADDER_MS = longArrayOf(10_000L, 30_000L, 60_000L, 120_000L)
        const val SEEK_RUN_GAP_MS = 700L
    }

    @OptIn(UnstableApi::class)
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val player = player ?: return super.dispatchKeyEvent(event)

        if (player.isCommandAvailable(Player.COMMAND_GET_CURRENT_MEDIA_ITEM) && player.isPlayingAd) {
            return super.dispatchKeyEvent(event)
        }

        if (controller.isVisible) {
            if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.action == KeyEvent.ACTION_UP) hideController()
                return true
            }
            val currentFocus = controller.findFocus()
            if (currentFocus != null) {
                lastFocusedView = currentFocus
                val onTimeBar = currentFocus.id == androidx.media3.ui.R.id.exo_progress
                val confirm = event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                    event.keyCode == KeyEvent.KEYCODE_ENTER
                val scrubbing = (currentFocus as? TrailerPlayerScreen.ExtendedTimeBar)?.scrubbing
                if (onTimeBar && confirm && scrubbing != true) {
                    if (event.action == KeyEvent.ACTION_UP) togglePlayback()
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
            if (event.action == KeyEvent.ACTION_DOWN && isDirectionKey(event.keyCode)) {
                restoreControllerFocus()
                return true
            }
            return super.dispatchKeyEvent(event)
        }

        val focused = findFocus()
        if (focused != null && focused !== this) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_SPACE -> {
                    if (event.action == KeyEvent.ACTION_UP) focused.performClick()
                    return true
                }

                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN ->
                    return super.dispatchKeyEvent(event)
            }
        }

        if (event.action != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event)

        return when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                seekByAndReveal(-stepFor(-1))
                true
            }

            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                seekByAndReveal(stepFor(1))
                true
            }

            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_SPACE,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                togglePlayback()
                true
            }

            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                player.play()
                showController()
                true
            }

            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                player.pause()
                showController()
                true
            }

            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                onNextEpisode?.invoke() ?: return super.dispatchKeyEvent(event)
                true
            }

            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                onPreviousEpisode?.invoke() ?: return super.dispatchKeyEvent(event)
                true
            }

            else -> super.dispatchKeyEvent(event)
        }
    }

    private fun togglePlayback() {
        val p = player ?: return
        if (p.isPlaying) p.pause() else p.play()
        showController()
    }

    private fun stepFor(direction: Int): Long {
        val now = android.os.SystemClock.uptimeMillis()
        if (direction != seekRunDirection || now - seekRunLastAt > SEEK_RUN_GAP_MS) {
            seekRunDirection = direction
            seekRunCount = 0
        }
        seekRunLastAt = now
        val step = SEEK_LADDER_MS[seekRunCount.coerceAtMost(SEEK_LADDER_MS.lastIndex)]
        seekRunCount++
        return step * direction
    }

    private fun seekByAndReveal(deltaMs: Long) {
        val p = player ?: return
        val duration = p.duration
        var target = p.currentPosition + deltaMs
        target = target.coerceAtLeast(0L)
        if (duration > 0) target = target.coerceAtMost(duration)
        p.seekTo(target)
        showController()
    }

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

    private fun isAncestorOf(view: View): Boolean {
        var current: ViewParent? = view.parent
        while (current != null) {
            if (current === this) return true
            current = current.parent
        }
        return false
    }

    private fun isDirectionKey(keyCode: Int) = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> true

        else -> false
    }

    @OptIn(UnstableApi::class)
    private fun restoreControllerFocus() {
        val current = rootView.findFocus()
        if (current != null && current !== this && !isAncestorOf(current)) return
        val last = lastFocusedView
        if (last != null && last.isShown && last.isFocusable && last.requestFocus()) return
        if (findPlayPauseButton()?.requestFocus() == true) return
        findFirstFocusableView(controller)?.requestFocus()
    }

    @OptIn(UnstableApi::class)
    private fun installControllerFocusRestore() {
        if (focusRestoreInstalled) return
        focusRestoreInstalled = true
        controller.addVisibilityListener { visibility ->
            if (visibility != View.VISIBLE) return@addVisibilityListener
            post { restoreControllerFocus() }
        }
    }

    @OptIn(UnstableApi::class)
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        installControllerFocusRestore()
    }
}
