package com.saikou.sozo_tv.presentation.screens.tv_garden

import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.databinding.FilterDialogBinding
import com.saikou.sozo_tv.databinding.FilterDialogGardenBinding
import com.saikou.sozo_tv.domain.model.MySpinnerItem
import com.saikou.sozo_tv.presentation.screens.category.CustomSpinnerAdapter
import com.saikou.sozo_tv.utils.LocalData
import com.skydoves.powerspinner.IconSpinnerItem

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

    val filterList = arrayListOf(MySpinnerItem("By Country"), MySpinnerItem("By Category"))

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.close.setOnClickListener {
            dismiss()
        }
        dialog!!.window?.setWindowAnimations(R.style.DialogAnimation)
        dialog!!.window?.setBackgroundDrawable(ColorDrawable(0))
        selectedSort = arguments?.getString("selectedSort")
        if (selectedSort != null) binding.sortFilter.hint =
            "Selected Sort: $selectedSort"
        binding.sortFilter.apply {
            setSpinnerAdapter(CustomSpinnerAdapter(this).apply {
                this.setOnSpinnerItemSelectedListen {
                    binding.sortFilter.hint = "Selected Sort: ${it.title}"
                    selectedSort = it.title.toString()
                }
                var findIndex = -1
                filterList.onEachIndexed { index, mySpinnerItem ->
                    if (selectedSort.toString() == mySpinnerItem.title) {
                        findIndex = index

                    }
                }
                if (findIndex != -1) {
                    notifyItemSelected(findIndex)
                }
            })

            setItems(
                filterList
            )
            setOnSpinnerItemSelectedListener<IconSpinnerItem> { _, _, _, item ->

            }
            preferenceName = "sort"
        }

        binding.applyFilter.setOnClickListener {
            onFiltersApplied?.invoke(selectedSort)
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(
            selectedSort: String?
        ): FilterDialogGarden {
            val dialog = FilterDialogGarden()
            val args = Bundle()
            args.putString("selectedSort", selectedSort)
            dialog.arguments = args
            return dialog
        }
    }

}


