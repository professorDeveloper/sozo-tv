package com.saikou.sozo_tv.views

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout

/** Keeps wrap-content TV dialogs inside the display's safe area. */
class TvDialogLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val metrics = resources.displayMetrics
        val inset = (48 * metrics.density).toInt()
        fun bounded(spec: Int, limit: Int): Int {
            val size = if (MeasureSpec.getMode(spec) == MeasureSpec.UNSPECIFIED) limit
                else minOf(MeasureSpec.getSize(spec), limit)
            val mode = if (MeasureSpec.getMode(spec) == MeasureSpec.EXACTLY)
                MeasureSpec.EXACTLY else MeasureSpec.AT_MOST
            return MeasureSpec.makeMeasureSpec(size.coerceAtLeast(0), mode)
        }
        super.onMeasure(bounded(widthMeasureSpec, metrics.widthPixels - inset),
            bounded(heightMeasureSpec, metrics.heightPixels - inset))
    }
}
