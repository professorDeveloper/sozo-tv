package com.saikou.sozo_tv.presentation.screens.tv_garden

import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.fragment.app.DialogFragment
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.utils.requestInitialFocus
import com.saikou.sozo_tv.databinding.FilterDialogGardenBinding

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
        binding.customContainer.title.text = "Custom List"

        binding.close.setOnClickListener { dismiss() }

        selectedSort = arguments?.getString("selectedSort") ?: "By Country"
        if (selectedSort != null) {
            binding.sliderHint.hint = "Selected Sort: $selectedSort"
        }

        // 🔹 Avvalgi tanlovni rang bilan ajratib ko‘rsatamiz
        updateSelectionUI(selectedSort)

        selectedRow().requestInitialFocus()

        // 🔹 Eventlar
        binding.countryContainer.root.setOnClickListener {
            selectedSort = "By Country"
            binding.sliderHint.hint = "Selected Sort: $selectedSort"
            updateSelectionUI(selectedSort)
        }

        binding.categoryContainer.root.setOnClickListener {
            selectedSort = "By Category"
            binding.sliderHint.hint = "Selected Sort: $selectedSort"
            updateSelectionUI(selectedSort)
        }

        binding.customContainer.root.setOnClickListener {
            selectedSort = "Custom List"
            binding.sliderHint.hint = "Selected Sort: $selectedSort"
            updateSelectionUI(selectedSort)
        }

        binding.applyFilter.setOnClickListener {
            onFiltersApplied?.invoke(selectedSort)
            dismiss()
        }
    }

    private fun selectedRow(): View = when (selectedSort) {
        "By Category" -> binding.categoryContainer.root
        "Custom List" -> binding.customContainer.root
        else -> binding.countryContainer.root
    }

    private fun updateSelectionUI(selected: String?) {
        val rows = listOf(
            "By Country" to binding.countryContainer.root,
            "By Category" to binding.categoryContainer.root,
            "Custom List" to binding.customContainer.root,
        )
        rows.forEach { (key, row) ->
            val isSelected = key == selected
            row.isSelected = isSelected
            row.setBackgroundResource(
                if (isSelected) R.drawable.background_item_tv_category_tv_selected
                else R.drawable.background_item_tv_category_tv
            )
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
