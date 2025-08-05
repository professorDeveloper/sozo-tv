package com.saikou.sozo_tv.presentation.screens.category.dialog

import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.GridLayoutManager
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.databinding.FilterDialogBinding
import com.saikou.sozo_tv.presentation.screens.category.CustomSpinnerAdapter
import com.saikou.sozo_tv.utils.LocalData
import com.skydoves.powerspinner.IconSpinnerAdapter
import com.skydoves.powerspinner.IconSpinnerItem

class FilterDialog : DialogFragment() {

    private var _binding: FilterDialogBinding? = null
    private val binding get() = _binding!!
    var onFiltersApplied: ((String?, String?, String?) -> Unit)? = null
    private var selectedSort: String? = null
    private var selectedYear: String? = null
    private var selectedRating: String? = null


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FilterDialogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.close.setOnClickListener {
            dismiss()
        }
        dialog!!.window?.setWindowAnimations(R.style.DialogAnimation)
        dialog!!.window?.setBackgroundDrawable(ColorDrawable(0))
        selectedSort = arguments?.getString("selectedSort")
        selectedYear = arguments?.getString("selectedYear")
        selectedRating = arguments?.getString("avgScore")
        if (selectedYear != null) binding.yearFilter.hint = "Selected Year: ${selectedYear}"
        if (selectedSort != null) binding.sortFilter.hint =
            "Selected Sort: $selectedSort"
        binding.sortFilter.apply {
            setSpinnerAdapter(CustomSpinnerAdapter(this).apply {
                this.setOnSpinnerItemSelectedListen {
                    binding.sortFilter.hint = "Selected Sort: ${it.title}"
                    selectedSort = it.title.toString()
                }
                var findIndex = -1
                LocalData.sortSpinner.onEachIndexed { index, mySpinnerItem ->
                    if (selectedSort.toString() == mySpinnerItem.title) {
                        findIndex = index

                    }
                }
                if (findIndex != -1) {
                    notifyItemSelected(findIndex)
                }
            })

            setItems(
                LocalData.sortSpinner
            )
            setOnSpinnerItemSelectedListener<IconSpinnerItem> { _, _, _, item ->
            }
            preferenceName = "sort"
        }
        binding.yearFilter.apply {
            setSpinnerAdapter(CustomSpinnerAdapter(this).apply {
                this.setOnSpinnerItemSelectedListen {
                    selectedYear = it.title
                    binding.yearFilter.hint = "Selected Year: ${it.title}"
                }
                var ggIndex = -1
                LocalData.years.onEachIndexed { index, mySpinnerItem ->
                    if (selectedYear.toString() == mySpinnerItem.title) {
                        ggIndex = index
                    }
                }
                if (ggIndex != -1) {
                    this.notifyItemSelected(ggIndex)
                }
            })

            setItems(
                LocalData.years
            )
            setOnSpinnerItemSelectedListener<IconSpinnerItem> { _, _, _, item ->
            }
        }
        binding.sliderRating.addOnChangeListener { slider, value, fromUser ->
            val rating = value.toInt()
            selectedRating = if (rating != 0) rating.toString() else null
        }
        if (selectedRating != null) {
            if (selectedRating != "") {
                binding.sliderRating.value = selectedRating.toString().toFloat()

            }
        }

        binding.applyFilter.setOnClickListener {
            onFiltersApplied?.invoke(selectedSort, selectedYear, selectedRating)
            dismiss()
        }
        binding.clearFilter.setOnClickListener {
            selectedSort = null
            selectedYear = null
            selectedRating = ""
            binding.sortFilter.hint = "Select Sort"
            binding.yearFilter.hint = "Select Year"
            binding.sortFilter.clearSelectedItem()
            binding.yearFilter.clearSelectedItem()
            binding.sliderRating.value = 1f
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(
            selectedYear: String?, selectedSort: String?, avgScore: String?
        ): FilterDialog {
            val dialog = FilterDialog()
            val args = Bundle()
            args.putString("selectedYear", selectedYear)
            args.putString("selectedSort", selectedSort)
            args.putString("avgScore", avgScore)
            dialog.arguments = args
            return dialog
        }
    }

}


