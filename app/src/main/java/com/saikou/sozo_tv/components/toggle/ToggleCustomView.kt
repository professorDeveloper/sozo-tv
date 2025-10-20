package com.saikou.sozo_tv.components.toggle

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.saikou.sozo_tv.R

class ToggleCustomView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var isAnimeMode = true // Default to Anime Mode
    private var onModeChanged: ((isAnimeMode: Boolean) -> Unit)? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 18f
        textAlign = Paint.Align.CENTER
    }

    private val colorNetflixRed = ContextCompat.getColor(context, R.color.netflix_red)
    private val colorNetflixGray = ContextCompat.getColor(context, R.color.netflix_gray_dark)
    private val colorNetflixWhite = ContextCompat.getColor(context, R.color.netflix_white)
    private val colorNetflixDarkGray = ContextCompat.getColor(context, R.color.netflix_dark_gray)

    private var animeModeRect = RectF()
    private var movieModeRect = RectF()
    private var isFocused = false

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val width = width.toFloat()
        val height = height.toFloat()
        val padding = 12f
        val cornerRadius = 12f
        val buttonWidth = (width - padding * 3) / 2
        val buttonHeight = height - padding * 2

        animeModeRect.set(
            padding,
            padding,
            padding + buttonWidth,
            padding + buttonHeight
        )

        movieModeRect.set(
            padding * 2 + buttonWidth,
            padding,
            width - padding,
            padding + buttonHeight
        )

        paint.color = if (isAnimeMode) colorNetflixDarkGray else colorNetflixGray
        canvas.drawRoundRect(animeModeRect, cornerRadius, cornerRadius, paint)

        if (isFocused && isAnimeMode) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
            paint.color = colorNetflixRed
            canvas.drawRoundRect(animeModeRect, cornerRadius, cornerRadius, paint)
            paint.style = Paint.Style.FILL
        }

        textPaint.color = colorNetflixWhite
        canvas.drawText(
            "Anime Mode",
            animeModeRect.centerX(),
            animeModeRect.centerY() + textPaint.textSize / 3,
            textPaint
        )

        // Draw Movie Mode Button
        paint.color = if (!isAnimeMode) colorNetflixRed else colorNetflixGray
        canvas.drawRoundRect(movieModeRect, cornerRadius, cornerRadius, paint)

        if (isFocused && !isAnimeMode) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
            paint.color = colorNetflixWhite
            canvas.drawRoundRect(movieModeRect, cornerRadius, cornerRadius, paint)
            paint.style = Paint.Style.FILL
        }

        textPaint.color = if (!isAnimeMode) colorNetflixWhite else colorNetflixWhite
        canvas.drawText(
            "Movie Mode",
            movieModeRect.centerX(),
            movieModeRect.centerY() + textPaint.textSize / 3,
            textPaint
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val x = event.x
                if (animeModeRect.contains(x, event.y)) {
                    if (!isAnimeMode) {
                        isAnimeMode = true
                        onModeChanged?.invoke(true)
                        invalidate()
                    }
                    return true
                } else if (movieModeRect.contains(x, event.y)) {
                    if (isAnimeMode) {
                        isAnimeMode = false
                        onModeChanged?.invoke(false)
                        invalidate()
                    }
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (!isAnimeMode) {
                    isAnimeMode = true
                    onModeChanged?.invoke(true)
                    invalidate()
                }
                true
            }

            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (isAnimeMode) {
                    isAnimeMode = false
                    onModeChanged?.invoke(false)
                    invalidate()
                }
                true
            }

            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                isAnimeMode = !isAnimeMode
                onModeChanged?.invoke(isAnimeMode)
                invalidate()
                true
            }

            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onFocusChanged(
        gainFocus: Boolean,
        direction: Int,
        previouslyFocusedRect: android.graphics.Rect?
    ) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        isFocused = gainFocus
        invalidate()
    }

    fun setOnModeChanged(callback: (isAnimeMode: Boolean) -> Unit) {
        onModeChanged = callback
    }

    fun getSelectedMode(): String = if (isAnimeMode) "Anime Mode" else "Movie Mode"

    fun isAnimeModeSelected(): Boolean = isAnimeMode
}
