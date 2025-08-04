package com.saikou.sozo_tv.components.smooth

import android.content.Context
import android.util.AttributeSet
import androidx.leanback.widget.VerticalGridView

class SlowVerticalGridView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : VerticalGridView(context, attrs) {

    override fun smoothScrollBy(dx: Int, dy: Int) {
        super.smoothScrollBy(dx / 2, dy / 2) // yoki boshqa qiymat
    }
}
