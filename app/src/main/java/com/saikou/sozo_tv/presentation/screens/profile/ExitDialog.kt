package com.saikou.sozo_tv.presentation.screens.profile

import android.annotation.SuppressLint
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.data.model.anilist.Profile
import com.saikou.sozo_tv.databinding.ExitDialogBinding
import com.saikou.sozo_tv.utils.loadImage

/**
 * Sign-out confirmation.
 *
 * Built through [newInstance] with its data in the arguments bundle: the system recreates a
 * DialogFragment with its no-arg constructor after a configuration change or process death, and
 * the old constructor-argument version crashed there.
 */
class ExitDialog : DialogFragment() {

    companion object {
        private const val ARG_NAME = "name"
        private const val ARG_AVATAR = "avatar"

        fun newInstance(data: Profile): ExitDialog = ExitDialog().apply {
            arguments = bundleOf(ARG_NAME to data.name, ARG_AVATAR to data.avatarUrl)
        }
    }

    private var _binding: ExitDialogBinding? = null
    private val binding get() = _binding!!

    private var noClearListener: (() -> Unit)? = null

    private var yesContinueListener: (() -> Unit)? = null

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
            noClearListener?.invoke() ?: dismiss()
        }
        binding.accountName.text = arguments?.getString(ARG_NAME).orEmpty()
        binding.coverImage.loadImage(arguments?.getString(ARG_AVATAR))
        binding.yesExit.setOnClickListener {
            // Listeners do not survive recreation; a restored dialog just closes rather than
            // offering a button that does nothing.
            yesContinueListener?.invoke() ?: dismiss()
        }

    }

    override fun onStart() {
        super.onStart()
        // Signing out is the destructive half of this dialog, so start on the way out. A dialog's
        // view is only attached once it is shown, so onViewCreated is too early to place focus.
        binding.notNowBtn.requestFocus()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
