package com.saikou.sozo_tv.presentation.screens.category

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.recyclerview.widget.RecyclerView
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.databinding.ItemMovieBinding
import com.saikou.sozo_tv.domain.model.MainModel
import com.saikou.sozo_tv.utils.loadImage

class CategoriesPageAdapter(val isDetail: Boolean = false) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        var isUpdated = false
        const val TYPE_CATEGORY = 1
        const val COLUMN_COUNT = 5
        const val COLUMN_COUNT_NUM4 = 4
    }

    lateinit var clickDetaill: (MainModel) -> Unit
    fun setClickDetail(listener: (MainModel) -> Unit) {
        clickDetaill = listener
    }

    lateinit var categoriesPageInterfaceg: CategoriesPageInterface
    fun setCategoriesPageInterface(categoriesPageInterface: CategoriesPageInterface) {
        this.categoriesPageInterfaceg = categoriesPageInterface
    }


    interface CategoriesPageInterface {
        fun onCategorySelected(category: MainModel, position: Int)
    }

    var categoryTabs: ArrayList<String> = arrayListOf()
    var categoryList: ArrayList<MainModel> = arrayListOf()

    override fun getItemViewType(position: Int): Int {
        return when (position) {
            else -> COLUMN_COUNT
        }
    }

    override fun getItemCount(): Int {
        return categoryList.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            else -> CategoryViewHolder(ItemMovieBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is CategoryViewHolder -> {
                val categoryIndex = position
                if (categoryIndex in categoryList.indices) {
                    holder.bind(categoryList[categoryIndex])
                }
            }
        }
    }

    fun updateTabs(tabList: ArrayList<String>) {
        this.categoryTabs = tabList
        notifyItemChanged(0)
    }

    fun updateCategoriesAll(newCategoryList: ArrayList<MainModel>) {
        categoryList.clear()
        categoryList.addAll(newCategoryList)
        notifyDataSetChanged()
    }

    fun updateCategories(newCategories: ArrayList<MainModel>) {
        val startPosition = categoryList.size
        categoryList.addAll(newCategories)
        notifyItemRangeInserted(startPosition + 1, newCategories.size)
        isUpdated = false
    }


    inner class CategoryViewHolder(private val binding: ItemMovieBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(data: MainModel) {
            binding.apply {
                binding.itemImg.loadImage(data.image)
                binding.topContainer.text = data.title
                binding.root.setOnClickListener {
                    if (isDetail) {
                        clickDetaill.invoke(data)
                    } else {
                        clickDetaill.invoke(data)
                    }
                }
                binding.root.setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus) {
                        if (bindingAdapterPosition == categoryList.size - 2 || bindingAdapterPosition == categoryList.size - 1 || bindingAdapterPosition == categoryList.size - 3 || bindingAdapterPosition == categoryList.size - 4 || bindingAdapterPosition == categoryList.size - 5) {
                            Log.d("GGG", "bind:FOCUUSEDDD ")
                            categoriesPageInterfaceg.onCategorySelected(
                                data, absoluteAdapterPosition
                            )
                        }
                    }
                    val animation = when {
                        hasFocus -> AnimationUtils.loadAnimation(
                            binding.root.context,
                            R.anim.zoom_in
                        )

                        else -> AnimationUtils.loadAnimation(
                            binding.root.context,
                            R.anim.zoom_out
                        )
                    }
                    binding.root.startAnimation(animation)
                    animation.fillAfter = true
                }
                binding.root.isFocusable = true
                binding.root.isFocusableInTouchMode = true
                val layoutParams = binding.root.layoutParams as ViewGroup.MarginLayoutParams
                layoutParams.topMargin = 14
                layoutParams.bottomMargin = 14
                binding.root.layoutParams = layoutParams

            }
        }
    }
}