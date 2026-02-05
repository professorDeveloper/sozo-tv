package com.saikou.sozo_tv.presentation.screens.profile

import android.annotation.SuppressLint
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.fragment.app.DialogFragment
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.databinding.NsfwDialogBinding

class NsfwAlertDialog : DialogFragment() {

    private var _binding: NsfwDialogBinding? = null
    private val binding get() = _binding!!


    private lateinit var yesContinueListener: () -> Unit
    private lateinit var onbackPressedListener: () -> Unit

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
        _binding = NsfwDialogBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        dialog!!.window?.setBackgroundDrawable(ColorDrawable(0))
        dialog!!.window?.setWindowAnimations(R.style.DialogAnimation)
        binding.btnConfirm.setOnClickListener {
            yesContinueListener.invoke()
        }
        requireActivity().onBackPressedDispatcher.addCallback {
            onbackPressedListener.invoke()
        }

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}