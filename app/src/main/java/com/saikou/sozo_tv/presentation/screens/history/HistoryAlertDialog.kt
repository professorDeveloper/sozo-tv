package com.saikou.sozo_tv.presentation.screens.history

import android.annotation.SuppressLint
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.databinding.HistoryAlertDialogBinding

class HistoryAlertDialog : DialogFragment() {

    private var _binding: HistoryAlertDialogBinding? = null
    private val binding get() = _binding!!

    // Named for what they do. `noClearListener` was the one that CLEARED and
    // `yesContinueListener` the one that cancelled, which is a good way to wire
    // a destructive action to the wrong button on the next edit.
    private var onClear: (() -> Unit)? = null
    private var onKeep: (() -> Unit)? = null

    fun setOnClear(listener: () -> Unit) {
        onClear = listener
    }

    fun setOnKeep(listener: () -> Unit) {
        onKeep = listener
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = HistoryAlertDialogBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dialog!!.window?.setBackgroundDrawable(ColorDrawable(0))
        dialog!!.window?.setWindowAnimations(R.style.DialogAnimation)
        binding.clearBtn.setOnClickListener { onClear?.invoke() }
        binding.keepBtn.setOnClickListener { onKeep?.invoke() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
