package com.saikou.sozo_tv.presentation.screens.detail

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.Html
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.palette.graphics.Palette
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.adapters.CastAdapter
import com.saikou.sozo_tv.app.MyApp
import com.saikou.sozo_tv.databinding.ItemCastRecommendedBinding
import com.saikou.sozo_tv.databinding.ItemCategoryDetailsHeaderBinding
import com.saikou.sozo_tv.databinding.ItemPlayCastBinding
import com.saikou.sozo_tv.databinding.ItemPlayDetailsHeaderBinding
import com.saikou.sozo_tv.databinding.ItemPlayDetailsSectionBinding
import com.saikou.sozo_tv.databinding.ItemPlayRecommendedBinding
import com.saikou.sozo_tv.domain.model.Cast
import com.saikou.sozo_tv.domain.model.CastAdapterModel
import com.saikou.sozo_tv.domain.model.CastDetailModel
import com.saikou.sozo_tv.domain.model.CategoryDetails
import com.saikou.sozo_tv.domain.model.DetailCategory
import com.saikou.sozo_tv.domain.model.MainModel
import com.saikou.sozo_tv.presentation.screens.category.CategoriesPageAdapter
import com.saikou.sozo_tv.presentation.screens.home.HomeAdapter
import com.saikou.sozo_tv.presentation.screens.home.vh.ViewHolderFactory
import com.saikou.sozo_tv.utils.LocalData
import com.saikou.sozo_tv.utils.LocalData.castList
import com.saikou.sozo_tv.utils.LocalData.recommendedMovies
import com.saikou.sozo_tv.utils.LocalData.recommendedMoviesCast
import com.saikou.sozo_tv.utils.gone
import com.saikou.sozo_tv.utils.loadImage
import com.saikou.sozo_tv.utils.setupGridLayoutForCategories
import com.saikou.sozo_tv.utils.toYear
import com.saikou.sozo_tv.utils.visible
import kotlin.random.Random

class CastDetailAdapter(
    val itemList: MutableList<HomeAdapter.HomeData> = mutableListOf(),
    private val detailsButtonListener: DetailsInterface
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    // Placeholder for the third ite
//    val castResponse = mutableListOf<CastItem>()

    interface DetailsInterface {
        fun onCancelButtonClicked()
    }

    companion object {
        const val DETAILS_ITEM_HEADER = 20
        const val DETAILS_ITEM_SECTION = 21
        const val DETAILS_ITEM_THIRD = 22
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            DETAILS_ITEM_HEADER -> ViewHolderFactory.create(parent, viewType)
            DETAILS_ITEM_SECTION -> ViewHolderFactory.create(parent, viewType)
            DETAILS_ITEM_THIRD -> {
                val binding = ItemCastRecommendedBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                ItemPlayDetailsThirdViewHolder(binding)

            }

            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun getItemCount(): Int {
        return itemList.size
    }

    override fun getItemViewType(position: Int): Int {
        return itemList[position].viewType
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = itemList[position]

        when (holder) {
            is ItemPlayDetailsHeaderViewHolder -> {
                if (item is CastAdapterModel && item.viewType == DETAILS_ITEM_HEADER) {
                    holder.bind(item, interfaceListener = detailsButtonListener)
                }
            }

            is ItemPlayDetailsSectionViewHolder -> {
                if (item is CastAdapterModel && item.viewType == DETAILS_ITEM_SECTION) {
                    holder.bind(item)
                }
            }

            is ItemPlayDetailsThirdViewHolder -> {
                if (item is CastAdapterModel && item.viewType == DETAILS_ITEM_THIRD) {
                    holder.bind(item)
                }
            }
        }

    }


    class ItemPlayDetailsThirdViewHolder(private val binding: ItemCastRecommendedBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(model: CastAdapterModel) {
            binding.root.visible()
            binding.topContainer.visible()
            Log.d("GGG", "bind:fuck WHy not shoewn ${model.media} ")
            val adapter = CategoriesPageAdapter(isDetail = true)
            adapter.setClickDetail {
                LocalData.focusChangedListenerPlayerg.invoke(it)
            }
            adapter.setCategoriesPageInterface(object :
                CategoriesPageAdapter.CategoriesPageInterface {
                override fun onCategorySelected(category: MainModel, position: Int) {
                }

            })
            binding.topContainer.adapter = adapter
            binding.topContainer.setupGridLayoutForCategories(adapter)
            adapter.updateCategoriesAll(recommendedMoviesCast as ArrayList<MainModel>)
        }
    }

    class ItemPlayDetailsSectionViewHolder(
        private val binding: ItemPlayDetailsSectionBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        private var currentLayoutId: Int? = null

        init {
            setFocusChangeListener(
                binding.aboutFilmTv, binding.indicator1, R.layout.item_container_about_film
            )

        }

        fun bind(item: CastAdapterModel) {
            binding.aboutFilmTv.text = binding.root.context.getString(R.string.about_character)
        }

        private fun setFocusChangeListener(view: View, indicator: View, layoutResId: Int) {
            view.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    Log.d("GGG", "setFocusChangeListener:${layoutResId} || ${currentLayoutId} ")
                    indicator.visibility = View.VISIBLE
                } else {
                    indicator.visibility = View.INVISIBLE
                }
            }
        }

    }


    class ItemPlayDetailsHeaderViewHolder(private val binding: ItemCategoryDetailsHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        private var isOn = false
        private var isPlay = true

        @SuppressLint("SetTextI18n")
        fun bind(item: CastAdapterModel, interfaceListener: DetailsInterface) {
            binding.backBtn.setOnClickListener {
                interfaceListener.onCancelButtonClicked()
            }
            binding.characterImage.loadImage(item.image)
            binding.name.text = item.name
            binding.middle.text = item.role

            Glide.with(binding.root.context)
                .asBitmap()
                .load(item.image)
                .into(object : CustomTarget<Bitmap>() {
                    override fun onResourceReady(
                        resource: Bitmap,
                        transition: Transition<in Bitmap>?
                    ) {
                        Palette.from(resource).generate { palette ->
                            if (palette != null) {
                                val mainBg = ContextCompat.getColor(
                                    binding.root.context, R.color.main_background
                                )

                                // Ranglarni yig‘ib olamiz
                                val dominant = palette.getDominantColor(mainBg)
                                val vibrant = palette.getVibrantColor(dominant)
                                val muted = palette.getMutedColor(dominant)

                                // Gradient ranglar: yuqoridan pastga
                                val gradientColors = intArrayOf(
                                    vibrant,    // yuqorida jonli rang
                                    muted,      // keyinroq sokin rang
                                    dominant,   // asosiy rang
                                    mainBg      // eng pastda — siz bergan background
                                )

                                val gradient = GradientDrawable(
                                    GradientDrawable.Orientation.TOP_BOTTOM,
                                    gradientColors
                                )
                                gradient.cornerRadius = 0f

                                binding.root.background = gradient
                            }
                        }
                    }

                    override fun onLoadCleared(placeholder: Drawable?) {}
                })

        }
    }

    fun submitRecommendedMovies(movies: List<MainModel>) {
        recommendedMoviesCast.clear()
        recommendedMoviesCast.addAll(movies)
        notifyItemChanged(3)
    }

    fun submitList(list: List<HomeAdapter.HomeData>) {
        val result = DiffUtil.calculateDiff(object : DiffUtil.Callback() {

            override fun getOldListSize(): Int = itemList.size

            override fun getNewListSize(): Int = list.size

            /**
             * Ikkala ro‘yxatdagi bir xil elementlarni tekshiradi (masalan, ID orqali).
             */
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val oldItem = itemList[oldItemPosition]
                val newItem = list[newItemPosition]

                return when {
                    oldItem is CastAdapterModel && newItem is CastAdapterModel && oldItem.viewType == DETAILS_ITEM_HEADER && newItem.viewType == DETAILS_ITEM_HEADER &&oldItem.viewType == DETAILS_ITEM_THIRD && newItem.viewType == DETAILS_ITEM_THIRD-> {
                        oldItem.name == newItem.name
                    }

                    oldItem is CastAdapterModel && newItem is CastAdapterModel && oldItem.viewType == DETAILS_ITEM_SECTION && newItem.viewType == DETAILS_ITEM_SECTION && oldItem.viewType == DETAILS_ITEM_THIRD && newItem.viewType == DETAILS_ITEM_THIRD -> {
                        oldItem.name == newItem.name
                    }

                    else -> false
                }
            }

            /**
             * Ikkala ro‘yxatdagi elementlar mazmunining bir xil ekanligini tekshiradi.
             */
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val oldItem = itemList[oldItemPosition]
                val newItem = list[newItemPosition]
                return oldItem == newItem
            }
        })

        itemList.clear()
        itemList.addAll(list)
        result.dispatchUpdatesTo(this)
    }


}