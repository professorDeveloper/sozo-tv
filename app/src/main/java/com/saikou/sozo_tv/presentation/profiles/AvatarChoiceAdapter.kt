package com.saikou.sozo_tv.presentation.profiles

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.saikou.sozo_tv.databinding.ItemProfileAvatarChoiceBinding

class AvatarChoiceAdapter(
    selected: String?,
    private val onSelected: (String) -> Unit,
) : RecyclerView.Adapter<AvatarChoiceAdapter.Holder>() {
    private var selectedId = selected

    override fun getItemCount() = ProfileAvatarResources.selectableIds.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ItemProfileAvatarChoiceBinding.inflate(LayoutInflater.from(parent.context), parent, false),
    )

    override fun onBindViewHolder(holder: Holder, position: Int) =
        holder.bind(ProfileAvatarResources.selectableIds[position])

    inner class Holder(private val binding: ItemProfileAvatarChoiceBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(id: String) = with(binding) {
            avatar.setImageResource(requireNotNull(ProfileAvatarResources.drawable(id)))
            root.isActivated = id == selectedId
            root.alpha = if (id == selectedId) 1f else .82f
            root.setOnClickListener {
                val old = ProfileAvatarResources.selectableIds.indexOf(selectedId)
                selectedId = id
                if (old >= 0) notifyItemChanged(old)
                notifyItemChanged(bindingAdapterPosition)
                onSelected(id)
            }
            root.setOnFocusChangeListener { view, focused ->
                view.animate().scaleX(if (focused) 1.09f else 1f)
                    .scaleY(if (focused) 1.09f else 1f).setDuration(120).start()
            }
        }
    }
}
