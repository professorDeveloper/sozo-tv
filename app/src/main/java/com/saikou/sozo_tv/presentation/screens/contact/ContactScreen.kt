package com.saikou.sozo_tv.presentation.screens.contact

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.saikou.sozo_tv.data.local.pref.PreferenceManager
import com.saikou.sozo_tv.databinding.ContactScreenBinding
import com.saikou.sozo_tv.utils.requestInitialFocus
import com.saikou.sozo_tv.utils.snackString

class ContactScreen : Fragment() {
    private var _binding: ContactScreenBinding? = null
    private val binding get() = _binding!!
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ContactScreenBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.seasonalBackground.setTheme(PreferenceManager().getSeasonalTheme())

        // playContainer was declared focusable with no listener behind it: the only focusable
        // view on the screen, and pressing OK on it did nothing at all. Plenty of TVs ship
        // without Telegram or a browser, so a device that cannot open the link falls back to
        // showing the handle rather than throwing.
        binding.playContainer.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(TELEGRAM_URL)))
            } catch (_: ActivityNotFoundException) {
                snackString("Telegram: $TELEGRAM_HANDLE", requireActivity(), TELEGRAM_URL)
            }
        }
        binding.playContainer.requestInitialFocus()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        const val TELEGRAM_HANDLE = "@sozoApp"
        const val TELEGRAM_URL = "https://t.me/sozoApp"
    }

}