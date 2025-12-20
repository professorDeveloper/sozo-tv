package com.saikou.sozo_tv.components.seasonal

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

internal class SnowRenderer(context: Context) {

    private data class Flake(
        var x: Float,
        var y: Float,
        var r: Float,
        var speed: Float,
        var drift: Float,
        var alpha: Int
    )

    private val density = context.resources.displayMetrics.density
    private val rnd = Random(System.nanoTime())

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
    }

    private var w = 0
    private var h = 0
    private var flakes: Array<Flake> = emptyArray()

    fun onSizeChanged(width: Int, height: Int) {
        w = width
        h = height
        if (w <= 0 || h <= 0) return

        val count = (w / (28f * density)).toInt().coerceIn(60, 160)
        flakes = Array(count) { newFlake(true) }
    }

    fun update(dtMs: Long) {
        val dt = dtMs / 1000f
        for (f in flakes) {
            f.y += f.speed * dt
            f.x += f.drift * dt

            if (f.x < 0) f.x = w.toFloat()
            if (f.x > w) f.x = 0f

            if (f.y > h) {
                val nf = newFlake(false)
                f.x = nf.x
                f.y = nf.y
                f.r = nf.r
                f.speed = nf.speed
                f.drift = nf.drift
                f.alpha = nf.alpha
            }
        }
    }

    fun draw(canvas: Canvas) {
        for (f in flakes) {
            paint.alpha = f.alpha
            canvas.drawCircle(f.x, f.y, f.r, paint)
        }
    }

    private fun newFlake(randomY: Boolean): Flake {
        return Flake(
            x = rnd.nextFloat() * max(1, w),
            y = if (randomY) rnd.nextFloat() * max(1, h) else -10f,
            r = rnd.nextFloat() * (1.4f * density) + (0.6f * density),
            speed = rnd.nextFloat() * (40f * density) + (20f * density),
            drift = (rnd.nextFloat() - 0.5f) * (8f * density),
            alpha = rnd.nextInt(80, 150)
        )
    }
}
