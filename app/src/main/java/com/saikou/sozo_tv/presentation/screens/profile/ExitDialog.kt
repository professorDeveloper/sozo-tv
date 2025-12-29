package com.saikou.sozo_tv.presentation.screens.profile

import android.annotation.SuppressLint
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.data.model.anilist.Profile
import com.saikou.sozo_tv.databinding.ExitDialogBinding
import com.saikou.sozo_tv.utils.loadImage

class ExitDialog(val data: Profile) : DialogFragment() {

    private var _binding: ExitDialogBinding? = null
    private val binding get() = _binding!!

    private lateinit var noClearListener: () -> Unit

    private lateinit var yesContinueListener: () -> Unit

    fun setNoClearListener(listener: () -> Unit) {
        noClearListener = listener
    }

    fun setYesContinueListener(listener: () -> Unit) {
        yesContinueListener = listener
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ExitDialogBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dialog!!.window?.setBackgroundDrawable(ColorDrawable(0))
        dialog!!.window?.setWindowAnimations(R.style.DialogAnimation)
        binding.notNowBtn.setOnClickListener {
            noClearListener.invoke()
        }
        binding.accountName.text = data.name
        binding.coverImage.loadImage(data.avatarUrl)
        binding.yesExit.setOnClickListener {
            yesContinueListener.invoke()
        }

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
