package com.saikou.sozo_tv.presentation.activities

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.data.repository.HouseholdProfileRepository
import com.saikou.sozo_tv.databinding.ActivityProfileEditorBinding
import com.saikou.sozo_tv.presentation.profiles.AvatarChoiceAdapter
import com.saikou.sozo_tv.presentation.profiles.ProfileAvatarResources
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class ProfileEditorActivity : AppCompatActivity() {
    private lateinit var binding: ActivityProfileEditorBinding
    private val profiles: HouseholdProfileRepository by inject()
    private var profileId: String? = null
    private var avatar: String = ProfileAvatarResources.selectableIds.first()
    private var saving = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        profileId = intent.getStringExtra(EXTRA_PROFILE_ID)
        val original = profiles.state.value.profiles.firstOrNull { it.id == profileId }
        avatar = original?.avatar?.takeIf { ProfileAvatarResources.drawable(it) != null }
            ?: ProfileAvatarResources.selectableIds.first()
        binding.title.setText(if (original == null) R.string.profile_create_title else R.string.profile_edit_title)
        binding.name.text = original?.name.orEmpty()
        binding.kids.isChecked = original?.isKids == true
        binding.delete.visibility = if (original != null && !original.isDefault) View.VISIBLE else View.GONE
        binding.avatars.layoutManager = GridLayoutManager(this, 6)
        binding.avatars.adapter = AvatarChoiceAdapter(avatar) { avatar = it }
        bindKeyboard()
        binding.save.setOnClickListener { save() }
        binding.delete.setOnClickListener { confirmDelete() }
        binding.avatars.post {
            binding.avatars.findViewHolderForAdapterPosition(
                ProfileAvatarResources.selectableIds.indexOf(avatar).coerceAtLeast(0)
            )?.itemView?.requestFocus()
        }
    }

    private fun bindKeyboard() = with(binding.keyboard) {
        setOnKeyClickListener { if (binding.name.text.length < 30) binding.name.append(it) }
        setOnBackspaceClickListener {
            if (binding.name.text.isNotEmpty()) binding.name.text = binding.name.text.dropLast(1)
        }
        setOnClearClickListener { binding.name.text = "" }
    }

    private fun save() {
        val name = binding.name.text.toString().trim()
        if (name.isBlank()) {
            Toast.makeText(this, R.string.profile_name_required, Toast.LENGTH_SHORT).show()
            return
        }
        if (saving) return
        saving = true
        binding.save.isEnabled = false
        lifecycleScope.launch {
            val id = profileId
            val ok = if (id == null) {
                profiles.create(name, avatar, null, binding.kids.isChecked)
            } else {
                profiles.update(id, mapOf(
                    "name" to name,
                    "avatar" to avatar,
                    "isKids" to binding.kids.isChecked,
                ))
            }
            if (ok) finish() else {
                saving = false
                binding.save.isEnabled = true
                Toast.makeText(this@ProfileEditorActivity, profiles.state.value.error, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun confirmDelete() {
        val id = profileId ?: return
        AlertDialog.Builder(this)
            .setMessage(R.string.profile_delete_confirm)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.profile_delete) { _, _ ->
                lifecycleScope.launch {
                    if (profiles.delete(id)) finish()
                    else Toast.makeText(this@ProfileEditorActivity, profiles.state.value.error, Toast.LENGTH_LONG).show()
                }
            }.show()
    }

    companion object {
        const val EXTRA_PROFILE_ID = "sozo.extra.PROFILE_ID"
    }
}
