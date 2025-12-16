package com.saikou.sozo_tv.components
import android.animation.AnimatorInflater
import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.KeyEvent
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.withStyledAttributes
import com.saikou.sozo_tv.R

class TvStepperView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val btnMinus: TextView
    private val btnPlus: TextView
    private val txtValue: TextView

    private var minValue = 12
    private var maxValue = 28
    private var step = 1
    private var unit: String = "sp"

    private var _value = 16
    val value: Int get() = _value

    private var listener: ((Int) -> Unit)? = null

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL

        isFocusable = true
        isFocusableInTouchMode = true
        isClickable = true
        setPadding(dp(6), dp(4), dp(6), dp(4))
        setBackgroundResource(R.drawable.tv_chip_selector)

        inflate(context, R.layout.view_tv_stepper, this)
        btnMinus = findViewById(R.id.btn_minus)
        btnPlus = findViewById(R.id.btn_plus)
        txtValue = findViewById(R.id.txt_value)

        context.withStyledAttributes(attrs, R.styleable.TvStepperView) {
            minValue = getInt(R.styleable.TvStepperView_minValue, minValue)
            maxValue = getInt(R.styleable.TvStepperView_maxValue, maxValue)
            step = getInt(R.styleable.TvStepperView_step, step).coerceAtLeast(1)
            _value = getInt(R.styleable.TvStepperView_initialValue, _value)
            unit = getString(R.styleable.TvStepperView_unit) ?: unit
        }

        _value = _value.coerceIn(minValue, maxValue)
        render()

        // Optional: click to increase (Netflix-like)
        setOnClickListener { increment() }
    }

    fun setOnValueChangedListener(l: (Int) -> Unit) {
        listener = l
        l(_value)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> { decrement(); true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { increment(); true }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    private fun increment() {
        setValue(_value + step)
    }

    private fun decrement() {
        setValue(_value - step)
    }

    fun setValue(v: Int) {
        val newValue = v.coerceIn(minValue, maxValue)
        if (newValue == _value) return
        _value = newValue
        render()
        listener?.invoke(_value)
    }

    private fun render() {
        txtValue.text = "$_value$unit"
        // minus/plus only visual; you can gray out at edges if you want
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}