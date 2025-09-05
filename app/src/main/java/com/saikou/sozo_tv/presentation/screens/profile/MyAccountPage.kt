package com.saikou.sozo_tv.presentation.screens.profile

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.saikou.sozo_tv.databinding.MyAccountPageBinding


class MyAccountPage : Fragment() {
    private var _binding: MyAccountPageBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = MyAccountPageBinding.inflate(inflater, container, false)
        return binding.root
    }
}