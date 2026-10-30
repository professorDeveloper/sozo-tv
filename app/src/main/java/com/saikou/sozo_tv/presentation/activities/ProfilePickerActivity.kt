package com.saikou.sozo_tv.presentation.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.saikou.sozo_tv.data.repository.DeviceAuthRepository
import com.saikou.sozo_tv.data.repository.HouseholdProfileRepository
import com.saikou.sozo_tv.databinding.ActivityProfilePickerBinding
import com.saikou.sozo_tv.domain.model.HouseholdProfile
import com.saikou.sozo_tv.presentation.profiles.HouseholdProfileAdapter
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class ProfilePickerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityProfilePickerBinding
    private val profiles: HouseholdProfileRepository by inject()
    private val auth: DeviceAuthRepository by inject()
    private lateinit var adapter: HouseholdProfileAdapter
    private var startup = false
    private var manageMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfilePickerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        startup = intent.getBooleanExtra(EXTRA_STARTUP, false)
        adapter = HouseholdProfileAdapter { profile ->
            if (manageMode) openEditor(profile.id) else activate(profile)
        }
        binding.profiles.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.profiles.adapter = adapter
        binding.addProfile.setOnClickListener { openEditor(null) }
        binding.manageProfiles.setOnClickListener { toggleManageMode() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                profiles.state.collect { state ->
                    adapter.submit(state.profiles)
                    binding.loading.visibility = if (state.loading) View.VISIBLE else View.GONE
                    binding.error.visibility = if (state.error == null) View.GONE else View.VISIBLE
                    binding.error.text = state.error
                    val canManage = state.active?.isKids != true
                    binding.addProfile.visibility = if (canManage && state.profiles.size < state.max) View.VISIBLE else View.GONE
                    binding.manageProfiles.visibility = if (canManage && state.profiles.isNotEmpty()) View.VISIBLE else View.GONE
                    if (state.profiles.isNotEmpty()) binding.profiles.post {
                        if (!binding.profiles.hasFocus()) binding.profiles.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
                    }
                }
            }
        }
        lifecycleScope.launch {
            if (!auth.isSignedIn()) return@launch enterApp()
            val ok = profiles.refresh()
            val list = profiles.state.value.profiles
            if (startup && (list.size <= 1 || !ok && list.isEmpty())) {
                list.firstOrNull()?.let(profiles::activate)
                enterApp()
            }
        }
    }

    private fun activate(profile: HouseholdProfile) {
        profiles.activate(profile)
        enterApp()
    }

    private fun openEditor(id: String?) {
        startActivity(Intent(this, ProfileEditorActivity::class.java).apply {
            putExtra(ProfileEditorActivity.EXTRA_PROFILE_ID, id)
        })
    }

    private fun toggleManageMode() {
        manageMode = !manageMode
        binding.manageProfiles.setText(
            if (manageMode) com.saikou.sozo_tv.R.string.profile_done
            else com.saikou.sozo_tv.R.string.profile_manage
        )
        binding.profiles.announceForAccessibility(binding.manageProfiles.text)
    }

    private fun enterApp() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    companion object {
        const val EXTRA_STARTUP = "sozo.extra.PROFILE_STARTUP"
    }
}
