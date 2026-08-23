package com.saikou.sozo_tv.presentation.screens.play.dialog

import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.fragment.app.DialogFragment
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.adapters.PlayerSettingsAdapter
import com.saikou.sozo_tv.adapters.SettingRow
import com.saikou.sozo_tv.databinding.PopupPlayerSettingsBinding

/**
 * The player's settings menu.
 *
 * A narrow panel pinned to the corner the ⚙ button lives in, not a dialog in
 * the middle of the picture: the control bar had grown to eleven buttons and
 * stopped fitting, and the answer to that is a menu you can open without losing
 * sight of what you are watching.
 *
 * Two levels in one window. Choosing a row replaces the contents in place
 * rather than stacking a second dialog on top of the first, which is how every
 * player people already use behaves — and it means Back always means "up one",
 * never "close everything".
 */
class PlayerSettingsPopup : DialogFragment() {

    private var _binding: PopupPlayerSettingsBinding? = null
    private val binding get() = _binding!!

    /** Root entries, supplied fresh each time so values are never stale. */
    var rootRows: () -> List<SettingRow> = { emptyList() }

    /** Options for the row at [index], or empty when the row acts immediately. */
    var optionsFor: (Int) -> List<SettingRow> = { emptyList() }

    /** Row [row]'s option [option] was chosen. */
    var onOption: (row: Int, option: Int) -> Unit = { _, _ -> }

    /** A root row with no options — it does its own thing and closes the menu. */
    var onImmediate: (Int) -> Unit = {}

    private var openRow: Int? = null

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
        dialog?.window?.apply {
            setBackgroundDrawable(ColorDrawable(0))
            setWindowAnimations(R.style.DialogAnimation)
            // Pinned to the bottom-right, beside the button that opened it, and
            // never covering the middle of the frame.
            setGravity(Gravity.BOTTOM or Gravity.END)
            setLayout(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
            )
            attributes = attributes.apply { x = 28; y = 28 }
        }
        showRoot()
        installBackHandling()
    }

    private fun showRoot() {
        openRow = null
        render(getString(R.string.player_settings_title), rootRows()) { index ->
            val options = optionsFor(index)
            if (options.isEmpty()) {
                onImmediate(index)
                dismiss()
            } else {
                openRow = index
                render(rootRows().getOrNull(index)?.label.orEmpty(), options) { option ->
                    onOption(index, option)
                    // Back to the root so the new value is visible immediately —
                    // closing outright would hide the thing that just changed.
                    showRoot()
                }
            }
        }
    }

    private fun render(title: String, rows: List<SettingRow>, onPick: (Int) -> Unit) {
        val b = _binding ?: return
        b.popupTitle.text = title
        // BACK closes this too, but a visible way out is what a remote user
        // looks for — every other panel in the app has one.
        b.close.setOnClickListener { dismiss() }
        b.popupList.adapter = PlayerSettingsAdapter(rows, onPick)
        b.popupList.post {
            if (_binding == null) return@post
            val selected = rows.indexOfFirst { it.ticked }.coerceAtLeast(0)
            b.popupList.setSelectedPosition(selected)
            b.popupList.requestFocus()
        }
    }

    /**
     * Back closes the sub-list first, the menu second.
     *
     * Wired to the dialog itself rather than exposed for a caller to remember:
     * the first version of this was a public method nobody called, so Back
     * always closed the whole menu and the second level may as well not have
     * existed.
     */
    private fun installBackHandling() {
        dialog?.setOnKeyListener { _, keyCode, event ->
            if (keyCode != android.view.KeyEvent.KEYCODE_BACK) return@setOnKeyListener false
            if (event.action != android.view.KeyEvent.ACTION_UP) return@setOnKeyListener true
            if (openRow == null) {
                dismiss()
            } else {
                showRoot()
            }
            true
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
