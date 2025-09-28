package com.saikou.sozo_tv.utils

import android.animation.ObjectAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.databinding.ViewNetflixFilterSelectorBinding

class NetflixFilterSelector @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val binding: ViewNetflixFilterSelectorBinding =
        ViewNetflixFilterSelectorBinding.inflate(LayoutInflater.from(context), this, true)
    private var onFilterTypeChangedListener: ((FilterType) -> Unit)? = null
    private var currentFilterType = FilterType.COUNTRY
    
    enum class FilterType {
        COUNTRY, CATEGORY
    }

    init {
        setupView()
        setupClickListeners()
        setupFocusHandling()
        
        attrs?.let { processAttributes(it) }
        
        // Set initial state
        setFilterType(FilterType.COUNTRY)
    }
    
    private fun processAttributes(attrs: AttributeSet) {
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.NetflixFilterSelector)
        
        try {
            val defaultFilterType = typedArray.getInt(R.styleable.NetflixFilterSelector_defaultFilterType, 0)
            val buttonTextSize = typedArray.getDimension(R.styleable.NetflixFilterSelector_buttonTextSize, -1f)
            val buttonTextColor = typedArray.getColor(R.styleable.NetflixFilterSelector_buttonTextColor, -1)
            val selectedButtonColor = typedArray.getColor(R.styleable.NetflixFilterSelector_selectedButtonColor, -1)
            
            // Apply default filter type
            currentFilterType = if (defaultFilterType == 1) FilterType.CATEGORY else FilterType.COUNTRY
            
            // Apply text size if specified
            if (buttonTextSize > 0) {
                binding.btnFilterCountry.textSize = buttonTextSize
                binding.btnFilterCategory.textSize = buttonTextSize
            }
            
            // Apply text color if specified
            if (buttonTextColor != -1) {
                binding.btnFilterCountry.setTextColor(buttonTextColor)
                binding.btnFilterCategory.setTextColor(buttonTextColor)
            }
            
        } finally {
            typedArray.recycle()
        }
    }

    private fun setupView() {
        orientation = HORIZONTAL
        
        // Make buttons focusable for TV navigation
        binding.btnFilterCountry.isFocusable = true
        binding.btnFilterCategory.isFocusable = true
    }

    private fun setupClickListeners() {
        binding.btnFilterCountry.setOnClickListener {
            setFilterType(FilterType.COUNTRY)
        }
        
        binding.btnFilterCategory.setOnClickListener {
            setFilterType(FilterType.CATEGORY)
        }
    }

    private fun setupFocusHandling() {
        binding.btnFilterCountry.setOnFocusChangeListener { view, hasFocus ->
            animateButtonFocus(view, hasFocus)
        }
        
        binding.btnFilterCategory.setOnFocusChangeListener { view, hasFocus ->
            animateButtonFocus(view, hasFocus)
        }
    }

    fun setFilterType(filterType: FilterType) {
        if (currentFilterType != filterType) {
            currentFilterType = filterType
            updateButtonStates()
            onFilterTypeChangedListener?.invoke(filterType)
        }
    }

    fun setOnFilterTypeChangedListener(listener: (FilterType) -> Unit) {
        this.onFilterTypeChangedListener = listener
    }

    private fun updateButtonStates() {
        val countrySelected = (currentFilterType == FilterType.COUNTRY)
        val categorySelected = (currentFilterType == FilterType.CATEGORY)
        
        // Update button selection states
        binding.btnFilterCountry.isSelected = countrySelected
        binding.btnFilterCategory.isSelected = categorySelected
        
        // Update button colors
        updateButtonAppearance(binding.btnFilterCountry, countrySelected)
        updateButtonAppearance(binding.btnFilterCategory, categorySelected)
        
        // Animate selection indicator
        animateSelectionIndicator()
    }

    private fun updateButtonAppearance(button: View, isSelected: Boolean) {
        val backgroundColor = if (isSelected) {
            ContextCompat.getColor(context, R.color.netflix_red)
        } else {
            ContextCompat.getColor(context, R.color.netflix_section)
        }
        
        val textColor = if (isSelected) {
            ContextCompat.getColor(context, R.color.netflix_white)
        } else {
            ContextCompat.getColor(context, R.color.netflix_gray)
        }
        
        button.setBackgroundColor(backgroundColor)
        
        // Update text color if it's a TextView/Button
        if (button is android.widget.TextView) {
            button.setTextColor(textColor)
        }
    }

    private fun animateButtonFocus(view: View, hasFocus: Boolean) {
        val targetScale = if (hasFocus) 1.05f else 1.0f
        
        ObjectAnimator.ofFloat(view, "scaleX", view.scaleX, targetScale).apply {
            duration = 150
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
        
        ObjectAnimator.ofFloat(view, "scaleY", view.scaleY, targetScale).apply {
            duration = 150
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun animateSelectionIndicator() {
        // Create a smooth transition animation for the selection indicator
        val targetX = if (currentFilterType == FilterType.COUNTRY) {
            binding.btnFilterCountry.x
        } else {
            binding.btnFilterCategory.x
        }
        
        // You could add a selection indicator view and animate its position here
        // For now, we'll rely on the button background changes
    }
}
