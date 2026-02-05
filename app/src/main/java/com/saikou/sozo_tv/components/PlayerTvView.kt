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

        return when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                player.seekTo(player.currentPosition - 10_000)
                true
            }

            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                player.seekTo(player.currentPosition + 10_000)
                true
            }

            else -> super.dispatchKeyEvent(event)
        }
    }

    private fun findPlayPauseButton(): View? {
        return try {
            // ExoPlayer standart play/pause button ID si
            controller.findViewById(androidx.media3.ui.R.id.exo_play_pause)
        } catch (e: Exception) {
            null
        }
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

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)

        if (changedView == controller) {
            if (visibility == View.VISIBLE && lastFocusedView != null) {
                // Controller ko'rsatilganda oxirgi focus qilingan viewga qaytish
                post {
                    if (lastFocusedView?.isShown == true && lastFocusedView?.isFocusable == true) {
                        lastFocusedView?.requestFocus()
                    } else {
                        // Agar oxirgi view mavjud bo'lmasa, play/pause buttoniga focus qilish
                        val playPauseButton = findPlayPauseButton()
                        playPauseButton?.requestFocus()
                    }
                }
            }
        }
    }
}
