package com.saikou.sozo_tv.components

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import android.widget.ScrollView

class FocusableScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ScrollView(context, attrs, defStyleAttr) {

    override fun requestChildFocus(child: View?, focused: View?) {
        if (focused != null) {
            val rect = Rect()
            focused.getDrawingRect(rect)
            offsetDescendantRectToMyCoords(focused, rect)
            
            val scrollDelta = computeScrollDeltaToGetChildRectOnScreen(rect)
            if (scrollDelta != 0) {
                smoothScrollBy(0, scrollDelta)
            }
        }
        super.requestChildFocus(child, focused)
    }

    override fun computeScrollDeltaToGetChildRectOnScreen(rect: Rect): Int {
        if (childCount == 0) return 0

        val height = height
        var screenTop = scrollY
        var screenBottom = screenTop + height

        val fadingEdge = verticalFadingEdgeLength
        if (rect.top > 0) screenTop += fadingEdge
        if (rect.bottom < getChildAt(0).height) screenBottom -= fadingEdge

        return when {
            rect.bottom > screenBottom && rect.top > screenTop -> {
                if (rect.height() > height) rect.top - screenTop else rect.bottom - screenBottom
            }
            rect.top < screenTop && rect.bottom < screenBottom -> {
                if (rect.height() > height) rect.bottom - screenBottom else rect.top - screenTop
            }
            else -> 0
        }
    }
}