package com.saikou.sozo_tv.presentation.screens.play

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.adapters.VideoServersAdapter
import com.saikou.sozo_tv.databinding.DialogVideoQualityBinding
import com.saikou.sozo_tv.presentation.screens.play.dialog.applyGlassWindow

class VideoServerDialog(
    private val servers: List<VideoServersAdapter.ServerRow> = emptyList(),
    private val currentIndex: Int = 0,
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
        dialog?.applyGlassWindow()

        if (servers.isEmpty()) {
            dismissAllowingStateLoss()
            return
        }

        binding.dialogTitle.text = getString(titleRes)
        binding.dialogSubtitle.text = getString(subtitleRes)
        binding.close.setOnClickListener { dismiss() }

        val selected = currentIndex.coerceIn(0, servers.lastIndex)
        binding.videOptionRv.adapter = VideoServersAdapter(servers, selected) { row, index ->
            onPick?.invoke(row.name)
            onPickIndex?.invoke(index)
            dismiss()
        }
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
