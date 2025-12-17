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
        var alpha: Int,
    )

    private val density = context.resources.displayMetrics.density
    private val rnd = Random(System.nanoTime())

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private var widthPx = 0
    private var heightPx = 0

    private var flakes: Array<Flake> = emptyArray()

    fun onSizeChanged(w: Int, h: Int) {
        widthPx = w
        heightPx = h
        if (w <= 0 || h <= 0) return

        val targetCount = (w / (18f * density)).toInt().coerceIn(45, 140)

        flakes = Array(targetCount) {
            newFlake(randomY = true)
        }
    }

    fun update(dtMs: Long) {
        if (widthPx <= 0 || heightPx <= 0) return
        val dt = dtMs / 1000f

        for (f in flakes) {
            f.y += f.speed * dt
            f.x += f.drift * dt

            if (f.x < -f.r) f.x = widthPx + f.r
            if (f.x > widthPx + f.r) f.x = -f.r

            if (f.y > heightPx + f.r) {
                val nf = newFlake(randomY = false)
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
        val r = rnd.nextFloat() * (2.4f * density) + (0.9f * density)
        val speed = rnd.nextFloat() * (90f * density) + (35f * density)
        val drift = (rnd.nextFloat() - 0.5f) * (22f * density)
        val alpha = rnd.nextInt(45, 110)

        val x = rnd.nextFloat() * max(1, widthPx)
        val y = if (randomY) {
            rnd.nextFloat() * max(1, heightPx)
        } else {
            -abs(rnd.nextFloat() * heightPx * 0.15f)
        }

        return Flake(x = x, y = y, r = r, speed = speed, drift = drift, alpha = alpha)
    }
}
