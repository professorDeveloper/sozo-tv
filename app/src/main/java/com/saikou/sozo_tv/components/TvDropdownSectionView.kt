package com.saikou.sozo_tv.components

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
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

    /**
     * The expand/collapse animation currently in flight, so the next toggle can stop
     * it. Neither animator used to be held, so two of them could be writing
     * `contentContainer.layoutParams.height` at once — and the collapse listener set
     * `visibility = GONE` unconditionally at the end, even when an expand had started
     * since. The section then reported itself expanded while showing nothing, and took
     * two more presses to come back.
     */
    private var runningAnim: ValueAnimator? = null

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
            // A held OK button fires ACTION_DOWN over and over. Without this the
            // section toggled on every repeat and each toggle started a fresh
            // animator, which is how expand and collapse ended up racing each other
            // over the same layoutParams.height. Repeats are consumed, not acted on.
            if (event.repeatCount != 0) {
                return@setOnKeyListener keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                        keyCode == KeyEvent.KEYCODE_ENTER
            }
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER -> {
                    toggle(true)
                    true
                }

                else -> false
            }
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

        runningAnim?.cancel()
        runningAnim = null

        if (!animate) {
            // Reset the height too: an earlier animated expand leaves a pixel value
            // behind, and jumping straight to VISIBLE with that stale height showed
            // the section at whatever size it happened to be last time.
            contentContainer.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
            contentContainer.alpha = 1f
            contentContainer.visibility = if (_expanded) View.VISIBLE else View.GONE
            contentContainer.requestLayout()
            chevron.rotation = if (_expanded) 180f else 0f
            return
        }

        if (_expanded) expandAnim() else collapseAnim()
    }

    /**
     * How tall the content wants to be at the width it will actually get.
     *
     * This used to measure with `makeMeasureSpec(width, EXACTLY)` against THIS view's
     * width, which is 0 until first layout — so a section expanded before the page had
     * settled measured its content at zero width and animated toward a target that had
     * nothing to do with the real one. UNSPECIFIED is the honest fallback while the
     * width is still unknown.
     */
    private fun measureContentHeight(): Int {
        val lp = contentContainer.layoutParams as? MarginLayoutParams
        val available =
            width - paddingStart - paddingEnd - (lp?.leftMargin ?: 0) - (lp?.rightMargin ?: 0)
        val widthSpec = if (available > 0) {
            MeasureSpec.makeMeasureSpec(available, MeasureSpec.EXACTLY)
        } else {
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        }
        contentContainer.measure(
            widthSpec,
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
        )
        return contentContainer.measuredHeight.coerceAtLeast(0)
    }

    private fun expandAnim() {
        contentContainer.visibility = View.VISIBLE
        contentContainer.alpha = 0f

        val target = measureContentHeight()
        contentContainer.layoutParams.height = 0
        contentContainer.requestLayout()

        runningAnim = ValueAnimator.ofInt(0, target).apply {
            duration = animDuration
            interpolator = DecelerateInterpolator()
            addUpdateListener { a ->
                val h = a.animatedValue as Int
                contentContainer.layoutParams.height = h
                contentContainer.alpha =
                    if (target == 0) 1f else (h.toFloat() / target).coerceIn(0f, 1f)
                contentContainer.requestLayout()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    runningAnim = null
                    // onAnimationEnd fires on cancel too, so only finish the expand if
                    // we are still expanded.
                    if (!_expanded) return
                    // Hand the height back to WRAP_CONTENT. Leaving the measured pixel
                    // value in place froze the section at the size it had when it first
                    // opened, so anything that grew the content afterwards — a larger
                    // system font, a toggle subtitle wrapping to a second line — was
                    // clipped while still being focusable, and the D-pad moved onto a
                    // row the user could not see.
                    contentContainer.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                    contentContainer.alpha = 1f
                    contentContainer.requestLayout()
                }
            })
            start()
        }

        chevron.animate().rotation(180f).setDuration(animDuration).start()
    }

    private fun collapseAnim() {
        // Hiding a view that holds focus makes Android clear it and hand the D-pad to
        // the first focusable in the WINDOW — on the profile screen that is the
        // navigation rail, so closing a section threw the user out of the page. The
        // header is where they just pressed OK; that is where focus belongs.
        if (contentContainer.hasFocus()) header.requestFocus()

        val initial = contentContainer.height.coerceAtLeast(0)

        runningAnim = ValueAnimator.ofInt(initial, 0).apply {
            duration = animDuration
            interpolator = DecelerateInterpolator()
            addUpdateListener { a ->
                val h = a.animatedValue as Int
                contentContainer.layoutParams.height = h
                contentContainer.alpha =
                    if (initial == 0) 0f else (h.toFloat() / initial).coerceIn(0f, 1f)
                contentContainer.requestLayout()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    runningAnim = null
                    // Cancel routes through here as well, and the old body hid the
                    // content unconditionally — so a second press during the collapse
                    // left `_expanded == true` over a GONE container: open according to
                    // the section, empty according to the screen.
                    if (_expanded) return
                    contentContainer.visibility = View.GONE
                    contentContainer.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                    contentContainer.alpha = 1f
                    contentContainer.requestLayout()
                }
            })
            start()
        }

        chevron.animate().rotation(0f).setDuration(animDuration).start()
    }
}
