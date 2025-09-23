package com.saikou.sozo_tv.presentation.screens.play

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.saikou.sozo_tv.databinding.AlertPlayerDialogBinding
import com.saikou.sozo_tv.databinding.DialogVideoQualityBinding
import com.saikou.sozo_tv.parser.models.VideoOption

class VideoQualityDialog(private val list:ArrayList<VideoOption>): DialogFragment() {

    private var _binding: DialogVideoQualityBinding? = null
    private val binding get() = _binding!!


    private lateinit var yesContinueListener: () -> Unit


    fun setYesContinueListener(listener: () -> Unit) {
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
    }
}