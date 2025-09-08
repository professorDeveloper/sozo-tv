package com.saikou.sozo_tv.presentation.screens.episodes

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.databinding.EpisodeScreenBinding
import com.saikou.sozo_tv.utils.gone
import com.saikou.sozo_tv.utils.readData
import com.saikou.sozo_tv.utils.visible

class EpisodeScreen : Fragment() {
    private var _binding: EpisodeScreenBinding? = null
    private val binding get() = _binding!!
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = EpisodeScreenBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val currentSource = readData<String>("subSource") ?: ""
        if (currentSource.isEmpty()) {
            binding.topContainer.gone()
            binding.loadingLayout.gone()
            binding.textView6.gone()
            binding.placeHolder.root.visible()
            binding.placeHolder.placeHolderImg.setImageResource(R.drawable.ic_source)
            binding.placeHolder.placeholderTxt.text =
                "No Source Selected \n Please Select Source First "
        } else {
            binding.textView6.text = "Current Selected Source: $currentSource"
        }
    }
}