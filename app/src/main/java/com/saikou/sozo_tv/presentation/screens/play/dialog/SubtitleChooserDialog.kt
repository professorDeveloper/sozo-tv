package com.saikou.sozo_tv.presentation.screens.play.dialog

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.adapters.SubtitleAdapter
import com.saikou.sozo_tv.data.model.SubTitle
import com.saikou.sozo_tv.databinding.DialogSubtitleChooserBinding

class SubtitleChooserDialog : DialogFragment() {
    private var subtitles: List<SubTitle> = emptyList()
    private var currentSelected: SubTitle? = null
    private var useSubtitles: Boolean = false
    private lateinit var adapter: SubtitleAdapter
    private lateinit var listener: (SubTitle?) -> Unit
    private var _binding: DialogSubtitleChooserBinding? = null
    private val binding get() = _binding!!

    companion object {
        fun newInstance(
            subtitles: List<SubTitle>, selectedSubtitle: SubTitle?, useSubtitles: Boolean
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
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = DialogSubtitleChooserBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupDialogWindow()
        with(binding) {
            binding.rvSubtitles.isVisible = useSubtitles
            adapter = SubtitleAdapter(subtitles, currentSelected) { selectedSub ->
                currentSelected = selectedSub
                adapter.selected = selectedSub
                adapter.notifyDataSetChanged()
            }

            binding.close.setOnClickListener {
                Log.d("GG", "useSubtitles:${useSubtitles} ")
                listener(currentSelected)
                dismiss()
            }
            rvSubtitles.layoutManager = LinearLayoutManager(context)
            rvSubtitles.adapter = adapter
            rvSubtitles.visibility = if (useSubtitles) View.VISIBLE else View.GONE
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

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
    }
}