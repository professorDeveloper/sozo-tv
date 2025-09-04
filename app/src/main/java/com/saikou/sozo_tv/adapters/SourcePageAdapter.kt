package com.saikou.sozo_tv.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.saikou.sozo_tv.data.model.SubSource
import com.saikou.sozo_tv.databinding.SourceItemBinding
import com.saikou.sozo_tv.utils.gone
import com.saikou.sozo_tv.utils.readData
import com.saikou.sozo_tv.utils.visible

class SourcePageAdapter : RecyclerView.Adapter<SourcePageAdapter.SourcePageVh>() {

    private val list = ArrayList<SubSource>()
    private lateinit var isNotify: (String) -> Unit

    private var selectedSource: String? = null

    init {
        selectedSource = readData("selectedSource") ?: "GOGO"
    }

    fun setNotifyListener(listener: (String) -> Unit) {
        isNotify = listener
    }

    inner class SourcePageVh(var itemBinding: SourceItemBinding) :
        RecyclerView.ViewHolder(itemBinding.root) {
        fun onBind(data: SubSource) {
            itemBinding.apply {
                if (data.sourceId == selectedSource) {
                    isSelectedSource.visible()
                } else {
                    isSelectedSource.gone()
                }
                sourceTitle.text = data.title
            }
            itemBinding.root.setOnClickListener {
                selectedSource = data.sourceId
                isNotify.invoke(data.sourceId)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SourcePageVh {
        return SourcePageVh(
            SourceItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    fun submitList(newList: ArrayList<SubSource>) {
        list.clear()
        list.addAll(newList)
        notifyDataSetChanged()
    }

    override fun onBindViewHolder(holder: SourcePageVh, position: Int) {
        holder.onBind(list.get(position))
    }

    override fun getItemCount(): Int {
        return list.size
    }

}