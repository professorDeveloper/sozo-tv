package com.saikou.sozo_tv.utils

import android.view.LayoutInflater
import android.widget.LinearLayout
//
//class QualitySelectionManager(
//    private val container: LinearLayout,
//    private val qualities: List<QualityItem>,
//    private var selectedIndex: Int,
//    private val onQualitySelected: (QualityItem, Int) -> Unit
//) {
//    private val context = container.context
//    private var selectedView: ItemQualityBinding? = null
//
//    fun populateQualities() {
//        container.removeAllViews() // Clear previous items
//
//        qualities.forEachIndexed { index, qualityItem ->
//            val itemBinding =
//                ItemQualityBinding.inflate(LayoutInflater.from(context), container, false)
//            itemBinding.text1.text = qualityItem.typeName
//
//            // Set default selection state
//            updateSelectionState(itemBinding, index == selectedIndex)
//
//            itemBinding.root.setOnClickListener {
//                updateSelectedQuality(index, itemBinding)
//                onQualitySelected(qualityItem, index)
//            }
//
//
//            container.addView(itemBinding.root)
//        }
//    }
//
//    private fun updateSelectedQuality(newIndex: Int, newSelectedView: ItemQualityBinding) {
//        selectedView?.let { updateSelectionState(it, false) } // Remove highlight from previous
//        updateSelectionState(newSelectedView, true) // Highlight new selection
//        selectedView = newSelectedView
//        selectedIndex = newIndex
//    }
//
//    private fun updateSelectionState(view: ItemQualityBinding, isSelected: Boolean) {
//        if (isSelected) {
//            view.text1.isChecked = true
//        } else {
//            view.text1.isChecked = false
//            view.text1.setBackgroundResource(R.drawable.background_button)
//        }
//    }
//}
