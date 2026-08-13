package com.saikou.sozo_tv.utils

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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

        val numberKeys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
        numberKeys.forEach { number ->
            val keyId = resources.getIdentifier("key_$number", "id", context.packageName)
            findViewById<TextView>(keyId)?.setOnClickListener {
                onKeyClickListener?.invoke(number)
                updateKeyFocus(it)
            }
        }

        findViewById<TextView>(R.id.key_space)?.setOnClickListener {
            onKeyClickListener?.invoke(" ")
            updateKeyFocus(it)
        }

        findViewById<TextView>(R.id.key_backspace)?.setOnClickListener {
            onBackspaceClickListener?.invoke()
            updateKeyFocus(it)
        }

        setupSymbols()
    }

    /**
     * Symbol keys carry no ids — each one is bound from its OWN text, so adding a
     * symbol to `custom_tv_keyboard.xml` needs no change here. The page toggle
     * swaps which grid is visible and moves focus with it, because on a remote a
     * hidden-but-focused key strands the D-pad with nothing highlighted.
     */
    private fun setupSymbols() {
        val letters = findViewById<View>(R.id.letters_page) ?: return
        val symbols = findViewById<View>(R.id.symbols_page) ?: return
        val toggle = findViewById<TextView>(R.id.key_toggle) ?: return

        bindTextKeys(symbols)

        toggle.setOnClickListener {
            val showSymbols = symbols.visibility != View.VISIBLE
            symbols.visibility = if (showSymbols) View.VISIBLE else View.GONE
            letters.visibility = if (showSymbols) View.GONE else View.VISIBLE
            toggle.text = if (showSymbols) LABEL_LETTERS else LABEL_SYMBOLS
            updateKeyFocus(it)
            (if (showSymbols) symbols else letters).let(::focusFirstKey)
        }
    }

    /** Every TextView under [root] emits its own text when clicked. */
    private fun bindTextKeys(root: View) {
        forEachKey(root) { key ->
            key.setOnClickListener {
                onKeyClickListener?.invoke(key.text.toString())
                updateKeyFocus(it)
            }
        }
    }

    private fun focusFirstKey(root: View) {
        var first: TextView? = null
        forEachKey(root) { if (first == null) first = it }
        first?.requestFocus()
    }

    private fun forEachKey(root: View, action: (TextView) -> Unit) {
        when (root) {
            is TextView -> action(root)
            is ViewGroup -> for (i in 0 until root.childCount) forEachKey(root.getChildAt(i), action)
        }
    }

    private fun updateKeyFocus(clickedView: View) {
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

    private companion object {
        // NOT "?123": a value starting with '?' is read by AAPT as a theme
        // attribute reference (?attr/123) and fails resource linking.
        const val LABEL_SYMBOLS = "123#"
        const val LABEL_LETTERS = "ABC"
    }
}
