package com.saikou.sozo_tv.utils

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.saikou.sozo_tv.R

class CustomTVKeyboard @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private var onKeyClickListener: ((String) -> Unit)? = null
    private var onBackspaceClickListener: (() -> Unit)? = null
    private var onClearClickListener: (() -> Unit)? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.custom_tv_keyboard, this, true)
        setupKeyboard()
    }

    private fun setupKeyboard() {
        // Setup letter keys
        val letterKeys = listOf(
            "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l",
            "m", "n", "o", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y", "z"
        )

        letterKeys.forEach { letter ->
            val keyId = resources.getIdentifier("key_$letter", "id", context.packageName)
            findViewById<TextView>(keyId)?.setOnClickListener {
                onKeyClickListener?.invoke(letter)
                updateKeyFocus(it)
            }
        }

        // Setup number keys
        val numberKeys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
        numberKeys.forEach { number ->
            val keyId = resources.getIdentifier("key_$number", "id", context.packageName)
            findViewById<TextView>(keyId)?.setOnClickListener {
                onKeyClickListener?.invoke(number)
                updateKeyFocus(it)
            }
        }

        // Setup control keys
        findViewById<TextView>(R.id.key_backspace)?.setOnClickListener {
            onBackspaceClickListener?.invoke()
            updateKeyFocus(it)
        }

        findViewById<TextView>(R.id.key_clear)?.setOnClickListener {
            onClearClickListener?.invoke()
            updateKeyFocus(it)
        }
    }

    private fun updateKeyFocus(clickedView: View) {
        // Add visual feedback for key press
        clickedView.animate()
            .scaleX(0.9f)
            .scaleY(0.9f)
            .setDuration(100)
            .withEndAction {
                clickedView.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(100)
                    .start()
            }
            .start()
    }

    fun setOnKeyClickListener(listener: (String) -> Unit) {
        onKeyClickListener = listener
    }

    fun setOnBackspaceClickListener(listener: () -> Unit) {
        onBackspaceClickListener = listener
    }

    fun setOnClearClickListener(listener: () -> Unit) {
        onClearClickListener = listener
    }
}
