package com.saikou.sozo_tv.components

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.os.SystemClock
import android.util.AttributeSet
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import com.saikou.sozo_tv.components.seasonal.SeasonalThemeRegistry
import com.saikou.sozo_tv.components.seasonal.SnowRenderer
import com.saikou.sozo_tv.data.model.SeasonalTheme

class SeasonalBackgroundLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private var currentTheme: SeasonalTheme = SeasonalTheme.DEFAULT
    private var snowRenderer: SnowRenderer? = null

    private var animator: ValueAnimator? = null
    private var lastFrameMs: Long = 0L

    init {
        clipToPadding = false
        clipChildren = false
        isClickable = false
        isFocusable = false
    }

    fun setTheme(theme: SeasonalTheme) {
        if (theme == currentTheme) return
        currentTheme = theme
        applyTheme(theme)
    }

    private fun applyTheme(theme: SeasonalTheme) {
        val cfg = SeasonalThemeRegistry.config(theme)

        if (cfg.backgroundDrawable != null) {
            setBackgroundResource(cfg.backgroundDrawable)
        } else {
            background = null
        }

        snowRenderer = if (cfg.useSnow) SnowRenderer(context) else null
        snowRenderer?.onSizeChanged(width, height)

        restartAnimatorIfNeeded()
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        restartAnimatorIfNeeded()
    }

    override fun onDetachedFromWindow() {
        stopAnimator()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        snowRenderer?.onSizeChanged(w, h)
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        // draw effects over content (low alpha so UI stays readable)
        snowRenderer?.draw(canvas)
    }

    private fun restartAnimatorIfNeeded() {
        if (!isAttachedToWindow) return

        if (snowRenderer == null) {
            stopAnimator()
            return
        }

        if (animator?.isRunning == true) return

        lastFrameMs = SystemClock.uptimeMillis()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1000L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                val now = SystemClock.uptimeMillis()
                val dt = now - lastFrameMs
                lastFrameMs = now

                snowRenderer?.update(dt)
                postInvalidateOnAnimation()
            }
            start()
        }
    }

    private fun stopAnimator() {
        animator?.cancel()
        animator = null
    }
}
