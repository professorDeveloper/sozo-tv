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
    private var focusRestoreInstalled = false

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
            val currentFocus = controller.findFocus()
            if (currentFocus != null) {
                lastFocusedView = currentFocus
                return super.dispatchKeyEvent(event)
            }
            // Focus is on the PlayerView itself. Its rect covers the whole screen, so it is
            // never a directional candidate and the d-pad stays dead until the controls hide.
            if (event.action == KeyEvent.ACTION_DOWN && isDirectionKey(event.keyCode)) {
                restoreControllerFocus()
                return true
            }
            return super.dispatchKeyEvent(event)
        }

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

    private fun isDirectionKey(keyCode: Int) = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> true

        else -> false
    }

    @OptIn(UnstableApi::class)
    private fun restoreControllerFocus() {
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
