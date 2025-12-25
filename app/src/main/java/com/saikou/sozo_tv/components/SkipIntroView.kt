package com.saikou.sozo_tv.components

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.os.Handler
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.media3.exoplayer.ExoPlayer
import com.google.android.material.card.MaterialCardView
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.SkipIntroOverlayView
import com.saikou.sozo_tv.aniskip.AniSkip
import com.saikou.sozo_tv.aniskip.AniSkip.getType
import com.saikou.sozo_tv.data.local.pref.PreferenceManager
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
    private var skippedTimeStamps: MutableSet<String> = mutableSetOf()
    private lateinit var skipTimeButton: MaterialCardView
    private lateinit var skipTimeText: TextView
    private lateinit var skipContainer: FrameLayout
    private lateinit var manualSkipButton: MaterialCardView
    private lateinit var skipIntroOverlay: SkipIntroOverlayView
    private lateinit var preferenceManager: PreferenceManager

    private var fadeInAnimator: ObjectAnimator? = null
    private var fadeOutAnimator: ObjectAnimator? = null
    private var manualButtonAnimator: ObjectAnimator? = null
    private var overlayAnimator: ObjectAnimator? = null
    private var isButtonVisible = false
    private var isManualButtonVisible = false
    private var isOverlayVisible = false
    private val animationDuration = 400L
    private val showDelay = 500L
    private var delayedShowRunnable: Runnable? = null
    private var autoSkipRunnable: Runnable? = null
    private var currentTimestampId: String? = null
    private var updateRunnable: Runnable? = null

    fun initialize() {
        val skipView = LayoutInflater.from(controller.context)
            .inflate(R.layout.skip_intro_layout, controller, false) as FrameLayout
        controller.addView(skipView)

        skipContainer = skipView.findViewById(R.id.skip_intro_container)
        skipTimeButton = skipView.findViewById(R.id.skip_intro_button)
        skipTimeText = skipView.findViewById(R.id.skip_intro_text)
        manualSkipButton = skipView.findViewById(R.id.manual_skip_intro_button)
        skipIntroOverlay = skipView.findViewById(R.id.skip_intro_overlay)
        preferenceManager = PreferenceManager()

        Log.d(
            "SkipIntroView",
            "[v0] Initializing skip intro for episode $episodeNumber, malId: $malId"
        )

        CoroutineScope(Dispatchers.IO).launch {
            viewModel.loadTimeStamps(
                malId,
                episodeNumber,
                episodeLength,
                useProxyForTimeStamps = true
            )
        }

        skipTimeButton.setOnClickListener {
            Log.d("SkipIntroView", "[v0] Skip button clicked")
            currentTimeStamp?.let { timestamp ->
                player.seekTo((timestamp.interval.endTime * 1000).toLong())
                skippedTimeStamps.add(getTimestampId(timestamp))
                hideSkipButton()
                hideOverlay()
            }
        }

        manualSkipButton.setOnClickListener {
            Log.d("SkipIntroView", "[v0] Manual skip button clicked")
            currentTimeStamp?.let { timestamp ->
                val skipTypeText = when (timestamp.skipType) {
                    "op" -> "Skip Intro"
                    "ed" -> "Skip Outro"
                    "recap" -> "Skip Recap"
                    "mixed-op" -> "Skip Intro"
                    "mixed-ed" -> "Skip Outro"
                    else -> timestamp.skipType.getType()
                }

                // Show overlay briefly
                showOverlay(skipTypeText, timestamp)

                // Skip after 300ms
                handler.postDelayed({
                    player.seekTo((timestamp.interval.endTime * 1000).toLong())
                    skippedTimeStamps.add(getTimestampId(timestamp))
                    hideManualButton()
                    hideOverlay()
                }, 300)
            }
        }

        skipIntroOverlay.setOnClickListener {
            Log.d("SkipIntroView", "[v0] Overlay clicked")
            currentTimeStamp?.let { timestamp ->
                player.seekTo((timestamp.interval.endTime * 1000).toLong())
                skippedTimeStamps.add(getTimestampId(timestamp))
                hideOverlay()
                hideSkipButton()
            }
        }

        updateTimeStamp()
    }

    private fun getTimestampId(timestamp: AniSkip.Stamp): String {
        return "${timestamp.skipType}_${timestamp.interval.startTime}_${timestamp.interval.endTime}"
    }

    fun resetSkippedTimestamps() {
        Log.d("SkipIntroView", "[v0] Resetting skipped timestamps and cleaning up UI")
        skippedTimeStamps.clear()
        currentTimestampId = null
        currentTimeStamp = null

        // Cancel all pending callbacks
        delayedShowRunnable?.let { handler.removeCallbacks(it) }
        autoSkipRunnable?.let { handler.removeCallbacks(it) }
        updateRunnable?.let { handler.removeCallbacks(it) }

        // Hide all UI elements immediately
        hideSkipButton()
        hideManualButton()
        hideOverlay()
    }

    private fun showManualButton() {
        if (isManualButtonVisible) return

        Log.d("SkipIntroView", "[v0] Showing manual skip button")
        manualButtonAnimator?.cancel()
        manualSkipButton.visibility = View.VISIBLE
        manualSkipButton.alpha = 0f
        manualSkipButton.requestFocus()

        manualButtonAnimator = ObjectAnimator.ofFloat(manualSkipButton, "alpha", 0f, 1f).apply {
            duration = animationDuration
            interpolator = AccelerateDecelerateInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    isManualButtonVisible = true
                }
            })
            start()
        }
    }

    private fun hideManualButton() {
        if (!isManualButtonVisible && manualSkipButton.visibility == View.GONE) return

        Log.d("SkipIntroView", "[v0] Hiding manual skip button")
        manualButtonAnimator?.cancel()

        if (manualSkipButton.alpha == 0f || !isManualButtonVisible) {
            manualSkipButton.visibility = View.GONE
            isManualButtonVisible = false
            return
        }

        manualButtonAnimator =
            ObjectAnimator.ofFloat(manualSkipButton, "alpha", manualSkipButton.alpha, 0f).apply {
                duration = 200L
                interpolator = AccelerateDecelerateInterpolator()
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        manualSkipButton.visibility = View.GONE
                        isManualButtonVisible = false
                    }
                })
                start()
            }
    }

    private fun showSkipButton() {
        if (isButtonVisible) return

        Log.d("SkipIntroView", "[v0] Showing skip button with delay")
        delayedShowRunnable?.let { handler.removeCallbacks(it) }

        delayedShowRunnable = Runnable {
            fadeOutAnimator?.cancel()
            skipTimeButton.visibility = View.VISIBLE
            skipTimeButton.alpha = 0f
            skipTimeButton.requestFocus()

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

        handler.postDelayed(delayedShowRunnable!!, showDelay)
    }

    private fun hideSkipButton() {
        delayedShowRunnable?.let { handler.removeCallbacks(it) }

        if (!isButtonVisible && skipTimeButton.visibility == View.GONE) return

        Log.d("SkipIntroView", "[v0] Hiding skip button")
        fadeInAnimator?.cancel()

        if (skipTimeButton.alpha == 0f || !isButtonVisible) {
            skipTimeButton.visibility = View.GONE
            isButtonVisible = false
            return
        }

        fadeOutAnimator =
            ObjectAnimator.ofFloat(skipTimeButton, "alpha", skipTimeButton.alpha, 0f).apply {
                duration = 200L
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

    private fun showOverlay(skipTypeText: String, timestamp: AniSkip.Stamp) {
        if (isOverlayVisible) return

        Log.d("SkipIntroView", "[v0] Showing overlay: $skipTypeText")
        isOverlayVisible = true
        skipIntroOverlay.showSkipButton(skipTypeText) {
            player.seekTo((timestamp.interval.endTime * 1000).toLong())
            skippedTimeStamps.add(getTimestampId(timestamp))
            hideSkipButton()
        }
    }

    private fun hideOverlay() {
        if (!isOverlayVisible && skipIntroOverlay.visibility == View.GONE) return

        Log.d("SkipIntroView", "[v0] Hiding overlay")
        isOverlayVisible = false
        skipIntroOverlay.hideSkipButton()
    }

    private fun updateTimeStamp() {
        val playerCurrentTime = player.currentPosition / 1000
        val previousTimeStamp = currentTimeStamp

        currentTimeStamp = viewModel.timeStamps.value?.find { timestamp ->
            val timestampId = getTimestampId(timestamp)
            timestamp.interval.startTime < playerCurrentTime &&
                    playerCurrentTime < (timestamp.interval.endTime - 1) &&
                    !skippedTimeStamps.contains(timestampId)
        }

        val new = currentTimeStamp

        if (new != null) {
            val newTimestampId = getTimestampId(new)

            if (currentTimestampId != newTimestampId) {
                Log.d(
                    "SkipIntroView",
                    "[v0] New timestamp detected: ${new.skipType} at ${new.interval.startTime}-${new.interval.endTime}"
                )

                currentTimestampId = newTimestampId

                val skipTypeText = when (new.skipType) {
                    "op" -> "Skip Intro"
                    "ed" -> "Skip Outro"
                    "recap" -> "Skip Recap"
                    "mixed-op" -> "Skip Intro"
                    "mixed-ed" -> "Skip Outro"
                    else -> new.skipType.getType()
                }

                skipTimeText.text = skipTypeText

                val autoSkipEnabled = preferenceManager.isSkipIntroEnabled()
                Log.d("SkipIntroView", "[v0] Auto-skip enabled: $autoSkipEnabled")

                if (autoSkipEnabled) {
                    showOverlay(skipTypeText, new)
                    showSkipButton()

                    if (new.skipType == "op" || new.skipType == "ed") {
                        autoSkipRunnable?.let { handler.removeCallbacks(it) }
                        autoSkipRunnable = Runnable {
                            if (currentTimestampId == newTimestampId && !skippedTimeStamps.contains(
                                    newTimestampId
                                )
                            ) {
                                Log.d("SkipIntroView", "[v0] Auto-skipping timestamp")
                                player.seekTo((new.interval.endTime * 1000).toLong())
                                skippedTimeStamps.add(newTimestampId)
                                hideSkipButton()
                                hideOverlay()
                            }
                        }
                        handler.postDelayed(autoSkipRunnable!!, 2000)
                    }
                } else {
                    showManualButton()
                }
            }
        } else {
            if (currentTimestampId != null) {
                Log.d("SkipIntroView", "[v0] No active timestamp, hiding all UI")
                currentTimestampId = null
                autoSkipRunnable?.let { handler.removeCallbacks(it) }
                hideSkipButton()
                hideManualButton()
                hideOverlay()
            }
        }

        updateRunnable = Runnable { updateTimeStamp() }
        handler.postDelayed(updateRunnable!!, 500)
    }

    fun cleanup() {
        Log.d("SkipIntroView", "[v0] Cleaning up skip intro view")
        delayedShowRunnable?.let { handler.removeCallbacks(it) }
        autoSkipRunnable?.let { handler.removeCallbacks(it) }
        updateRunnable?.let { handler.removeCallbacks(it) }
        fadeInAnimator?.cancel()
        fadeOutAnimator?.cancel()
        manualButtonAnimator?.cancel()
        overlayAnimator?.cancel()
        handler.removeCallbacksAndMessages(null)

        skipTimeButton.visibility = View.GONE
        manualSkipButton.visibility = View.GONE
        skipIntroOverlay.visibility = View.GONE
        isButtonVisible = false
        isManualButtonVisible = false
        isOverlayVisible = false
    }
}
