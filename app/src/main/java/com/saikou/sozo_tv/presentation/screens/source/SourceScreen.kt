package com.saikou.sozo_tv.presentation.screens.source

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.adapters.SourceHeaderAdapter
import com.saikou.sozo_tv.adapters.SourcePageAdapter
import com.saikou.sozo_tv.databinding.SourceScreenBinding
import com.saikou.sozo_tv.utils.loadSources

class SourceScreen : Fragment() {
    private var _binding: SourceScreenBinding? = null
    private val binding get() = _binding!!
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = SourceScreenBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadSources {
            val adapter = SourceHeaderAdapter()
            binding.sourceRv.adapter = adapter
//            binding.sourceRv.setupGridLayoutForSources(adapter)
            adapter.submitList(it)
        }
    }
}