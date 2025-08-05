package com.saikou.sozo_tv.presentation.screens.category

import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.domain.model.MySpinnerItem
import com.skydoves.powerspinner.OnSpinnerItemSelectedListener
import com.skydoves.powerspinner.PowerSpinnerInterface
import com.skydoves.powerspinner.PowerSpinnerView

class CustomSpinnerAdapter(
    override val spinnerView: PowerSpinnerView
) : RecyclerView.Adapter<CustomSpinnerAdapter.CustomSpinnerViewHolder>(),
    PowerSpinnerInterface<MySpinnerItem> {

    var spinnerItems: List<MySpinnerItem> = listOf()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override var index: Int = spinnerView.selectedIndex

    override var onSpinnerItemSelectedListener: OnSpinnerItemSelectedListener<MySpinnerItem>? = null

    lateinit var onSpinnerItemSelected: (MySpinnerItem) -> Unit
    fun setOnSpinnerItemSelectedListen(listener: (MySpinnerItem) -> Unit) {
        onSpinnerItemSelected = listener
    }

    override fun getItemCount(): Int = spinnerItems.size

    override fun notifyItemSelected(index: Int) {
        this.index = index
        notifyDataSetChanged()
    }

    override fun setItems(itemList: List<MySpinnerItem>) {
        spinnerItems = itemList
    }

    fun getItem(position: Int): MySpinnerItem = spinnerItems[position]

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CustomSpinnerViewHolder {
        val view =
            LayoutInflater.from(parent.context).inflate(R.layout.spinner_custom_item, parent, false)
        return CustomSpinnerViewHolder(view).apply {
            itemView.setOnClickListener {
                val pos = bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION }
                    ?: return@setOnClickListener
                val oldIndex = index
                index = pos
                notifyItemChanged(oldIndex)
                notifyItemChanged(pos)
                onSpinnerItemSelected.invoke(
                    getItem(index)
                )
                spinnerView.dismiss()
            }
        }
    }

    override fun onBindViewHolder(holder: CustomSpinnerViewHolder, position: Int) {
        val item = getItem(position)
        val isSelected = position == index
        holder.bind(item, isSelected)
    }

    inner class CustomSpinnerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleText: TextView = itemView.findViewById(R.id.spinner_item_title)

        fun bind(item: MySpinnerItem, isSelected: Boolean) {
            titleText.text = item.title

            itemView.setBackgroundColor(
                if (isSelected) if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    itemView.context.getColor(
                        R.color.white40
                    )
                } else {
                    ContextCompat.getColor(itemView.context, R.color.white40)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    itemView.context.getColor(
                        R.color.spinner_color
                    )
                } else {
                    ContextCompat.getColor(itemView.context, R.color.spinner_color)
                }
            )


            itemView.isFocusable = true
            itemView.isFocusableInTouchMode = true

        }
    }
}
