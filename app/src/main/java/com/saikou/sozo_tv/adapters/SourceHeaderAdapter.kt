package com.saikou.sozo_tv.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.saikou.sozo_tv.data.model.Source
import com.saikou.sozo_tv.databinding.ItemSourceBinding
import com.saikou.sozo_tv.utils.SourceUi

class SourceHeaderAdapter() : RecyclerView.Adapter<SourceHeaderAdapter.SourceHeaderVh>() {
    var list = ArrayList<SourceUi>()
    lateinit var itemClickListenerFr: (String) -> Unit
    fun setItemClickListener(listener: (String) -> Unit) {
        itemClickListenerFr = listener
    }

    inner class SourceHeaderVh(var itemBinding: ItemSourceBinding) : RecyclerView.
    ViewHolder(itemBinding.root) {

        fun onBind(data: SourceUi) {
            itemBinding.apply {
                val sourcePageAdapter = SourcePageAdapter()
                textView6.text = data.country.toString()
                sourcePageAdapter.submitList(data.list)
                sourceRv.adapter = sourcePageAdapter
                sourcePageAdapter.setNotifyListener {
                    itemClickListenerFr.invoke(it)
                }
            }

        }

    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SourceHeaderVh {
        return SourceHeaderVh(
            ItemSourceBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }


    fun submitList(newList: ArrayList<SourceUi>) {
        list.clear()
        list.addAll(newList)
        notifyDataSetChanged()
    }

    override fun onBindViewHolder(holder: SourceHeaderVh, position: Int) {
        holder.onBind(list.get(position))
    }

    override fun getItemCount(): Int {
        return list.size
    }
}