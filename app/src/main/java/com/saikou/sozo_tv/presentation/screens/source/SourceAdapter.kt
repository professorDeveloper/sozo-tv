package com.saikou.sozo_tv.presentation.screens.source

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListUpdateCallback
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.data.extensions.ExtProvider

/** Findable views inside [R.layout.header_source]; the fragment wires the listeners. */
class SourceHeaderViews(val root: View) {
    val btnTabAniyomi: TextView = root.findViewById(R.id.btnTabAniyomi)
    val btnTabCloudstream: TextView = root.findViewById(R.id.btnTabCloudstream)
    val tvStatus: TextView = root.findViewById(R.id.tvStatus)
    val progressBar: ProgressBar = root.findViewById(R.id.progressBar)
    val etSearchProvider: EditText = root.findViewById(R.id.etSearchProvider)
    val repoFilterContainer: LinearLayout = root.findViewById(R.id.repoFilterContainer)
    val tvEmpty: TextView = root.findViewById(R.id.tvEmpty)
}

/**
 * Single adapter that renders the whole Sources screen:
 *  - position 0  → the header ([R.layout.header_source]) wired by the fragment via [onBindHeader]
 *  - positions 1+ → installed providers for the active engine tab
 *
 * Keeping the header inside the same RecyclerView lets the title/tabs/search scroll up with the
 * list and keeps all focusables in one D-pad traversal tree (TV-friendly).
 */
class SourceAdapter(
    private val onBindHeader: (SourceHeaderViews) -> Unit,
    private val onProviderClick: (ExtProvider) -> Unit,
    private val onProviderLongClick: (ExtProvider) -> Boolean = { false },
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val allItems = mutableListOf<ExtProvider>()
    private val items = mutableListOf<ExtProvider>()
    private var selectedId: String? = null
    private var query: String = ""
    private var repoFilter: String? = null

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long =
        if (position == 0) Long.MIN_VALUE else items[position - 1].id.hashCode().toLong()

    fun submit(list: List<ExtProvider>, selected: String?) {
        allItems.clear()
        allItems.addAll(list)
        selectedId = selected
        applyFilter()
    }

    /** Filter the visible providers by name/lang/repo (case-insensitive substring). */
    fun filter(text: String) {
        query = text.trim()
        applyFilter()
    }

    /** Restrict the list to one repo (null = all repos). Combined with the text [filter]. */
    fun setRepoFilter(repo: String?) {
        repoFilter = repo
        applyFilter()
    }

    /** Distinct repo names across all loaded providers, in first-seen order. */
    fun repos(): List<String> =
        allItems.mapNotNull { it.repo?.takeIf { r -> r.isNotBlank() } }.distinct()

    fun setSelected(id: String?) {
        val old = selectedId
        selectedId = id
        // Only repaint the two affected rows — never touch the header (position 0).
        notifyProviderChanged(old)
        notifyProviderChanged(id)
    }

    private fun notifyProviderChanged(id: String?) {
        if (id == null) return
        val idx = items.indexOfFirst { it.id == id }
        if (idx >= 0) notifyItemChanged(idx + 1)
    }

    /** Number of provider rows currently shown (excludes the header). */
    fun providerCount(): Int = items.size

    /** RecyclerView position (header-offset included) of the active provider, or -1. */
    fun selectedAdapterPosition(): Int {
        val id = selectedId ?: return -1
        val idx = items.indexOfFirst { it.id == id }
        return if (idx >= 0) idx + 1 else -1
    }

    private fun applyFilter() {
        val q = query.lowercase()
        var seq = allItems.asSequence()
        repoFilter?.let { repo -> seq = seq.filter { it.repo == repo } }
        if (q.isNotEmpty()) {
            seq = seq.filter {
                it.name.lowercase().contains(q) ||
                    (it.lang?.lowercase()?.contains(q) == true) ||
                    (it.repo?.lowercase()?.contains(q) == true)
            }
        }
        // Pin the active provider to the top so the current pick is always first (stable sort
        // keeps the rest in their original order).
        val newItems = seq.toList().sortedByDescending { it.id == selectedId }

        val old = items.toList()
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = old.size
            override fun getNewListSize() = newItems.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) =
                old[oldItemPosition].id == newItems[newItemPosition].id

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int) =
                old[oldItemPosition] == newItems[newItemPosition]
        })
        items.clear()
        items.addAll(newItems)

        // Dispatch granular updates offset by +1: position 0 is the header. Rebinding the header
        // (the focused search field) on every keystroke is what made the D-pad jump to the top,
        // so the header must stay untouched while only the rows below it change.
        diff.dispatchUpdatesTo(object : ListUpdateCallback {
            override fun onInserted(position: Int, count: Int) =
                notifyItemRangeInserted(position + 1, count)

            override fun onRemoved(position: Int, count: Int) =
                notifyItemRangeRemoved(position + 1, count)

            override fun onMoved(fromPosition: Int, toPosition: Int) =
                notifyItemMoved(fromPosition + 1, toPosition + 1)

            override fun onChanged(position: Int, count: Int, payload: Any?) =
                notifyItemRangeChanged(position + 1, count, payload)
        })
    }

    override fun getItemViewType(position: Int): Int =
        if (position == 0) TYPE_HEADER else TYPE_PROVIDER

    override fun getItemCount(): Int = items.size + 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderVH(inflater.inflate(R.layout.header_source, parent, false))
        } else {
            ProviderVH(inflater.inflate(R.layout.item_ext_provider, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is HeaderVH -> onBindHeader(holder.views)
            is ProviderVH -> {
                val p = items[position - 1]
                holder.bind(p, p.id == selectedId)
                holder.itemView.setOnClickListener { onProviderClick(p) }
                holder.itemView.setOnLongClickListener { onProviderLongClick(p) }
            }
        }
    }

    class HeaderVH(v: View) : RecyclerView.ViewHolder(v) {
        val views = SourceHeaderViews(v)
    }

    class ProviderVH(v: View) : RecyclerView.ViewHolder(v) {
        private val icon: ImageView = v.findViewById(R.id.ivIcon)
        private val name: TextView = v.findViewById(R.id.tvName)
        private val meta: TextView = v.findViewById(R.id.tvMeta)
        private val selected: TextView = v.findViewById(R.id.tvSelected)

        fun bind(p: ExtProvider, isSelected: Boolean) {
            name.text = p.name
            meta.text = listOfNotNull(p.group, p.lang, p.repo)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
            selected.isVisible = isSelected
            if (!p.icon.isNullOrEmpty()) {
                Glide.with(icon).load(p.icon)
                    .placeholder(R.drawable.ic_round_star_24).into(icon)
            } else {
                icon.setImageResource(R.drawable.ic_round_star_24)
            }
        }
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_PROVIDER = 1
    }
}
