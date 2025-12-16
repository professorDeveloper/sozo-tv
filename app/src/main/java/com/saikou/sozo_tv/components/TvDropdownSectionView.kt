package com.saikou.sozo_tv.components

import android.animation.Animator
import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.withStyledAttributes
import com.saikou.sozo_tv.R

class TvDropdownSectionView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val header: View
    private val titleTv: TextView
    private val subtitleTv: TextView
    private val summaryTv: TextView
    private val badgeTv: TextView
    private val chevron: ImageView
    private val contentContainer: LinearLayout

    private var animDuration = 220L
    private var _expanded = false
    val isExpanded: Boolean get() = _expanded

    private var onExpandedChanged: ((Boolean) -> Unit)? = null

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.view_tv_dropdown_section, this, true)

        header = findViewById(R.id.dropdown_header)
        titleTv = findViewById(R.id.dropdown_title)
        subtitleTv = findViewById(R.id.dropdown_subtitle)
        summaryTv = findViewById(R.id.dropdown_summary)
        badgeTv = findViewById(R.id.dropdown_badge)
        chevron = findViewById(R.id.dropdown_chevron)
        contentContainer = findViewById(R.id.dropdown_content)

        header.setOnClickListener { toggle(true) }
        header.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER -> {
                    toggle(true)
                    true
                }
                else -> false
            }
        }

        // Netflix-like fokus zoom (silliq)
        header.setOnFocusChangeListener { v, hasFocus ->
            v.animate()
                .scaleX(if (hasFocus) 1.03f else 1f)
                .scaleY(if (hasFocus) 1.03f else 1f)
                .setDuration(120)
                .start()
        }

        context.withStyledAttributes(attrs, R.styleable.TvDropdownSectionView) {
            titleTv.text = getString(R.styleable.TvDropdownSectionView_sectionTitle) ?: ""
            subtitleTv.text = getString(R.styleable.TvDropdownSectionView_sectionSubtitle) ?: ""
            val expanded = getBoolean(R.styleable.TvDropdownSectionView_expanded, false)
            setExpanded(expanded, animate = false)
        }
    }

    override fun onFinishInflate() {
        super.onFinishInflate()

        // XML'da yozilgan ichki child’larni dropdown_content ichiga ko‘chiramiz
        val toMove = mutableListOf<View>()
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child !== header && child !== contentContainer) {
                toMove.add(child)
            }
        }
        toMove.forEach { child ->
            removeView(child)
            contentContainer.addView(child)
        }
    }

    fun setOnExpandedChangeListener(listener: (Boolean) -> Unit) {
        onExpandedChanged = listener
    }

    fun setSummary(text: CharSequence?) {
        if (text.isNullOrBlank()) {
            summaryTv.visibility = View.GONE
        } else {
            summaryTv.visibility = View.VISIBLE
            summaryTv.text = text
        }
    }

    fun setBadge(text: CharSequence?) {
        if (text.isNullOrBlank()) {
            badgeTv.visibility = View.GONE
        } else {
            badgeTv.visibility = View.VISIBLE
            badgeTv.text = text
        }
    }

    fun toggle(animate: Boolean = true) {
        setExpanded(!_expanded, animate)
    }

    fun setExpanded(expand: Boolean, animate: Boolean = true) {
        if (expand == _expanded) return
        _expanded = expand
        onExpandedChanged?.invoke(_expanded)

        // Header background (top-corner vs full-corner) uchun
        header.isSelected = _expanded

        if (!animate) {
            contentContainer.visibility = if (_expanded) View.VISIBLE else View.GONE
            chevron.rotation = if (_expanded) 180f else 0f
            return
        }

        if (_expanded) expandAnim() else collapseAnim()
    }

    private fun expandAnim() {
        contentContainer.visibility = View.VISIBLE
        contentContainer.alpha = 0f

        contentContainer.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        )
        val target = contentContainer.measuredHeight.coerceAtLeast(0)
        contentContainer.layoutParams.height = 0
        contentContainer.requestLayout()

        ValueAnimator.ofInt(0, target).apply {
            duration = animDuration
            interpolator = DecelerateInterpolator()
            addUpdateListener { a ->
                val h = a.animatedValue as Int
                contentContainer.layoutParams.height = h
                contentContainer.alpha =
                    if (target == 0) 1f else (h.toFloat() / target).coerceIn(0f, 1f)
                contentContainer.requestLayout()
            }
            start()
        }

        chevron.animate().rotation(180f).setDuration(animDuration).start()
    }

    private fun collapseAnim() {
        val initial = contentContainer.height.coerceAtLeast(0)

        ValueAnimator.ofInt(initial, 0).apply {
            duration = animDuration
            interpolator = DecelerateInterpolator()
            addUpdateListener { a ->
                val h = a.animatedValue as Int
                contentContainer.layoutParams.height = h
                contentContainer.alpha =
                    if (initial == 0) 0f else (h.toFloat() / initial).coerceIn(0f, 1f)
                contentContainer.requestLayout()
            }
            addListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(animation: Animator) {}
                override fun onAnimationRepeat(animation: Animator) {}
                override fun onAnimationCancel(animation: Animator) = end()
                override fun onAnimationEnd(animation: Animator) = end()

                private fun end() {
                    contentContainer.visibility = View.GONE
                    contentContainer.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                    contentContainer.alpha = 1f
                }
            })
            start()
        }

        chevron.animate().rotation(0f).setDuration(animDuration).start()
    }
}
