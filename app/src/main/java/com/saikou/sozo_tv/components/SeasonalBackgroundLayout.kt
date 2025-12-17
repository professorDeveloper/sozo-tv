package com.saikou.sozo_tv.components

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.components.seasonal.SeasonalThemeRegistry
import com.saikou.sozo_tv.components.seasonal.SnowRenderer
import com.saikou.sozo_tv.data.model.SeasonalTheme

/**
 * Seasonal background layer for TV:
 * - DEFAULT: nothing (just your normal Netflix black screen)
 * - WINTER: snow + small winter icons ("Surprise Me")
 *
 * IMPORTANT: this does NOT change global app theme/colors.
 */
class SeasonalBackgroundLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private var currentTheme: SeasonalTheme = SeasonalTheme.DEFAULT

    private var snowRenderer: SnowRenderer? = null

    private var animator: ValueAnimator? = null
    private var lastFrameMs: Long = 0L

    // Track if *this view* applied a background via theme (so we can reset cleanly).
    private var appliedThemeBackground: Boolean = false

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


    /**
     * Called from UI (Settings -> "Surprise me again") to reshuffle winter icons.
     */

    private fun applyTheme(theme: SeasonalTheme) {
        val cfg = SeasonalThemeRegistry.config(theme)

        // Background:
        // - If XML already set a background for this view (e.g., preview card), keep it.
        // - If a theme background was applied before, reset to null when not needed.
        if (cfg.backgroundDrawable != null) {
            setBackgroundResource(cfg.backgroundDrawable)
            appliedThemeBackground = true
        } else if (appliedThemeBackground) {
            background = null
            appliedThemeBackground = false
        } else {
            // keep existing background (or null)
        }

        // If nothing set, keep Netflix black as default.
        if (background == null) {
            setBackgroundColor(ContextCompat.getColor(context, R.color.netflix_background_primary))
        }

        if (cfg.useSnow) {
            if (snowRenderer == null) snowRenderer = SnowRenderer(context)
            snowRenderer?.onSizeChanged(width, height)
        } else {
            snowRenderer = null
        }



        updateAnimationState()
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateAnimationState()
    }

    override fun onDetachedFromWindow() {
        stopAnimator()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        snowRenderer?.onSizeChanged(w, h)
        updateAnimationState()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        updateAnimationState()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        updateAnimationState()
    }

    override fun dispatchDraw(canvas: Canvas) {
        snowRenderer?.draw(canvas)
        super.dispatchDraw(canvas)
    }

    private fun updateAnimationState() {
        val shouldRun =
            isAttachedToWindow &&
                    (snowRenderer != null) &&
                    isShown &&
                    width > 0 &&
                    height > 0

        if (shouldRun) startAnimatorIfNeeded() else stopAnimator()
    }

    private fun startAnimatorIfNeeded() {
        if (animator?.isRunning == true) return

        lastFrameMs = SystemClock.uptimeMillis()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1000L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                val now = SystemClock.uptimeMillis()
                val dtRaw = now - lastFrameMs
                lastFrameMs = now

                val dt = dtRaw.coerceIn(0L, 50L)

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
