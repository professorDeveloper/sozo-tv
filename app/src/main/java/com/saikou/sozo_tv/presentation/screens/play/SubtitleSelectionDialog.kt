package com.saikou.sozo_tv.presentation.screens.play

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.data.model.SubtitleItem
import com.saikou.sozo_tv.databinding.SubtitleSelectionDialogBinding

class SubtitleSelectionDialog : DialogFragment() {

    private var _binding: SubtitleSelectionDialogBinding? = null
    private val binding get() = _binding!!

    private var subtitles: List<SubtitleItem> = emptyList()
    private var selectedPosition: Int = -1
    private var onSubtitleSelected: ((SubtitleItem, Int) -> Unit)? = null

    companion object {
        private const val ARG_SUBTITLES = "subtitles"
        private const val ARG_SELECTED_POSITION = "selected_position"

        fun newInstance(
            subtitles: List<SubtitleItem>,
            selectedPosition: Int = -1
        ) = SubtitleSelectionDialog().apply {
            arguments = Bundle().apply {
                putParcelableArrayList(ARG_SUBTITLES, ArrayList(subtitles))
                putInt(ARG_SELECTED_POSITION, selectedPosition)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        parseArguments()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).apply {
            window?.setBackgroundDrawableResource(android.R.color.transparent)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = SubtitleSelectionDialogBinding.inflate(inflater, container, false).also {
        _binding = it
    }.root

    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
    }

    private fun parseArguments() {
        arguments?.let {
            subtitles = it.getParcelableArrayList(ARG_SUBTITLES) ?: emptyList()
            selectedPosition = it.getInt(ARG_SELECTED_POSITION, -1)
        }
    }

    private fun setupUI() {
        setupRecyclerView()
        setupCloseButton()
        updateEmptyState()
    }

    private fun setupRecyclerView() {
        val adapter = SubtitleAdapter(
            subtitles = subtitles,
            selectedPosition = selectedPosition,
            onItemClick = { subtitle, position ->
                selectedPosition = position
                onSubtitleSelected?.invoke(subtitle, position)
                dismiss()
            }
        )

        binding.recyclerSubtitles.apply {
            layoutManager = LinearLayoutManager(context)
            this.adapter = adapter
            setHasFixedSize(true)

            if (selectedPosition >= 0) {
                post { scrollToPosition(selectedPosition) }
            }
        }
    }

    private fun setupCloseButton() {
        binding.btnClose.setOnClickListener { dismiss() }
    }

    private fun updateEmptyState() {
        binding.emptyState.visibility = if (subtitles.isEmpty()) {
            ViewGroup.VISIBLE
        } else {
            ViewGroup.GONE
        }
    }

    fun setOnSubtitleSelectedListener(listener: (SubtitleItem, Int) -> Unit) {
        onSubtitleSelected = listener
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // <CHANGE> Inner adapter class for subtitle items
    private inner class SubtitleAdapter(
        private val subtitles: List<SubtitleItem>,
        private val onItemClick: (SubtitleItem, Int) -> Unit,
        private val selectedPosition: Int = -1
    ) : RecyclerView.Adapter<SubtitleAdapter.SubtitleViewHolder>() {

        inner class SubtitleViewHolder(private val itemView: android.view.View) :
            RecyclerView.ViewHolder(itemView) {

            fun bind(subtitle: SubtitleItem, position: Int, isSelected: Boolean) {
                val tvLanguage = itemView.findViewById<android.widget.TextView>(R.id.tv_language)
                val tvInfo = itemView.findViewById<android.widget.TextView>(R.id.tv_info)
                val imgSelected = itemView.findViewById<android.widget.ImageView>(R.id.img_selected)

                tvLanguage.text = subtitle.lang
                tvInfo.text = "${subtitle.format.uppercase()} • ${subtitle.name}"
                imgSelected.visibility =
                    if (isSelected) android.view.View.VISIBLE else android.view.View.GONE

                itemView.setOnClickListener {
                    onItemClick(subtitle, position)
                }

                itemView.setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus) {
                        itemView.animate().scaleX(1.02f).scaleY(1.02f).duration = 200
                    } else {
                        itemView.animate().scaleX(1f).scaleY(1f).duration = 200
                    }
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SubtitleViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.subtitle_item, parent, false)
            return SubtitleViewHolder(view)
        }

        override fun onBindViewHolder(holder: SubtitleViewHolder, position: Int) {
            holder.bind(subtitles[position], position, position == selectedPosition)
        }

        override fun getItemCount() = subtitles.size
    }
}