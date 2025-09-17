package com.saikou.sozo_tv.presentation.screens.profile

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.databinding.MyAccountPageBinding


class MyAccountPage : Fragment() {
    private var _binding: MyAccountPageBinding? = null
    private val binding get() = _binding!!
    private lateinit var preferenceManager: com.saikou.sozo_tv.data.local.pref.PreferenceManager

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = MyAccountPageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        preferenceManager = com.saikou.sozo_tv.data.local.pref.PreferenceManager(requireContext())
        loadNsfwPreference()
        binding.nsfwToggleContainer.setOnClickListener {
            binding.nsfwSwitch.toggle()
        }
        binding.nsfwSwitch.setOnCheckedChangeListener { _, isChecked ->
            updateNsfwStatus(isChecked)
            saveNsfwPreference(isChecked)
        }

    }

    private fun saveNsfwPreference(isEnabled: Boolean) {
        preferenceManager.setNsfwEnabled(isEnabled)
    }

    private fun loadNsfwPreference() {
        val isEnabled = preferenceManager.isNsfwEnabled()
        binding.nsfwSwitch.isChecked = isEnabled
        updateNsfwStatus(isEnabled)
    }

    private fun updateNsfwStatus(isEnabled: Boolean) {
        binding.apply {
            if (isEnabled) {
                nsfwStatusDot.background = ContextCompat.getDrawable(
                    requireContext(),
                    R.drawable.netflix_status_dot_enabled
                )
                nsfwStatusText.text = "Enabled"
                nsfwStatusText.setTextColor(
                    ContextCompat.getColor(
                        requireContext(),
                        R.color.netflix_green
                    )
                )
            } else {
                nsfwStatusDot.background = ContextCompat.getDrawable(
                    requireContext(),
                    R.drawable.netflix_status_dot_disabled
                )
                nsfwStatusText.text = "Disabled"
                nsfwStatusText.setTextColor(
                    ContextCompat.getColor(
                        requireContext(),
                        R.color.netflix_gray
                    )
                )
            }
        }
    }
}