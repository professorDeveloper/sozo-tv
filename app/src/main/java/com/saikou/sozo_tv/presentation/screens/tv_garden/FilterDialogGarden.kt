package com.saikou.sozo_tv.presentation.screens.tv_garden

import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
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

    private val filterList = arrayListOf(
        MySpinnerItem("By Country"),
        MySpinnerItem("By Category"),
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupDialogWindow()


        binding.close.setOnClickListener {
            dismiss()
        }

        selectedSort = arguments?.getString("selectedSort")
        if (selectedSort != null) {
            binding.sortFilter.hint = "Selected Sort: $selectedSort"
        }

        binding.sortFilter.apply {
            val selectedItem = filterList.find { it.title == selectedSort }
            val selectedIndex = filterList.indexOf(selectedItem)

            setSpinnerAdapter(CustomSpinnerAdapter(selectedIndex, this).apply {
                this.setOnSpinnerItemSelectedListen {
                    binding.sortFilter.hint = "Selected Sort: ${it.title}"
                    selectedSort = it.title.toString()
                }
                var ggIndex = -1
                filterList.onEachIndexed { index, mySpinnerItem ->
                    if (selectedSort.toString() == mySpinnerItem.title) {
                        ggIndex = index
                    }
                }
                if (ggIndex != -1) {
                    this.notifyItemSelected(ggIndex)
                }
            })

            setItems(filterList)
            preferenceName = "sort"

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

    private fun setupFocusManagement() {
        binding.root.post {
            binding.sortFilter.requestFocus()
        }

        binding.root.setOnKeyListener { _, keyCode, event ->
            if (keyCode == android.view.KeyEvent.KEYCODE_BACK && event.action == android.view.KeyEvent.ACTION_UP) {
                dismiss()
                true
            } else {
                false
            }
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
