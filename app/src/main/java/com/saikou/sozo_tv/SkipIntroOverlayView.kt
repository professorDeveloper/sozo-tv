package com.saikou.sozo_tv

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import kotlin.math.min

class SkipIntroOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var isAnimating = false
    private var currentAlpha = 0f
    private var skipType = "Skip Intro"

    private val netflixRed = Color.parseColor("#E50914")
    private val netflixDarkGray = Color.parseColor("#1F1F1F")
    private val netflixWhite = Color.parseColor("#FFFFFF")
    private val netflixLightGray = Color.parseColor("#B3B3B3")

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CC000000")
        style = Paint.Style.FILL
    }

    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = netflixDarkGray
        style = Paint.Style.FILL
        setShadowLayer(24f, 0f, 8f, Color.parseColor("#80000000"))
    }

    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = netflixRed
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = netflixWhite
        textSize = 48f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val descriptionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = netflixLightGray
        textSize = 28f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }

    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = netflixWhite
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private var animator: ValueAnimator? = null
    private var onSkipClicked: (() -> Unit)? = null

    private val cardRect = RectF()
    private val cornerRadius = 16f
    private var centerX = 0f
    private var centerY = 0f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = w / 2f
        centerY = h / 2f

        val cardWidth = min(w * 0.5f, 480f)
        val cardHeight = 200f
        cardRect.set(
            centerX - cardWidth / 2f,
            centerY - cardHeight / 2f,
            centerX + cardWidth / 2f,
            centerY + cardHeight / 2f
        )
    }

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (!isAnimating || currentAlpha <= 0f) return

        backgroundPaint.alpha = (currentAlpha * 204).toInt()
        cardPaint.alpha = (currentAlpha * 255).toInt()
        accentPaint.alpha = (currentAlpha * 255).toInt()
        textPaint.alpha = (currentAlpha * 255).toInt()
        descriptionPaint.alpha = (currentAlpha * 255).toInt()
        iconPaint.alpha = (currentAlpha * 255).toInt()

        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, cardPaint)

        val accentRect = RectF(
            cardRect.left,
            cardRect.top,
            cardRect.left + 8f,
            cardRect.bottom
        )
        canvas.drawRoundRect(accentRect, cornerRadius, cornerRadius, accentPaint)

        val iconCenterX = centerX
        val iconCenterY = centerY - 30f
        val iconSize = 50f

        val path = Path().apply {
            moveTo(iconCenterX - iconSize / 3f, iconCenterY - iconSize / 2f)
            lineTo(iconCenterX + iconSize / 2f, iconCenterY)
            lineTo(iconCenterX - iconSize / 3f, iconCenterY + iconSize / 2f)
        }
        canvas.drawPath(path, iconPaint)

        val path2 = Path().apply {
            moveTo(iconCenterX + iconSize / 6f, iconCenterY - iconSize / 2f)
            lineTo(iconCenterX + iconSize, iconCenterY)
            lineTo(iconCenterX + iconSize / 6f, iconCenterY + iconSize / 2f)
        }
        canvas.drawPath(path2, iconPaint)

        canvas.drawText(skipType, centerX, centerY + 45f, textPaint)
        canvas.drawText("Tap to skip", centerX, centerY + 80f, descriptionPaint)
    }

    fun showSkipButton(
        type: String = "Skip Intro",
        onSkip: (() -> Unit)? = null
    ) {
        if (isAnimating) return

        skipType = type
        onSkipClicked = onSkip
        isAnimating = true
        visibility = VISIBLE

        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 400
            interpolator = AccelerateDecelerateInterpolator()

            addUpdateListener { animation ->
                currentAlpha = animation.animatedValue as Float
                invalidate()
            }

            start()
        }
    }

    fun hideSkipButton() {
        if (!isAnimating) return

        animator?.cancel()
        animator = ValueAnimator.ofFloat(currentAlpha, 0f).apply {
            duration = 200
            interpolator = AccelerateDecelerateInterpolator()

            addUpdateListener { animation ->
                currentAlpha = animation.animatedValue as Float
                invalidate()
            }

            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    isAnimating = false
                    visibility = GONE
                }
            })

            start()
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        if (isAnimating && currentAlpha > 0.5f) {
            onSkipClicked?.invoke()
            hideSkipButton()
        }
        return true
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
        animator = null
    }

    init {
        isClickable = true
        visibility = GONE
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }
}
