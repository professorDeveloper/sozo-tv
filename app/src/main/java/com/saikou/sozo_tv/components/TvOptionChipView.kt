package com.saikou.sozo_tv.components

import android.animation.AnimatorInflater
import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import com.saikou.sozo_tv.R

class TvOptionChipView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        isClickable = true

        gravity = Gravity.CENTER
        setTextColor(ContextCompat.getColor(context, R.color.netflix_text_primary))
        setBackgroundResource(R.drawable.tv_chip_selector)

        minHeight = dp(56)
        setPadding(dp(16), 0, dp(16), 0)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
