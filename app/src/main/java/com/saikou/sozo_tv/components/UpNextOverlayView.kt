package com.saikou.sozo_tv.components

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.databinding.ViewUpNextBinding
import com.saikou.sozo_tv.utils.loadImage
import kotlin.math.ceil

class UpNextOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    data class Spec(
        val title: String,
        val episodeLabel: String,
        val thumbnailUrl: String?,
        val seconds: Int,
    )

    private val binding = ViewUpNextBinding.inflate(LayoutInflater.from(context), this, true)
    private val handler = Handler(Looper.getMainLooper())
    private var tick: Runnable? = null
    private var onPlayNow: (() -> Unit)? = null
    private var onCancel: (() -> Unit)? = null

    var isShowing: Boolean = false
        private set

    init {
        visibility = GONE
        isFocusable = false
        binding.upNextPlay.setOnClickListener {
            val cb = onPlayNow
            dismiss()
            cb?.invoke()
        }
        binding.upNextCancel.setOnClickListener {
            val cb = onCancel
            dismiss()
            cb?.invoke()
        }
    }

    fun show(spec: Spec, onPlayNow: () -> Unit, onCancel: () -> Unit) {
        stopTicking()
        this.onPlayNow = onPlayNow
        this.onCancel = onCancel
        isShowing = true

        binding.upNextTitle.text = spec.title
        binding.upNextEpisode.text = spec.episodeLabel
        binding.upNextThumb.loadImage(spec.thumbnailUrl)

        val totalMs = spec.seconds.coerceAtLeast(1) * 1000L
        val deadline = SystemClock.uptimeMillis() + totalMs
        render(1f, spec.seconds)

        visibility = VISIBLE
        alpha = 0f
        animate().alpha(1f).setDuration(200).start()
        binding.upNextPlay.requestFocus()

        val runnable = object : Runnable {
            override fun run() {
                val left = deadline - SystemClock.uptimeMillis()
                if (left <= 0) {
                    val cb = this@UpNextOverlayView.onPlayNow
                    dismiss()
                    cb?.invoke()
                    return
                }
                render(left.toFloat() / totalMs, ceil(left / 1000f).toInt())
                handler.postDelayed(this, TICK_MS)
            }
        }
        tick = runnable
        handler.postDelayed(runnable, TICK_MS)
    }

    fun dismiss() {
        stopTicking()
        onPlayNow = null
        onCancel = null
        isShowing = false
        animate().cancel()
        alpha = 1f
        visibility = GONE
    }

    private fun render(fraction: Float, secondsLeft: Int) {
        binding.upNextRing.progress = (fraction * RING_MAX).toInt().coerceIn(0, RING_MAX)
        binding.upNextSeconds.text = secondsLeft.toString()
        binding.upNextCountdown.text = context.getString(R.string.up_next_in_seconds, secondsLeft)
    }

    private fun stopTicking() {
        tick?.let { handler.removeCallbacks(it) }
        tick = null
    }

    override fun onDetachedFromWindow() {
        stopTicking()
        animate().cancel()
        super.onDetachedFromWindow()
    }

    private companion object {
        const val TICK_MS = 100L
        const val RING_MAX = 1000
    }
}
