package com.saikou.sozo_tv.components
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.sin

class CountdownOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var countdownSeconds = 5
    private var currentProgress = 0f
    private var isAnimating = false

    private var nextEpisodeNumber = 1
    private var currentEpisodeNumber = 1
    private var showTitle = ""
    private var isEnglish = false

    // Paint objects for drawing
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#80000000") // Semi-transparent black
        style = Paint.Style.FILL
    }

    private val circleBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#40FFFFFF") // Semi-transparent white background track
        style = Paint.Style.STROKE
        strokeWidth = 12f
        strokeCap = Paint.Cap.ROUND
    }

    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 8f
        strokeCap = Paint.Cap.ROUND
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF4444") // Red color for better visibility
        style = Paint.Style.STROKE
        strokeWidth = 12f
        strokeCap = Paint.Cap.ROUND
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 48f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    private val descriptionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 24f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT
    }

    private var animator: ValueAnimator? = null
    private var onCountdownFinished: (() -> Unit)? = null
    private var onCountdownCancelled: (() -> Unit)? = null

    // Circle properties
    private val circleRadius = 80f
    private var centerX = 0f
    private var centerY = 0f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = w / 2f
        centerY = h / 2f - 50f // Slightly above center to make room for text
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (!isAnimating) return

        // Draw semi-transparent background
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        val rect = RectF(
            centerX - circleRadius,
            centerY - circleRadius,
            centerX + circleRadius,
            centerY + circleRadius
        )
        canvas.drawArc(rect, 0f, 360f, false, circleBackgroundPaint)

        val remainingProgress = 1f - currentProgress
        val sweepAngle = 360f * remainingProgress

        // Draw the decreasing progress arc starting from top (-90 degrees)
        if (remainingProgress > 0) {
            canvas.drawArc(rect, -90f, sweepAngle, false, progressPaint)
        }

        canvas.drawCircle(centerX, centerY, circleRadius - 20f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1A1A1A") // Dark background for number
            style = Paint.Style.FILL
        })

        // Draw countdown number
        val remainingSeconds = (countdownSeconds * remainingProgress).toInt() + 1
        canvas.drawText(
            remainingSeconds.toString(),
            centerX,
            centerY + textPaint.textSize / 3f,
            textPaint
        )

        val descriptionY = centerY + circleRadius + 60f

        if (isEnglish) {
            // English text
            val line1 = "Next episode in $remainingSeconds seconds"
            val line2 = if (showTitle.isNotEmpty()) {
                "$showTitle - Episode $nextEpisodeNumber"
            } else {
                "Episode $nextEpisodeNumber"
            }

            canvas.drawText(line1, centerX, descriptionY, descriptionPaint)
            canvas.drawText(line2, centerX, descriptionY + 35f, descriptionPaint)
        } else {
            // Uzbek text
            val line1 = "$remainingSeconds soniyadan so'ng, $nextEpisodeNumber-"
            val line2 = "seriyaga o'tib ketadi."

            canvas.drawText(line1, centerX, descriptionY, descriptionPaint)
            canvas.drawText(line2, centerX, descriptionY + 35f, descriptionPaint)
        }
    }

    fun startCountdown(
        seconds: Int = 5,
        nextEpisode: Int = 1,
        currentEpisode: Int = 1,
        title: String = "",
        useEnglish: Boolean = false,
        onFinished: (() -> Unit)? = null,
        onCancelled: (() -> Unit)? = null
    ) {
        if (isAnimating) {
            stopCountdown()
        }

        countdownSeconds = seconds
        nextEpisodeNumber = nextEpisode
        currentEpisodeNumber = currentEpisode
        showTitle = title
        isEnglish = useEnglish
        currentProgress = 0f
        isAnimating = true
        onCountdownFinished = onFinished
        onCountdownCancelled = onCancelled

        visibility = VISIBLE

        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = (seconds * 1000).toLong()
            interpolator = LinearInterpolator()

            addUpdateListener { animation ->
                currentProgress = animation.animatedValue as Float
                invalidate()
            }

            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    isAnimating = false
                    visibility = GONE
                    onCountdownFinished?.invoke()
                }

                override fun onAnimationCancel(animation: Animator) {
                    isAnimating = false
                    visibility = GONE
                    onCountdownCancelled?.invoke()
                }
            })

            start()
        }
    }

    fun updateEpisodeInfo(
        nextEpisode: Int,
        currentEpisode: Int = currentEpisodeNumber,
        title: String = showTitle,
        useEnglish: Boolean = isEnglish
    ) {
        nextEpisodeNumber = nextEpisode
        currentEpisodeNumber = currentEpisode
        showTitle = title
        isEnglish = useEnglish
        if (isAnimating) {
            invalidate()
        }
    }

    fun stopCountdown() {
        animator?.cancel()
        animator = null
        isAnimating = false
        visibility = GONE
    }

    fun pauseCountdown() {
        animator?.pause()
    }

    fun resumeCountdown() {
        animator?.resume()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopCountdown()
    }

    // Handle click to cancel countdown
    override fun performClick(): Boolean {
        super.performClick()
        if (isAnimating) {
            stopCountdown()
            onCountdownCancelled?.invoke()
        }
        return true
    }

    init {
        // Make view clickable to handle touch events
        isClickable = true
        visibility = GONE
    }
}
