package com.saikou.sozo_tv.components.seasonal

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.sin
import kotlin.random.Random

internal class FestiveParticlesRenderer(context: Context) {

    private data class Particle(
        var x: Float,
        var y: Float,
        var r: Float,
        var speedY: Float,
        var phase: Float,
        var color: Int
    )

    private val density = context.resources.displayMetrics.density
    private val rnd = Random(System.nanoTime())

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var w = 0
    private var h = 0
    private var time = 0f

    private var particles: Array<Particle> = emptyArray()

    private val colors = intArrayOf(
        0xFFFFD27F.toInt(),
        0xFF7FDFFF.toInt(),
        0xFFFF9FA3.toInt(),
        0xFFB9FFB0.toInt(),
        0xFFFF7FFF.toInt(),
        0xFFFFFF7F.toInt()
    )

    fun onSizeChanged(width: Int, height: Int) {
        w = width
        h = height
        if (w <= 0 || h <= 0) return

        val count = (w / (180f * density)).toInt().coerceIn(12, 24)
        particles = Array(count) { newParticle() }
    }

    fun update(dtMs: Long) {
        val dt = dtMs / 1000f
        time += dt

        for (p in particles) {
            p.y += p.speedY * dt
            if (p.y > h) {
                p.y = -10f
                p.x = rnd.nextFloat() * w
            }
        }
    }

    fun draw(canvas: Canvas) {
        for (p in particles) {
            val pulse = (sin(time + p.phase) + 1f) * 0.5f
            paint.color = p.color
            paint.alpha = (60 + pulse * 80).toInt()
            canvas.drawCircle(p.x, p.y, p.r, paint)
        }
    }

    private fun newParticle(): Particle {
        return Particle(
            x = rnd.nextFloat() * w,
            y = rnd.nextFloat() * h,
            r = rnd.nextFloat() * (4f * density) + (2f * density),
            speedY = rnd.nextFloat() * (12f * density) + (6f * density),
            phase = rnd.nextFloat() * 6.28f,
            color = colors[rnd.nextInt(colors.size)]
        )
    }
}
