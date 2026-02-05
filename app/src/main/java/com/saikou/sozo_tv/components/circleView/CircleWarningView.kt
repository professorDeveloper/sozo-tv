package com.saikou.sozo_tv.components.circleView

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View

class CircleWarningView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#ef4444") // Red background
        style = Paint.Style.FILL
    }

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#40000000") // Shadow color
        style = Paint.Style.FILL
        maskFilter = BlurMaskFilter(8f, BlurMaskFilter.Blur.NORMAL)
    }

    private val trianglePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        strokeWidth = 3f
    }

    private val exclamationPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        textAlign = Paint.Align.CENTER
        textSize = 24f
        typeface = Typeface.DEFAULT_BOLD
    }

    private val trianglePath = Path()
    private var centerX = 0f
    private var centerY = 0f
    private var radius = 0f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = w / 2f
        centerY = h / 2f
        radius = (minOf(w, h) / 2f) - 8f // Leave space for shadow

        // Create triangle path for warning icon
        createTrianglePath()
    }

    private fun createTrianglePath() {
        trianglePath.reset()
        val triangleSize = radius * 0.5f
        val triangleHeight = triangleSize * 0.866f // Height of equilateral triangle

        // Triangle vertices
        val topX = centerX
        val topY = centerY - triangleHeight / 2f
        val leftX = centerX - triangleSize / 2f
        val leftY = centerY + triangleHeight / 2f
        val rightX = centerX + triangleSize / 2f
        val rightY = centerY + triangleHeight / 2f

        trianglePath.moveTo(topX, topY)
        trianglePath.lineTo(leftX, leftY)
        trianglePath.lineTo(rightX, rightY)
        trianglePath.close()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw shadow
        canvas.drawCircle(centerX + 2f, centerY + 2f, radius, shadowPaint)

        // Draw circle background
        canvas.drawCircle(centerX, centerY, radius, circlePaint)

        // Draw warning triangle outline
        canvas.drawPath(trianglePath, trianglePaint)

        // Draw exclamation mark inside triangle
        val exclamationY = centerY + (exclamationPaint.textSize / 3f)
        canvas.drawText("!", centerX, exclamationY, exclamationPaint)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredSize = 64.dpToPx() // Default 64dp size

        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        val width = when (widthMode) {
            MeasureSpec.EXACTLY -> widthSize
            MeasureSpec.AT_MOST -> minOf(desiredSize, widthSize)
            else -> desiredSize
        }

        val height = when (heightMode) {
            MeasureSpec.EXACTLY -> heightSize
            MeasureSpec.AT_MOST -> minOf(desiredSize, heightSize)
            else -> desiredSize
        }

        setMeasuredDimension(width, height)
    }

    private fun Int.dpToPx(): Int {
        return (this * context.resources.displayMetrics.density).toInt()
    }

    // Customization methods
    fun setCircleColor(color: Int) {
        circlePaint.color = color
        invalidate()
    }

    fun setIconColor(color: Int) {
        trianglePaint.color = color
        exclamationPaint.color = color
        invalidate()
    }

    fun setSize(sizeDp: Int) {
        val params = layoutParams
        val sizePx = sizeDp.dpToPx()
        params.width = sizePx
        params.height = sizePx
        layoutParams = params
    }
}
