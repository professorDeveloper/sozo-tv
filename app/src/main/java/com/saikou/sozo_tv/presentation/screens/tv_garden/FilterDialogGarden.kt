package com.saikou.sozo_tv.presentation.screens.tv_garden

import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.databinding.FilterDialogGardenBinding
import com.saikou.sozo_tv.domain.model.MySpinnerItem
import com.saikou.sozo_tv.presentation.screens.category.CustomSpinnerAdapter
import com.saikou.sozo_tv.utils.LocalData

class FilterDialogGarden : DialogFragment() {

    private var _binding: FilterDialogGardenBinding? = null
    private val binding get() = _binding!!
    var onFiltersApplied: ((String?) -> Unit)? = null
    private var selectedSort: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FilterDialogGardenBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupDialogWindow()
        binding.categoryContainer.title.text = "By Category"
        binding.countryContainer.title.text = "By Country"


        val selectedColor =
            ContextCompat.getColor(requireContext(), R.color.selected_category_color)
        val defaultTextColor =
            ContextCompat.getColor(requireContext(), R.color.color_item_tv_category_tv)

        binding.close.setOnClickListener {
            dismiss()
        }

        selectedSort = arguments?.getString("selectedSort")?:"By Country"
        if (selectedSort != null) {
            binding.sliderHint.hint = "Selected Sort: $selectedSort"
        }

        if (
            selectedSort == "By Category"
        ) {
            binding.categoryContainer.root.setBackgroundResource(R.drawable.background_item_tv_category_tv_selected)
            binding.countryContainer.root.setBackgroundResource(R.drawable.background_item_tv_category_tv)
            binding.countryContainer.title.setTextColor(defaultTextColor)
            binding.categoryContainer.title.setTextColor(selectedColor)
        } else {

            binding.countryContainer.root.setBackgroundResource(R.drawable.background_item_tv_category_tv_selected)
            binding.categoryContainer.root.setBackgroundResource(R.drawable.background_item_tv_category_tv)
            binding.categoryContainer.title.setTextColor(defaultTextColor)
            binding.countryContainer.title.setTextColor(selectedColor)
        }
        binding.countryContainer.root.setOnClickListener {
            selectedSort = "By Country"
            binding.sliderHint.hint = "Selected Sort: $selectedSort"
            binding.countryContainer.root.setBackgroundResource(R.drawable.background_item_tv_category_tv_selected)
            binding.categoryContainer.root.setBackgroundResource(R.drawable.background_item_tv_category_tv)
            binding.categoryContainer.title.setTextColor(defaultTextColor)
            binding.countryContainer.title.setTextColor(selectedColor)
        }

        binding.categoryContainer.root.setOnClickListener {
            selectedSort = "By Category"
            binding.sliderHint.hint = "Selected Sort: $selectedSort"
            binding.countryContainer.root.setBackgroundResource(R.drawable.background_item_tv_category_tv)
            binding.categoryContainer.root.setBackgroundResource(R.drawable.background_item_tv_category_tv_selected)
            binding.categoryContainer.title.setTextColor(selectedColor)
            binding.countryContainer.title.setTextColor(defaultTextColor)
        }
        binding.applyFilter.setOnClickListener {
            onFiltersApplied?.invoke(selectedSort)
            dismiss()
        }
    }

    private fun setupDialogWindow() {
        dialog?.window?.apply {
            setWindowAnimations(R.style.DialogAnimation)
            setBackgroundDrawable(ColorDrawable(0))
            setFlags(
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            )
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(selectedSort: String?): FilterDialogGarden {
            val dialog = FilterDialogGarden()
            val args = Bundle()
            args.putString("selectedSort", selectedSort)
            dialog.arguments = args
            return dialog
        }
    }
}
