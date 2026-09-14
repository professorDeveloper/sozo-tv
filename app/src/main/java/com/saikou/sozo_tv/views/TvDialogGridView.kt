package com.saikou.sozo_tv.views

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import androidx.leanback.widget.VerticalGridView

/** A bounded dialog viewport with room for the shared 1.04x row focus effect. */
class TvDialogGridView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : VerticalGridView(context, attrs, defStyleAttr) {
    init {
        val density = resources.displayMetrics.density
        setPadding((16 * density).toInt(), (8 * density).toInt(),
            (16 * density).toInt(), (8 * density).toInt())
        clipChildren = false
        clipToPadding = false
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val limit = (resources.displayMetrics.heightPixels * 0.5f).toInt()
        val size = if (MeasureSpec.getMode(heightSpec) == MeasureSpec.UNSPECIFIED) limit
            else minOf(MeasureSpec.getSize(heightSpec), limit)
        super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(size, MeasureSpec.AT_MOST))
    }

    override fun dispatchDraw(canvas: Canvas) {
        // Allow focus into padding, but keep scrolled rows out of the dialog header.
        val save = canvas.save()
        canvas.clipRect(0, 0, width, height)
        super.dispatchDraw(canvas)
        canvas.restoreToCount(save)
    }
}
