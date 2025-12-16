package com.saikou.sozo_tv.components

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.withStyledAttributes
import com.saikou.sozo_tv.R

class TvToggleRowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val container: View
    private val titleTv: TextView
    private val subtitleTv: TextView
    private val switchView: Switch
    private val statusDot: View
    private val statusText: TextView

    private var ignoreCallback = false
    private var onCheckedChanged: ((Boolean) -> Unit)? = null

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.view_tv_toggle_row, this, true)

        container = findViewById(R.id.toggle_container)
        titleTv = findViewById(R.id.toggle_title)
        subtitleTv = findViewById(R.id.toggle_subtitle)
        switchView = findViewById(R.id.toggle_switch)
        statusDot = findViewById(R.id.toggle_status_dot)
        statusText = findViewById(R.id.toggle_status_text)
        container.setOnClickListener { switchView.toggle() }
        container.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER -> {
                    switchView.toggle()
                    true
                }
                else -> false
            }
        }

        switchView.setOnCheckedChangeListener { _, checked ->
            if (ignoreCallback) return@setOnCheckedChangeListener
            updateStatus(checked)
            onCheckedChanged?.invoke(checked)
        }

        context.withStyledAttributes(attrs, R.styleable.TvToggleRowView) {
            titleTv.text = getString(R.styleable.TvToggleRowView_titleText) ?: ""
            subtitleTv.text = getString(R.styleable.TvToggleRowView_subtitleText) ?: ""
        }

        updateStatus(switchView.isChecked)
    }

    fun setTitle(text: CharSequence?) {
        titleTv.text = text ?: ""
    }

    fun setSubtitle(text: CharSequence?) {
        subtitleTv.text = text ?: ""
    }

    fun setChecked(checked: Boolean, notify: Boolean = false) {
        ignoreCallback = !notify
        switchView.isChecked = checked
        ignoreCallback = false
        updateStatus(checked)
        if (notify) onCheckedChanged?.invoke(checked)
    }

    fun isChecked(): Boolean = switchView.isChecked

    fun setOnCheckedChangedListener(listener: (Boolean) -> Unit) {
        onCheckedChanged = listener
    }

    private fun updateStatus(enabled: Boolean) {
        if (enabled) {
            statusDot.background = ContextCompat.getDrawable(context, R.drawable.netflix_status_dot_enabled)
            statusText.text = "Enabled"
            statusText.setTextColor(ContextCompat.getColor(context, R.color.netflix_green))
        } else {
            statusDot.background = ContextCompat.getDrawable(context, R.drawable.netflix_status_dot_disabled)
            statusText.text = "Disabled"
            statusText.setTextColor(ContextCompat.getColor(context, R.color.netflix_gray))
        }
    }
}
