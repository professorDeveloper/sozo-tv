package com.saikou.sozo_tv.adapters

import androidx.recyclerview.widget.DiffUtil
import com.saikou.sozo_tv.domain.model.SearchModel

/** Null IDs are not an identity: unrelated cards must never inherit each other's focus. */
internal object SearchResultDiff : DiffUtil.ItemCallback<SearchModel>() {
    override fun areItemsTheSame(oldItem: SearchModel, newItem: SearchModel): Boolean =
        if (oldItem.id != null && newItem.id != null) oldItem.id == newItem.id
        else oldItem == newItem

    override fun areContentsTheSame(oldItem: SearchModel, newItem: SearchModel): Boolean =
        oldItem == newItem
}
