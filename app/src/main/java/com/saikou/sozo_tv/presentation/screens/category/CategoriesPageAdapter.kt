package com.saikou.sozo_tv.presentation.screens.category

import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.databinding.ItemBottombarBinding
import com.saikou.sozo_tv.databinding.ItemMovieBinding
import com.saikou.sozo_tv.domain.model.MainModel

class CategoriesPageAdapter(
    private val isDetail: Boolean = false,
    private val showBottomBar: Boolean = false
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val VIEW_TYPE_NAVBAR = 0
        const val VIEW_TYPE_MOVIE = 1
        var isUpdated = false
    }

    lateinit var clickDetaill: (MainModel) -> Unit
    fun setClickDetail(listener: (MainModel) -> Unit) {
        clickDetaill = listener
    }

    var onAnimeClick: (() -> Unit)? = null
    var onCharactersClick: (() -> Unit)? = null

    lateinit var categoriesPageInterfaceg: CategoriesPageInterface
    fun setCategoriesPageInterface(categoriesPageInterface: CategoriesPageInterface) {
        this.categoriesPageInterfaceg = categoriesPageInterface
    }

    interface CategoriesPageInterface {
        fun onCategorySelected(category: MainModel, position: Int)
    }

    var categoryList: ArrayList<MainModel> = arrayListOf()

    private var selectedPosition: Int = RecyclerView.NO_POSITION
    private var isAnimeSelected = true

    override fun getItemViewType(position: Int): Int {
        return if (showBottomBar && position == 0) VIEW_TYPE_NAVBAR else VIEW_TYPE_MOVIE
    }

    override fun getItemCount(): Int {
        return if (showBottomBar) categoryList.size + 1 else categoryList.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_NAVBAR -> NavBarViewHolder(ItemBottombarBinding.inflate(inflater, parent, false))
            else -> CategoryViewHolder(ItemMovieBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is NavBarViewHolder -> holder.bind()
            is CategoryViewHolder -> {
                val index = if (showBottomBar) position - 1 else position
                if (index in categoryList.indices) {
                    holder.bind(categoryList[index], index)
                }
            }
        }
    }

    fun updateCategoriesAll(newCategoryList: ArrayList<MainModel>) {
        categoryList.clear()
        categoryList.addAll(newCategoryList)
        selectedPosition = RecyclerView.NO_POSITION
        notifyDataSetChanged()
    }

    /*** ViewHolders ***/
    inner class NavBarViewHolder(private val binding: ItemBottombarBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind() {
            updateNavSelection()

            binding.navAnime.setOnClickListener {
                isAnimeSelected = true
                updateNavSelection()
                onAnimeClick?.invoke()
            }
            binding.navCharacters.setOnClickListener {
                isAnimeSelected = false
                updateNavSelection()
                onCharactersClick?.invoke()
            }
        }

        private fun updateNavSelection() {
            // navAnime tanlanganda background selected bo'ladi
            binding.navAnime.background = ContextCompat.getDrawable(
                binding.root.context,
                if (isAnimeSelected) R.drawable.tab_background_selector else R.drawable.tab_background_unselected
            )
            binding.navCharacters.background = ContextCompat.getDrawable(
                binding.root.context,
                if (!isAnimeSelected) R.drawable.tab_background_selector else R.drawable.tab_background_unselected
            )
        }
    }

    inner class CategoryViewHolder(private val binding: ItemMovieBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(data: MainModel, index: Int) {
            binding.apply {
                Glide.with(root.context).load(data.image).into(itemImg)
                topContainer.text = data.title

                updateSelectedState(index)

                root.setOnClickListener {
                    val oldPosition = selectedPosition
                    selectedPosition = index
                    if (oldPosition != RecyclerView.NO_POSITION)
                        notifyItemChanged(oldPosition + if (showBottomBar) 1 else 0)
                    notifyItemChanged(index + if (showBottomBar) 1 else 0)
                    clickDetaill.invoke(data)
                }

                root.setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus && absoluteAdapterPosition >= itemCount - 5) {
                        categoriesPageInterfaceg.onCategorySelected(data, absoluteAdapterPosition)
                    }
                    val animation = AnimationUtils.loadAnimation(
                        root.context,
                        if (hasFocus) R.anim.zoom_in else R.anim.zoom_out
                    )
                    root.startAnimation(animation)
                    animation.fillAfter = true
                }

                root.isFocusable = true
                root.isFocusableInTouchMode = true
                (root.layoutParams as ViewGroup.MarginLayoutParams).apply {
                    topMargin = 14
                    bottomMargin = 14
                }
            }
        }

        private fun updateSelectedState(index: Int) {
            val isSelected = index == selectedPosition
            binding.root.background = ContextCompat.getDrawable(
                binding.root.context,
                if (isSelected) R.drawable.tab_background_selector else R.drawable.tab_background_unselected
            )
        }
    }
}
