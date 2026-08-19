package com.saikou.sozo_tv.presentation.screens.profile

import android.annotation.SuppressLint
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.databinding.NsfwDisableDialogBinding

class NfcDisabledDialog : DialogFragment() {

    private var _binding: NsfwDisableDialogBinding? = null
    private val binding get() = _binding!!


    private var yesContinueListener: (() -> Unit)? = null
    private var onbackPressedListener: (() -> Unit)? = null

    fun setYesContinueListener(listener: () -> Unit) {
        yesContinueListener = listener
    }

    fun setOnBackPressedListener(listener: () -> Unit) {
        onbackPressedListener = listener
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = NsfwDisableDialogBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        dialog!!.window?.setBackgroundDrawable(ColorDrawable(0))
        dialog!!.window?.setWindowAnimations(R.style.DialogAnimation)
        binding.goToSettingsButton.setOnClickListener {
            yesContinueListener?.invoke()
        }
        binding.hideButton.setOnClickListener {
            onbackPressedListener?.invoke() ?: dismiss()
        }
        dialog?.setOnCancelListener { onbackPressedListener?.invoke() }

    }

    override fun onStart() {
        super.onStart()
        // A dialog's view is only attached once it is shown, so onViewCreated is too early to
        // place focus deliberately instead of letting it land by traversal order.
        binding.goToSettingsButton.requestFocus()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}