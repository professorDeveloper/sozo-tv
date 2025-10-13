package com.saikou.sozo_tv.components

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.media3.exoplayer.ExoPlayer
import com.google.android.material.card.MaterialCardView
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.aniskip.AniSkip
import com.saikou.sozo_tv.aniskip.AniSkip.getType
import com.saikou.sozo_tv.presentation.viewmodel.PlayViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SkipIntroView(
    private val controller: ConstraintLayout,
    private val player: ExoPlayer,
    private val viewModel: PlayViewModel,
    private val handler: Handler,
    private val malId: Int,
    private val episodeNumber: Int,
    private val episodeLength: Long
) {
    private var currentTimeStamp: AniSkip.Stamp? = null
    private var skippedTimeStamps: MutableList<AniSkip.Stamp> = mutableListOf()
    private lateinit var skipTimeButton: MaterialCardView
    private lateinit var skipTimeText: TextView
    private lateinit var skipContainer: FrameLayout

    private var fadeInAnimator: ObjectAnimator? = null
    private var fadeOutAnimator: ObjectAnimator? = null
    private var isButtonVisible = false
    private val animationDuration = 300L // Netflix-style quick animations

    fun initialize() {
        val skipView = LayoutInflater.from(controller.context)
            .inflate(R.layout.skip_intro_layout, controller, false) as FrameLayout
        controller.addView(skipView)

        skipContainer = skipView.findViewById(R.id.skip_intro_container)
        skipTimeButton = skipView.findViewById(R.id.skip_intro_button)
        skipTimeText = skipView.findViewById(R.id.skip_intro_text)

        CoroutineScope(Dispatchers.IO).launch {
            viewModel.loadTimeStamps(malId, episodeNumber, episodeLength, useProxyForTimeStamps = true)
        }

        skipTimeButton.setOnClickListener {
            currentTimeStamp?.let { timestamp ->
                player.seekTo((timestamp.interval.endTime * 1000).toLong())
                hideSkipButton() // Hide immediately after clicking
            }
        }

        updateTimeStamp()
    }

    private fun showSkipButton() {
        if (isButtonVisible) return

        fadeOutAnimator?.cancel()
        skipTimeButton.visibility = View.VISIBLE

        fadeInAnimator = ObjectAnimator.ofFloat(skipTimeButton, "alpha", 0f, 1f).apply {
            duration = animationDuration
            interpolator = AccelerateDecelerateInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    isButtonVisible = true
                }
            })
            start()
        }
    }

    private fun hideSkipButton() {
        if (!isButtonVisible) return

        fadeInAnimator?.cancel()

        fadeOutAnimator = ObjectAnimator.ofFloat(skipTimeButton, "alpha", 1f, 0f).apply {
            duration = animationDuration
            interpolator = AccelerateDecelerateInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    skipTimeButton.visibility = View.GONE
                    isButtonVisible = false
                }
            })
            start()
        }
    }

    private fun updateTimeStamp() {
        val playerCurrentTime = player.currentPosition / 1000
        val previousTimeStamp = currentTimeStamp

        currentTimeStamp = viewModel.timeStamps.value?.find { timestamp ->
            timestamp.interval.startTime < playerCurrentTime &&
                    playerCurrentTime < (timestamp.interval.endTime - 1)
        }

        val new = currentTimeStamp

        if (new != null) {
            val skipTypeText = when (new.skipType) {
                "op" -> "Skip Intro"
                "ed" -> "Skip Outro"
                "recap" -> "Skip Recap"
                "mixed-op" -> "Skip Intro"
                "mixed-ed" -> "Skip Outro"
                else -> new.skipType.getType()
            }

            skipTimeText.text = skipTypeText

            if (previousTimeStamp != new) {
                showSkipButton()
            }

            val autoSkipEnabled = true // Replace with actual settings logic
            if (autoSkipEnabled &&
                (new.skipType == "op" || new.skipType == "ed") &&
                !skippedTimeStamps.contains(new)) {
                player.seekTo((new.interval.endTime * 1000).toLong())
                skippedTimeStamps.add(new)
                hideSkipButton()
            }
        } else {
            if (previousTimeStamp != null) {
                hideSkipButton()
            }
        }

        handler.postDelayed({ updateTimeStamp() }, 500)
    }

    fun cleanup() {
        fadeInAnimator?.cancel()
        fadeOutAnimator?.cancel()
        handler.removeCallbacksAndMessages(null)
    }
}
