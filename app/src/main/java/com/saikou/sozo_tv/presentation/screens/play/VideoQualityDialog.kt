package com.saikou.sozo_tv.presentation.screens.play

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.saikou.sozo_tv.adapters.VideoOptionsAdapter
import com.saikou.sozo_tv.databinding.DialogVideoQualityBinding
import com.saikou.sozo_tv.parser.models.VideoOption
import com.saikou.sozo_tv.presentation.screens.play.dialog.applyGlassWindow

class VideoQualityDialog(
    private val list: List<VideoOption> = emptyList(),
    private val currentIndex: Int = 0,
) : DialogFragment() {

    private var _binding: DialogVideoQualityBinding? = null
    private val binding get() = _binding!!

    private var yesContinueListener: ((VideoOption, Int) -> Unit)? = null

    fun setYesContinueListener(listener: (VideoOption, Int) -> Unit) {
        yesContinueListener = listener
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = DialogVideoQualityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dialog?.applyGlassWindow()

        if (list.isEmpty()) {
            dismissAllowingStateLoss()
            return
        }

        binding.close.setOnClickListener { dismiss() }

        val selected = currentIndex.coerceIn(0, list.lastIndex)
        val adapter = VideoOptionsAdapter(list) { video, i ->
            yesContinueListener?.invoke(video, i)
            dismiss()
        }
        adapter.setDefaultSelected(selected)
        binding.videOptionRv.adapter = adapter
        binding.videOptionRv.post {
            val rv = _binding?.videOptionRv ?: return@post
            rv.selectedPosition = selected
            rv.requestFocus()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
