package com.saikou.sozo_tv.components

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.os.SystemClock
import android.util.AttributeSet
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.components.seasonal.FestiveParticlesRenderer
import com.saikou.sozo_tv.components.seasonal.SnowRenderer
import com.saikou.sozo_tv.data.model.SeasonalTheme

class SeasonalBackgroundLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private var snow: SnowRenderer? = null
    private var festive: FestiveParticlesRenderer? = null

    private var animator: ValueAnimator? = null
    private var lastFrame = 0L

    private val bgColor: Int by lazy {
        ContextCompat.getColor(context, R.color.netflix_background_primary)
    }

    init {
        clipChildren = false
        clipToPadding = false
    }

    fun setTheme(theme: SeasonalTheme) {
        if (theme == SeasonalTheme.WINTER) {
            if (snow == null) snow = SnowRenderer(context)
        } else {
            snow = null
            festive = null
        }
        startAnim()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        snow?.onSizeChanged(w, h)
        festive?.onSizeChanged(w, h)
    }

    override fun dispatchDraw(canvas: Canvas) {
        canvas.drawColor(bgColor)
        festive?.draw(canvas)
        snow?.draw(canvas)

        super.dispatchDraw(canvas)
    }

    private fun startAnim() {
        if (animator != null) return

        lastFrame = SystemClock.uptimeMillis()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1000L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                val now = SystemClock.uptimeMillis()
                val dt = (now - lastFrame).coerceIn(0, 40)
                lastFrame = now

                snow?.update(dt)
                festive?.update(dt)
                postInvalidateOnAnimation()
            }
            start()
        }
    }
}
