package com.saikou.sozo_tv.presentation.screens.source

import android.content.Context
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import com.saikou.sozo_tv.data.extensions.ExtensionEngine
import kotlinx.coroutines.launch

/**
 * Settings screen for an Aniyomi source, built from whatever the extension itself declares.
 *
 * Every failure here used to land on the same screen: completely blank. A missing argument
 * returned early, a source that is not [eu.kanade.tachiyomi.source.ConfigurableSource] resolved
 * to null, a source that threw was swallowed by runCatching, and a source with nothing to
 * configure simply added no rows. On a TV that is indistinguishable from a screen that failed
 * to render - there is no text to read and nothing to move the highlight onto. Each case now
 * says which one it was.
 */
class AniyomiSourceSettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val provider = arguments?.getString(ARG_PROVIDER).orEmpty()
        val bareId = provider.removePrefix("an:")
        preferenceManager.sharedPreferencesName = "source_$bareId"
        preferenceManager.sharedPreferencesMode = Context.MODE_PRIVATE
        val screen = preferenceManager.createPreferenceScreen(requireContext())
        preferenceScreen = screen

        if (provider.isEmpty()) {
            showNotice(screen, "No source was selected.")
            return
        }

        lifecycleScope.launch {
            val src = ExtensionEngine.shared.aniyomiConfigurable(provider)
            if (src == null) {
                showNotice(screen, "This source has no settings.")
                return@launch
            }
            runCatching { src.setupPreferenceScreen(screen) }
                .onFailure {
                    showNotice(screen, "Could not load settings: ${it.message ?: "unknown error"}")
                }
                .onSuccess {
                    if (screen.preferenceCount == 0) {
                        showNotice(screen, "This source has no settings.")
                    }
                }
        }
    }

    /**
     * Not selectable: it is a statement, not an action, and a focusable row that does nothing
     * when pressed is its own kind of dead end. The screen is now readable and BACK leaves it,
     * which is all an informational screen owes the user.
     */
    private fun showNotice(screen: PreferenceScreen, message: String) {
        if (!isAdded) return
        screen.addPreference(
            Preference(requireContext()).apply {
                title = message
                isSelectable = false
                isPersistent = false
            }
        )
    }

    companion object {
        const val ARG_PROVIDER = "provider"
    }
}
