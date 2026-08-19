package com.saikou.sozo_tv.presentation.screens.play

import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.adapters.VideoServersAdapter
import com.saikou.sozo_tv.databinding.DialogVideoQualityBinding

class VideoServerDialog(
    private val servers: List<VideoServersAdapter.ServerRow>,
    private val currentIndex: Int,
    private val titleRes: Int = R.string.player_server_title,
    private val subtitleRes: Int = R.string.player_server_subtitle,
) : DialogFragment() {

    private var _binding: DialogVideoQualityBinding? = null
    private val binding get() = _binding!!

    private var onPick: ((String) -> Unit)? = null
    private var onPickIndex: ((Int) -> Unit)? = null

    fun setOnServerPicked(listener: (String) -> Unit) {
        onPick = listener
    }

    /** For rows whose label is not itself the answer. */
    fun setOnRowPicked(listener: (Int) -> Unit) {
        onPickIndex = listener
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = DialogVideoQualityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dialog?.window?.setBackgroundDrawable(ColorDrawable(0))
        dialog?.window?.setWindowAnimations(R.style.DialogAnimation)

        binding.dialogTitle.text = getString(titleRes)
        binding.dialogSubtitle.text = getString(subtitleRes)

        binding.videOptionRv.adapter = VideoServersAdapter(servers, currentIndex) { row, index ->
            onPick?.invoke(row.name)
            onPickIndex?.invoke(index)
            dismiss()
        }

        binding.videOptionRv.post {
            binding.videOptionRv.setSelectedPosition(currentIndex.coerceAtLeast(0))
            binding.videOptionRv.requestFocus()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
