package com.saikou.sozo_tv.components

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import android.view.ViewGroup
import androidx.media3.exoplayer.ExoPlayer
import com.google.android.material.card.MaterialCardView
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.SkipIntroOverlayView
import com.saikou.sozo_tv.aniskip.AniSkip
import com.saikou.sozo_tv.aniskip.AniSkip.getType
import com.saikou.sozo_tv.data.local.pref.PreferenceManager
import com.saikou.sozo_tv.presentation.viewmodel.PlayAnimeViewModel

class SkipIntroView(
    private val controller: ViewGroup,
    private val player: ExoPlayer,
    private val viewModel: PlayAnimeViewModel,
    private val handler: Handler,
    private val malId: Int,
    private val episodeNumber: Int,
    private val episodeLength: Long
) {
    private var currentTimeStamp: AniSkip.Stamp? = null
    private var skippedTimeStamps: MutableSet<String> = mutableSetOf()
    private lateinit var skipTimeButton: MaterialCardView
    private lateinit var skipTimeText: TextView
    private lateinit var manualSkipButton: MaterialCardView
    private lateinit var manualSkipText: TextView
    private lateinit var fixedSkipButton: MaterialCardView
    private lateinit var skipIntroOverlay: SkipIntroOverlayView
    private lateinit var preferenceManager: PreferenceManager
    private var skipView: FrameLayout? = null

    private var fadeInAnimator: ObjectAnimator? = null
    private var fadeOutAnimator: ObjectAnimator? = null
    private var manualButtonAnimator: ObjectAnimator? = null
    private var overlayAnimator: ObjectAnimator? = null
    private var isButtonVisible = false
    private var isManualButtonVisible = false
    private var isFixedButtonVisible = false
    private var fixedSkipDone = false
    private var hideFixedRunnable: Runnable? = null
    private var isOverlayVisible = false
    private val animationDuration = 400L
    private val showDelay = 500L
    private var delayedShowRunnable: Runnable? = null
    private var autoSkipRunnable: Runnable? = null
    private var currentTimestampId: String? = null
    private var updateRunnable: Runnable? = null

    companion object {
        const val FIXED_SKIP_SECONDS = 85L


        const val FIXED_SKIP_WINDOW_START = 5_000L
        const val FIXED_SKIP_WINDOW_END = 240_000L
        const val FIXED_SKIP_VISIBLE_MS = 10_000L
    }

    fun initialize() {
        val skipView = LayoutInflater.from(controller.context)
            .inflate(R.layout.skip_intro_layout, controller, false) as FrameLayout
        controller.addView(skipView)
        this.skipView = skipView

        skipTimeButton = skipView.findViewById(R.id.skip_intro_button)
        skipTimeText = skipView.findViewById(R.id.skip_intro_text)
        manualSkipButton = skipView.findViewById(R.id.manual_skip_intro_button)
        manualSkipText = skipView.findViewById(R.id.manual_skip_intro_text)
        fixedSkipButton = skipView.findViewById(R.id.fixed_skip_intro_button)
        skipView.findViewById<TextView>(R.id.fixed_skip_intro_text).text =
            controller.context.getString(R.string.skip_forward_seconds, FIXED_SKIP_SECONDS.toInt())
        skipIntroOverlay = skipView.findViewById(R.id.skip_intro_overlay)
        preferenceManager = PreferenceManager()

        viewModel.loadTimeStamps(
            malId,
            episodeNumber,
            episodeLength,
            useProxyForTimeStamps = true
        )

        skipTimeButton.setOnClickListener {
            currentTimeStamp?.let { timestamp ->
                player.seekTo((timestamp.interval.endTime * 1000).toLong())
                skippedTimeStamps.add(getTimestampId(timestamp))
                hideSkipButton()
                hideOverlay()
            }
        }

        manualSkipButton.setOnClickListener {
            currentTimeStamp?.let { timestamp ->
                val skipTypeText = labelFor(timestamp.skipType)

                showOverlay(skipTypeText, timestamp)

                handler.postDelayed({
                    player.seekTo((timestamp.interval.endTime * 1000).toLong())
                    skippedTimeStamps.add(getTimestampId(timestamp))
                    hideManualButton()
                    hideOverlay()
                }, 300)
            }
        }

        fixedSkipButton.setOnClickListener {
            val target = (player.currentPosition + FIXED_SKIP_SECONDS * 1000L)
                .coerceAtMost(maxOf(player.duration - 1000L, 0L))
            player.seekTo(target)
            fixedSkipDone = true
            hideFixedButton()
        }

        skipIntroOverlay.setOnClickListener {
            currentTimeStamp?.let { timestamp ->
                player.seekTo((timestamp.interval.endTime * 1000).toLong())
                skippedTimeStamps.add(getTimestampId(timestamp))
                hideOverlay()
                hideSkipButton()
            }
        }

        updateTimeStamp()
    }

    private fun shouldShowFixedButton(): Boolean {
        if (fixedSkipDone || isFixedButtonVisible) return false
        if (currentTimeStamp != null) return false
        if (isButtonVisible || isManualButtonVisible) return false
        val position = player.currentPosition
        return position in FIXED_SKIP_WINDOW_START..FIXED_SKIP_WINDOW_END
    }

    private fun showFixedButton() {
        if (isFixedButtonVisible) return
        isFixedButtonVisible = true
        fixedSkipButton.visibility = View.VISIBLE
        fixedSkipButton.alpha = 0f
        hideFixedRunnable?.let { handler.removeCallbacks(it) }
        hideFixedRunnable = Runnable { if (!fixedSkipDone) hideFixedButton() }
        handler.postDelayed(hideFixedRunnable!!, FIXED_SKIP_VISIBLE_MS)
        ObjectAnimator.ofFloat(fixedSkipButton, "alpha", 0f, 1f).apply {
            duration = animationDuration
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun hideFixedButton() {
        if (!isFixedButtonVisible && fixedSkipButton.visibility == View.GONE) return
        isFixedButtonVisible = false
        ObjectAnimator.ofFloat(fixedSkipButton, "alpha", fixedSkipButton.alpha, 0f).apply {
            duration = animationDuration
            interpolator = AccelerateDecelerateInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    fixedSkipButton.visibility = View.GONE
                }
            })
            start()
        }
    }

    private fun labelFor(skipType: String): String {
        val res = when (skipType) {
            "op", "mixed-op" -> R.string.skip_intro_label
            "ed", "mixed-ed" -> R.string.skip_outro_label
            "recap" -> R.string.skip_recap_label
            else -> null
        }
        return res?.let { controller.context.getString(it) } ?: skipType.getType()
    }

    private fun canTakeFocus(): Boolean {
        val focused = controller.rootView.findFocus() ?: return true
        return focused === controller
    }

    private fun getTimestampId(timestamp: AniSkip.Stamp): String {
        return "${timestamp.skipType}_${timestamp.interval.startTime}_${timestamp.interval.endTime}"
    }

    fun resetSkippedTimestamps() {
        skippedTimeStamps.clear()
        currentTimestampId = null
        currentTimeStamp = null

        delayedShowRunnable?.let { handler.removeCallbacks(it) }
        autoSkipRunnable?.let { handler.removeCallbacks(it) }
        updateRunnable?.let { handler.removeCallbacks(it) }

        hideSkipButton()
        hideManualButton()
        hideFixedButton()
        hideOverlay()
        fixedSkipDone = false
    }

    private fun showManualButton() {
        if (isManualButtonVisible) return

        manualButtonAnimator?.cancel()
        manualSkipButton.visibility = View.VISIBLE
        manualSkipButton.alpha = 0f
        if (canTakeFocus()) manualSkipButton.requestFocus()

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

        delayedShowRunnable?.let { handler.removeCallbacks(it) }

        delayedShowRunnable = Runnable {
            fadeOutAnimator?.cancel()
            skipTimeButton.visibility = View.VISIBLE
            skipTimeButton.alpha = 0f
            if (canTakeFocus()) skipTimeButton.requestFocus()

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

        isOverlayVisible = true
        skipIntroOverlay.showSkipButton(skipTypeText) {
            player.seekTo((timestamp.interval.endTime * 1000).toLong())
            skippedTimeStamps.add(getTimestampId(timestamp))
            hideSkipButton()
        }
    }

    private fun hideOverlay() {
        if (!isOverlayVisible && skipIntroOverlay.visibility == View.GONE) return

        isOverlayVisible = false
        skipIntroOverlay.hideSkipButton()
    }

    private fun updateTimeStamp() {
        val playerCurrentTime = player.currentPosition / 1000

        currentTimeStamp = viewModel.stampsFor(episodeNumber)?.find { timestamp ->
            val timestampId = getTimestampId(timestamp)
            timestamp.interval.startTime < playerCurrentTime &&
                    playerCurrentTime < (timestamp.interval.endTime - 1) &&
                    !skippedTimeStamps.contains(timestampId)
        }

        val new = currentTimeStamp

        if (new != null) {
            val newTimestampId = getTimestampId(new)

            if (currentTimestampId != newTimestampId) {

                currentTimestampId = newTimestampId

                val skipTypeText = labelFor(new.skipType)

                skipTimeText.text = skipTypeText

                val autoSkipEnabled = preferenceManager.isSkipIntroEnabled()

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
                                player.seekTo((new.interval.endTime * 1000).toLong())
                                skippedTimeStamps.add(newTimestampId)
                                hideSkipButton()
                                hideOverlay()
                            }
                        }
                        handler.postDelayed(autoSkipRunnable!!, 2000)
                    }
                } else {
                    manualSkipText.text = skipTypeText
                    showManualButton()
                }
            }
        } else {
            if (currentTimestampId != null) {
                currentTimestampId = null
                autoSkipRunnable?.let { handler.removeCallbacks(it) }
                hideSkipButton()
                hideManualButton()
                hideOverlay()
            }
        }

        if (shouldShowFixedButton()) {
            showFixedButton()
        } else if (isFixedButtonVisible) {
            hideFixedButton()
        }

        updateRunnable = Runnable { updateTimeStamp() }
        handler.postDelayed(updateRunnable!!, 500)
    }

    private fun cleanup() {
        delayedShowRunnable?.let { handler.removeCallbacks(it) }
        autoSkipRunnable?.let { handler.removeCallbacks(it) }
        updateRunnable?.let { handler.removeCallbacks(it) }
        hideFixedRunnable?.let { handler.removeCallbacks(it) }
        fadeInAnimator?.cancel()
        fadeOutAnimator?.cancel()
        manualButtonAnimator?.cancel()
        overlayAnimator?.cancel()
        handler.removeCallbacksAndMessages(null)

        skipTimeButton.visibility = View.GONE
        manualSkipButton.visibility = View.GONE
        fixedSkipButton.visibility = View.GONE
        skipIntroOverlay.visibility = View.GONE
        isButtonVisible = false
        isManualButtonVisible = false
        isFixedButtonVisible = false
        isOverlayVisible = false
    }

    fun detach() {
        if (!::skipTimeButton.isInitialized) return
        cleanup()
        skipView?.let { controller.removeView(it) }
        skipView = null
    }
}
