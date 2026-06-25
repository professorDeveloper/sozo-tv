package com.saikou.sozo_tv.presentation.screens.source

import android.content.Context
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceFragmentCompat
import com.saikou.sozo_tv.data.extensions.ExtensionEngine
import kotlinx.coroutines.launch

class AniyomiSourceSettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val provider = arguments?.getString(ARG_PROVIDER).orEmpty()
        val bareId = provider.removePrefix("an:")
        preferenceManager.sharedPreferencesName = "source_$bareId"
        preferenceManager.sharedPreferencesMode = Context.MODE_PRIVATE
        val screen = preferenceManager.createPreferenceScreen(requireContext())
        preferenceScreen = screen
        if (provider.isEmpty()) return
        lifecycleScope.launch {
            val src = ExtensionEngine.shared.aniyomiConfigurable(provider)
            runCatching { src?.setupPreferenceScreen(screen) }
        }
    }

    companion object {
        const val ARG_PROVIDER = "provider"
    }
}
