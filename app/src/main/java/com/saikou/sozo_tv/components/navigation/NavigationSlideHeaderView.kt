package com.saikou.sozo_tv.components.navigation

import android.content.Context
import android.util.AttributeSet
import androidx.constraintlayout.widget.ConstraintLayout

class NavigationSlideHeaderView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private var openListener: (() -> Unit)? = null
    private var closeListener: (() -> Unit)? = null

    /** Starts true because the header's own layout is drawn expanded. */
    private var opened = true


    /**
     * A listener registered after the state already changed is applied at once.
     *
     * The rail collapses while the menu is being built, long before the host has anything to
     * register, so the first close() used to fire into a null listener: the items collapsed and the
     * header stayed expanded, leaving the rail half open until it was focused once.
     */
    fun setOnOpenListener(onOpen: () -> Unit) {
        openListener = onOpen
        if (opened) onOpen()
    }

    fun setOnCloseListener(onClose: () -> Unit) {
        closeListener = onClose
        if (!opened) onClose()
    }


    fun open() {
        opened = true
        isFocusable = true
        isFocusableInTouchMode = true

        openListener?.invoke()
    }

    fun close() {
        opened = false
        isFocusable = false
        isFocusableInTouchMode = false

        closeListener?.invoke()
    }
}