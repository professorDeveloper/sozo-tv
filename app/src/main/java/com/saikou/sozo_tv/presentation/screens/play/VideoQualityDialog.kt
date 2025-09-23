package com.saikou.sozo_tv.presentation.screens.play

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.saikou.sozo_tv.adapters.VideoOptionsAdapter
import com.saikou.sozo_tv.data.local.pref.PreferenceManager
import com.saikou.sozo_tv.databinding.AlertPlayerDialogBinding
import com.saikou.sozo_tv.databinding.DialogVideoQualityBinding
import com.saikou.sozo_tv.parser.models.VideoOption

class VideoQualityDialog(private val list: List<VideoOption>, private var currentIndex: Int) :
    DialogFragment() {

    private var _binding: DialogVideoQualityBinding? = null
    private val binding get() = _binding!!


    private lateinit var yesContinueListener: (VideoOption, Int) -> Unit


    fun setYesContinueListener(listener: (VideoOption, Int) -> Unit) {
        yesContinueListener = listener
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogVideoQualityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val adapter = VideoOptionsAdapter(list) { video, i ->
            yesContinueListener.invoke(video, i)
            dismiss()
        }
        binding.videOptionRv.adapter = adapter
        adapter.setDefaultSelected(currentIndex)
    }
}