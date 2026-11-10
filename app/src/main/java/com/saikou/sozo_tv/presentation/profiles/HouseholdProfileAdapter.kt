package com.saikou.sozo_tv.presentation.profiles

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.saikou.sozo_tv.databinding.ItemHouseholdProfileBinding
import com.saikou.sozo_tv.domain.model.HouseholdProfile

class HouseholdProfileAdapter(
    private val onClick: (HouseholdProfile) -> Unit,
) : RecyclerView.Adapter<HouseholdProfileAdapter.Holder>() {
    private var items: List<HouseholdProfile> = emptyList()

    fun submit(value: List<HouseholdProfile>) {
        items = value
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ItemHouseholdProfileBinding.inflate(LayoutInflater.from(parent.context), parent, false),
    )

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])

    inner class Holder(private val binding: ItemHouseholdProfileBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(profile: HouseholdProfile) = with(binding) {
            name.text = profile.name
            kidsBadge.visibility = if (profile.isKids) View.VISIBLE else View.GONE
            val drawable = ProfileAvatarResources.drawable(profile.avatar)
            avatar.setImageResource(drawable ?: android.R.color.transparent)
            initial.visibility = if (drawable == null) View.VISIBLE else View.GONE
            initial.text = profile.name.trim().firstOrNull()?.uppercase() ?: "?"
            val base = ProfileAvatarResources.fallbackColor(profile.color, profile.name)
            (avatar.background as? GradientDrawable)?.setColor(base)
                ?: run { avatar.background = GradientDrawable().apply { setColor(base); cornerRadius = 22f } }
            root.contentDescription = buildString {
                append(profile.name)
                if (profile.isKids) append(", bolalar profili")
            }
            root.setOnClickListener { onClick(profile) }
            root.setOnFocusChangeListener { view, focused ->
                view.animate().scaleX(if (focused) 1.055f else 1f)
                    .scaleY(if (focused) 1.055f else 1f).setDuration(140).start()
            }
        }
    }
}
