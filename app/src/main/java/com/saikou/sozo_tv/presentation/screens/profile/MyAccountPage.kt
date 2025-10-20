package com.saikou.sozo_tv.presentation.screens.profile

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
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
        preferenceManager = com.saikou.sozo_tv.data.local.pref.PreferenceManager()
        loadChannelPreference()
        loadNsfwPreference()
        loadModePreference()
        setupModeButtons()
        binding.channelToggleContainer.setOnClickListener {
            binding.channelSwitch.toggle()
        }
        binding.channelSwitch.setOnCheckedChangeListener { _, isChecked ->
            updateChannelStatus(isChecked)
            saveChannelPreference(isChecked)
        }

        binding.nsfwToggleContainer.setOnClickListener {
            binding.nsfwSwitch.toggle()
        }
        binding.nsfwSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (preferenceManager.isNsfwEnabled()) {
                updateNsfStatus(isChecked)
                saveNsfPreference(isChecked)
            } else {
                showNsfwWarningDialog()

            }
        }

    }

    private fun setupModeButtons() {
        binding.apply {
            animeModeButton.setOnClickListener {
                setModeAnime(true)
                updateModeUI(true)
            }

            movieModeButton.setOnClickListener {
                setModeAnime(false)
                updateModeUI(false)
            }


        }
    }

    private fun loadModePreference() {
        val isAnimeMode = isModeAnimeEnabled()
        updateModeUI(isAnimeMode)
    }

    private fun updateModeUI(isAnimeMode: Boolean) {
        binding.apply {
            updateButtonBackground(animeModeButton, isAnimeMode)
            updateButtonBackground(movieModeButton, !isAnimeMode)

        }
    }

    private fun isModeAnimeEnabled(): Boolean {
        return preferenceManager.isModeAnimeEnabled()
    }

    private fun setModeAnime(enabled: Boolean) {
        preferenceManager.setModeAnime(enabled)
    }

    @SuppressLint("SetTextI18n")
    private fun updateButtonBackground(button: android.widget.TextView, isActive: Boolean) {
        if (isActive) {
            button.setBackgroundResource(
                R.drawable.switch_selected_background
            )

        } else {
            button.setBackgroundResource(
                R.drawable.switch_background
            )

        }
    }


    private fun showNsfwWarningDialog() {
        val dialog = NsfwAlertDialog()
        dialog.setYesContinueListener {
            updateChannelStatus(true)
            saveChannelPreference(true)
            dialog.dismiss()
        }
        dialog.setOnBackPressedListener {
            binding.nsfwSwitch.isChecked = false
            updateChannelStatus(false)
            saveChannelPreference(false)
            dialog.dismiss()
        }


        dialog.show(parentFragmentManager, "NsfwWarningDialog")
    }

    private fun saveChannelPreference(isEnabled: Boolean) {
        preferenceManager.setChannelEnabled(isEnabled)
    }

    private fun saveNsfPreference(isEnabled: Boolean) {
        preferenceManager.setNsfwEnabled(isEnabled)
    }

    private fun loadChannelPreference() {
        val isEnabled = preferenceManager.isChannelEnabled()
        binding.channelSwitch.isChecked = isEnabled
        updateChannelStatus(isEnabled)
    }

    private fun loadNsfwPreference() {
        val isEnabled = preferenceManager.isNsfwEnabled()
        binding.nsfwSwitch.isChecked = isEnabled
        updateNsfStatus(isEnabled)
    }

    @SuppressLint("SetTextI18n")
    private fun updateChannelStatus(isEnabled: Boolean) {
        binding.apply {
            if (isEnabled) {
                channelStatusDot.background = ContextCompat.getDrawable(
                    requireContext(),
                    R.drawable.netflix_status_dot_enabled
                )
                channelStatusText.text = "Enabled"
                channelStatusText.setTextColor(
                    ContextCompat.getColor(
                        requireContext(),
                        R.color.netflix_green
                    )
                )
            } else {
                channelStatusDot.background = ContextCompat.getDrawable(
                    requireContext(),
                    R.drawable.netflix_status_dot_disabled
                )
                channelStatusText.text = "Disabled"
                channelStatusText.setTextColor(
                    ContextCompat.getColor(
                        requireContext(),
                        R.color.netflix_gray
                    )
                )
            }
        }
    }

    private fun updateNsfStatus(isEnabled: Boolean) {
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