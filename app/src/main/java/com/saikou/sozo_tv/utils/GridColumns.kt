package com.saikou.sozo_tv.utils

import android.R.attr.numColumns
import android.view.View
import androidx.leanback.widget.VerticalGridView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Sets a grid's column count from the width it actually got.
 *
 * A hardcoded count is wrong on every panel but the one it was written for. Too
 * many and each cell ends up narrower than the fixed-width item, so rows sit on
 * top of each other and the last column is clipped; too few and half the screen
 * is empty. TV widths run 853dp to 1280dp, which is the difference between
 * three columns and six.
 *
 * [itemWidthDp] is the item's own width plus its horizontal margins.
 */
fun VerticalGridView.autoFitColumns(itemWidthDp: Int, min: Int = 2, max: Int = 8) {
    fun apply(measured: Int) {
        if (measured <= 0) return
        val itemPx = itemWidthDp * resources.displayMetrics.density
        val usable = measured - paddingLeft - paddingRight
        val columns = (usable / itemPx).toInt().coerceIn(min, max)
        if (numColumns != columns) setNumColumns(columns)
    }

    if (width > 0) apply(width)
    addOnLayoutChangeListener { v: View, l, _, r, _, ol, _, or, _ ->
        if (r - l != or - ol) apply(v.width)
    }
}

/** Same, for a plain RecyclerView driven by a GridLayoutManager. */
fun RecyclerView.autoFitSpans(itemWidthDp: Int, min: Int = 2, max: Int = 8) {
    fun apply(measured: Int) {
        val lm = layoutManager as? GridLayoutManager ?: return
        if (measured <= 0) return
        val itemPx = itemWidthDp * resources.displayMetrics.density
        val usable = measured - paddingLeft - paddingRight
        val columns = (usable / itemPx).toInt().coerceIn(min, max)
        if (lm.spanCount != columns) lm.spanCount = columns
    }

    if (width > 0) apply(width)
    addOnLayoutChangeListener { v: View, l, _, r, _, ol, _, or, _ ->
        if (r - l != or - ol) apply(v.width)
    }
}
