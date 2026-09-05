package com.saikou.sozo_tv.presentation.screens.play.dialog

import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.adapters.PlayerSettingsAdapter
import com.saikou.sozo_tv.adapters.SettingRow
import com.saikou.sozo_tv.databinding.PopupPlayerSettingsBinding

class PlayerSettingsPopup : DialogFragment() {

    private var _binding: PopupPlayerSettingsBinding? = null
    private val binding get() = _binding!!

    var rootRows: () -> List<SettingRow> = { emptyList() }

    var optionsFor: (Int) -> List<SettingRow> = { emptyList() }

    var onOption: (row: Int, option: Int) -> Unit = { _, _ -> }

    var onImmediate: (Int) -> Unit = {}

    private var openRow: Int? = null
    private var lastRootRow = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = PopupPlayerSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dialog?.applyGlassWindow(Gravity.BOTTOM or Gravity.END)
        val inset = (28 * resources.displayMetrics.density).toInt()
        dialog?.window?.apply {
            attributes = attributes.apply {
                x = inset
                y = inset
            }
        }
        binding.close.setOnClickListener { dismiss() }
        showRoot()
        installBackHandling()
    }

    private fun showRoot() {
        openRow = null
        val rows = rootRows()
        lastRootRow = lastRootRow.coerceIn(0, (rows.size - 1).coerceAtLeast(0))
        render(getString(R.string.player_settings_title), rows, lastRootRow) { index ->
            lastRootRow = index
            val options = optionsFor(index)
            if (options.isEmpty()) {
                onImmediate(index)
                dismiss()
            } else {
                openRow = index
                val selected = options.indexOfFirst { it.ticked }.coerceAtLeast(0)
                render(rows.getOrNull(index)?.label.orEmpty(), options, selected) { option ->
                    onOption(index, option)
                    showRoot()
                }
            }
        }
    }

    private fun render(
        title: String,
        rows: List<SettingRow>,
        selected: Int,
        onPick: (Int) -> Unit,
    ) {
        val b = _binding ?: return
        b.popupTitle.text = title
        b.popupList.adapter = PlayerSettingsAdapter(rows, onPick)
        b.popupList.post {
            val list = _binding?.popupList ?: return@post
            list.selectedPosition = selected.coerceIn(0, (rows.size - 1).coerceAtLeast(0))
            list.requestFocus()
        }
    }

    private fun installBackHandling() {
        dialog?.setOnKeyListener { _, keyCode, event ->
            if (keyCode != KeyEvent.KEYCODE_BACK) return@setOnKeyListener false
            if (event.action != KeyEvent.ACTION_UP) return@setOnKeyListener true
            if (openRow == null) dismiss() else showRoot()
            true
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
