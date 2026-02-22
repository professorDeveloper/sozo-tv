package com.saikou.sozo_tv.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.data.model.SubSource
import com.saikou.sozo_tv.databinding.ItemSourceBinding
import com.saikou.sozo_tv.databinding.ItemSourceHeaderBinding
import com.saikou.sozo_tv.domain.model.GroupedSource
import com.saikou.sozo_tv.utils.loadImage

class SourceAdapter(
    private val onClick: (SubSource) -> Unit,
    private val selectedAnimeId: String? = null,
    private val selectedMovieId: String? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_SOURCE = 1
    }

    // Har bir element yoki header yoki source
    sealed class ListItem {
        data class Header(
            val category: String, // "ANIME", "MOVIE"
            val title: String,    // "AnimeFLV"
            val count: Int
        ) : ListItem()

        data class Source(
            val subSource: SubSource,
            val groupType: String // qaysi guruhga tegishli
        ) : ListItem()
    }

    private val items = mutableListOf<ListItem>()
    private var selectedIndex = -1

    // ==================== HEADER ViewHolder ====================
    inner class HeaderViewHolder(val binding: ItemSourceHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ListItem.Header) {
            binding.tvGroupCategory.text = item.category
            binding.tvGroupTitle.text = item.title
            binding.tvSourceCount.text = "${item.count} sources"
        }
    }

    // ==================== SOURCE ViewHolder ====================
    inner class SourceViewHolder(val binding: ItemSourceBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ListItem.Source, isSelected: Boolean) {
            val sub = item.subSource

            binding.tvCode.text = sub.title.ifEmpty { sub.sourceId.uppercase() }
            binding.tvCountry.text = sub.country
            binding.tvTitle.text = sub.sourceId.uppercase()

            // Logo yoki harf
            if (!sub.logoUrl.isNullOrEmpty() && sub.logoUrl != "null") {
                binding.ivLogo.loadImage(sub.logoUrl)
                binding.ivLogo.visibility = View.VISIBLE
                binding.tvLetterLogo.visibility = View.GONE
            } else {
                binding.ivLogo.visibility = View.GONE
                binding.tvLetterLogo.visibility = View.VISIBLE
                val firstLetter = if (sub.title.isNotEmpty())
                    sub.title[0].toString().uppercase()
                else
                    sub.sourceId.take(1).uppercase()
                binding.tvLetterLogo.text = firstLetter
            }

            // Selected / Normal
            val bgRes = if (isSelected) R.drawable.bg_item_selected
            else R.drawable.bg_item_normal
            binding.rootItem.background = ContextCompat.getDrawable(binding.root.context, bgRes)

            // Click
            binding.rootItem.setOnClickListener {
                selectItem(bindingAdapterPosition, sub)
            }

            // D-pad Enter/OK
            binding.rootItem.setOnKeyListener { _, keyCode, event ->
                if (event.action == android.view.KeyEvent.ACTION_DOWN &&
                    (keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
                            keyCode == android.view.KeyEvent.KEYCODE_ENTER)
                ) {
                    selectItem(bindingAdapterPosition, sub)
                    true
                } else false
            }
        }

        private fun selectItem(position: Int, sub: SubSource) {
            if (position == RecyclerView.NO_POSITION) return
            val oldIndex = selectedIndex
            selectedIndex = position
            if (oldIndex != -1) notifyItemChanged(oldIndex)
            notifyItemChanged(selectedIndex)
            onClick(sub)
        }
    }

    // ==================== Adapter overrides ====================

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is ListItem.Header -> TYPE_HEADER
            is ListItem.Source -> TYPE_SOURCE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> {
                val binding = ItemSourceHeaderBinding.inflate(inflater, parent, false)
                HeaderViewHolder(binding)
            }
            else -> {
                val binding = ItemSourceBinding.inflate(inflater, parent, false)
                SourceViewHolder(binding)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is ListItem.Header -> (holder as HeaderViewHolder).bind(item)
            is ListItem.Source -> {
                val isSelected = isItemSelected(item)
                (holder as SourceViewHolder).bind(item, isSelected || position == selectedIndex)
            }
        }
    }

    override fun getItemCount(): Int = items.size

    // ==================== Selection logic ====================

    private fun isItemSelected(item: ListItem.Source): Boolean {
        val selectedId = when (item.groupType) {
            "anime" -> selectedAnimeId
            "movie" -> selectedMovieId
            else -> null
        }
        return selectedId != null && item.subSource.sourceId == selectedId
    }

    // ==================== Data update ====================

    /**
     * GroupedSource ro'yxatidan flat list yaratadi:
     * [Header, Source, Source, Source, Header, Source, Source, ...]
     */
    fun updateList(groups: List<GroupedSource>) {
        items.clear()
        selectedIndex = -1

        for (group in groups) {
            // Header qo'shish
            val categoryLabel = when (group.type) {
                "anime" -> "ANIME"
                "movie" -> "MOVIE"
                else -> group.type.uppercase()
            }
            items.add(
                ListItem.Header(
                    category = categoryLabel,
                    title = group.title,
                    count = group.sources.size
                )
            )

            // Source'larni qo'shish
            val selectedId = when (group.type) {
                "anime" -> selectedAnimeId
                "movie" -> selectedMovieId
                else -> null
            }

            for (source in group.sources) {
                val sourceItem = ListItem.Source(
                    subSource = source,
                    groupType = group.type
                )
                items.add(sourceItem)

                // Avvaldan tanlangan source'ni topish
                if (selectedId != null && source.sourceId == selectedId) {
                    selectedIndex = items.size - 1
                }
            }
        }

        notifyDataSetChanged()
    }
}