package com.saikou.sozo_tv.presentation.screens.contact

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.saikou.sozo_tv.databinding.ContactScreenBinding

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

}