package com.saikou.sozo_tv.presentation.screens.play.dialog

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.saikou.sozo_tv.adapters.SubtitleAdapter
import com.saikou.sozo_tv.data.model.SubTitle
import com.saikou.sozo_tv.databinding.DialogSubtitleChooserBinding

class SubtitleChooserDialog : BottomSheetDialogFragment() {
    private var subtitles: List<SubTitle> = emptyList()
    private var currentSelected: SubTitle? = null
    private var useSubtitles: Boolean = false
    private lateinit var adapter: SubtitleAdapter
    private lateinit var listener: (SubTitle?) -> Unit
    companion object {
        fun newInstance(
            subtitles: List<SubTitle>,
            selectedSubtitle: SubTitle?,
            useSubtitles: Boolean
        ): SubtitleChooserDialog {
            val fragment = SubtitleChooserDialog()
            fragment.subtitles = subtitles
            fragment.currentSelected = selectedSubtitle
            fragment.useSubtitles = useSubtitles
            return fragment
        }
    }

    fun setSubtitleSelectionListener(listener: (SubTitle?) -> Unit) {
        this.listener = listener
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = DialogSubtitleChooserBinding.inflate(inflater, container, false)
        with(binding) {
            adapter = SubtitleAdapter(subtitles, currentSelected) { selectedSub ->
                currentSelected = selectedSub
                adapter.selected = selectedSub
                adapter.notifyDataSetChanged()
                dismiss()
            }
            rvSubtitles.layoutManager = LinearLayoutManager(context)
            rvSubtitles.adapter = adapter

            subtitleSwitch.isChecked = useSubtitles
            rvSubtitles.visibility = if (useSubtitles) View.VISIBLE else View.GONE

            subtitleSwitch.setOnCheckedChangeListener { _, isChecked ->
                useSubtitles = isChecked
                rvSubtitles.visibility = if (isChecked) View.VISIBLE else View.GONE
                if (isChecked && currentSelected == null && subtitles.isNotEmpty()) {
                    currentSelected = subtitles[0]
                    adapter.selected = currentSelected
                    adapter.notifyDataSetChanged()
                } else if (!isChecked) {
                    currentSelected = null
                    dismiss()
                }
            }

            subtitleToggleContainer.setOnClickListener {
                subtitleSwitch.toggle()
            }
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.isFocusableInTouchMode = true
        view.requestFocus()

        dialog?.let {
            val bottomSheet = it.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let { sheet ->
                val behavior = BottomSheetBehavior.from(sheet)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
            }
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        listener(currentSelected)
    }
}