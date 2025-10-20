package com.saikou.sozo_tv.presentation.screens.home

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.model.GlideUrl
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.app.MyApp
import com.saikou.sozo_tv.databinding.BannerItemBinding
import com.saikou.sozo_tv.databinding.ContentBannerBinding
import com.saikou.sozo_tv.databinding.ItemCategoryBinding
import com.saikou.sozo_tv.databinding.ItemChannelCategoryBinding
import com.saikou.sozo_tv.databinding.ItemGenreBinding
import com.saikou.sozo_tv.databinding.ItemMiddleChannelBinding
import com.saikou.sozo_tv.databinding.ItemMovieBinding
import com.saikou.sozo_tv.domain.model.BannerItem
import com.saikou.sozo_tv.domain.model.BannerModel
import com.saikou.sozo_tv.domain.model.Category
import com.saikou.sozo_tv.domain.model.CategoryChannel
import com.saikou.sozo_tv.domain.model.CategoryChannelItem
import com.saikou.sozo_tv.domain.model.CategoryDetails
import com.saikou.sozo_tv.domain.model.CategoryGenre
import com.saikou.sozo_tv.domain.model.CategoryGenreItem
import com.saikou.sozo_tv.presentation.screens.home.vh.ViewHolderFactory
import com.saikou.sozo_tv.utils.LocalData
import com.saikou.sozo_tv.utils.loadImage

class HomeAdapter(private val itemList: MutableList<HomeData> = mutableListOf()) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

//    private var bannerPosition = false

    interface HomeData {
        val viewType: Int
    }


    companion object {
        const val VIEW_BANNER = 0
        const val VIEW_CATEGORY_FILMS = 2
        const val VIEW_BANNER_ITEM = 3
        const val VIEW_CATEGORY_FILMS_ITEM = 4
        const val VIEW_GENRE = 5
        const val VIEW_GENRE_ITEM = 6
        const val VIEW_CHANNEL = 7
        const val VIEW_CHANNEL_ITEM = 8

    }

    /**
     * RecyclerView yangi ViewHolder yaratishni talab qilganda chaqiriladi.
     * Bu yerda Factory pattern orqali mos ViewHolder yaratiladi.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return ViewHolderFactory.create(parent, viewType)
    }

    /**
     * Adapterdagi jami elementlar sonini qaytaradi.
     */
    override fun getItemCount(): Int {
        return itemList.size
    }

    /**
     * Ma'lumotlarni kerakli ViewHolder bilan bog‘laydi.
     * Har bir pozitsiya uchun mos ma'lumot ko‘rsatiladi.
     */
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = itemList[position]
        when (holder) {
            is BannerViewHolder -> {
                if (item is BannerModel) {
                    holder.bind(item)
                }
            }

            is BannerItemViewHolder -> {
                if (item is BannerItem) {
                    holder.bind(item)
                }
            }

            is GenreViewHolder -> {
                if (item is CategoryGenre) {
                    holder.bind(item)
                }
            }

            is GenreItemViewHolder -> {
                if (item is CategoryGenreItem) {
                    holder.bind(item)
                }
            }

            is ChannelViewHolder -> {
                if (item is CategoryChannel) {
                    holder.bind(item)
                }
            }

            is ChannelItemViewHolder -> {
                if (item is CategoryChannelItem) {
                    holder.bind(item)
                }
            }

            is CategoryFilmsItemViewHolder -> {
                if (item is CategoryDetails) {
                    holder.bind(item)
                }
            }

            is ItemCategoryViewHolder -> {
                if (item is Category) {
                    holder.bind(item)
                }
            }
        }
    }


    /**
     * Berilgan pozitsiyadagi elementning `viewType` ni qaytaradi.
     * Bu adapterga qaysi ViewHolderni ishlatishni aniqlashga yordam beradi.
     */
    override fun getItemViewType(position: Int): Int {
        return itemList[position].viewType
    }


    /**
     * Bannerlarni ko‘rsatish uchun ViewHolder.
     */
    class BannerViewHolder(private val binding: ContentBannerBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: BannerModel) {


            val arraYList: ArrayList<BannerItem> = item.data as ArrayList<BannerItem>
            val adapter = HomeAdapter().apply {
                submitList(arraYList)
            }
            binding.viewPager.adapter = adapter
            binding.dotsIndicator.attachTo(binding.viewPager)
            val handler = Handler(Looper.getMainLooper())
            val runnable = object : Runnable {
                override fun run() {
                    if (binding.viewPager.hasFocus() || (binding.viewPager.getChildAt(0) as? RecyclerView)
                            ?.findViewHolderForAdapterPosition(binding.viewPager.currentItem)
                            ?.itemView?.hasFocus() == true
                    ) {
                        val currentItem = binding.viewPager.currentItem
                        val nextItem =
                            if (currentItem == adapter.itemCount - 1) 0 else currentItem + 1
                        binding.viewPager.setCurrentItem(nextItem, true)
                        binding.viewPager.post {
                            (binding.viewPager.getChildAt(0) as? RecyclerView)
                                ?.findViewHolderForAdapterPosition(nextItem)
                                ?.itemView
                                ?.requestFocus()
                        }
                    }
                    handler.postDelayed(this, 8000)
                }
            }
            handler.postDelayed(runnable, 8000)

            binding.viewPager.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    val currentItem = binding.viewPager.currentItem
                    (binding.viewPager.getChildAt(0) as? RecyclerView)
                        ?.findViewHolderForAdapterPosition(currentItem)
                        ?.itemView
                        ?.requestFocus()
                }
            }
        }

    }


    /**
     * Banner elementlarini ko‘rsatish uchun ViewHolder.
     */
    class BannerItemViewHolder(private val binding: BannerItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        @SuppressLint("SetTextI18n")
        fun bind(item: BannerItem) {
            binding.root.setOnClickListener {
                LocalData.listenerItemBanner.invoke(
                    item
                )

            }
            val activity = binding.root.context as? Activity
            if (activity == null || activity.isDestroyed || activity.isFinishing) {
                return
            }

            if (item.contentItem.isMovie) {
                Glide.with(MyApp.context)
                    .load(GlideUrl("${LocalData.IMDB_BACKDROP_PATH}${item.contentItem.image}"))
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .override(400)
                    .into(binding.bannerImg)
            } else {
                Glide.with(MyApp.context)
                    .load(GlideUrl(item.contentItem.image))
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .override(400)
                    .into(binding.bannerImg)
            }

            binding.title.text = item.contentItem.title
            binding.description.text =
                item.contentItem.description
            binding.root.setOnKeyListener { v, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN) {
                    when (keyCode) {
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            return@setOnKeyListener true
                        }

                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            binding.root.clearFocus()
                            return@setOnKeyListener true
                        }

                        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                            return@setOnKeyListener false
                        }
                    }
                }
                return@setOnKeyListener false
            }


        }
    }

    class GenreViewHolder(private val binding: ItemCategoryBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: CategoryGenre) {
            binding.tvCategoryTitle.text = item.name
            binding.hgvCategory.apply {
                setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT)

                adapter = HomeAdapter().apply {
                    submitList(item.list)
                }

//                HomeFakeDAta.setFocusChangedListenerPlayer {
//                    HomeFakeDAta.categoryItemClicked.invoke(
//                        item, it
//                    )
//                }

                setItemSpacing(10)
            }
        }
    }


    class GenreItemViewHolder(private val binding: ItemGenreBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: CategoryGenreItem) {
            Glide.with(binding.root.context).load(item.content.image).into(binding.imgGenre)
            binding.topContainer.text = item.content.title
            binding.root.setOnFocusChangeListener { view, hasFocus ->
                if (bindingAdapterPosition != 0) {
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
                binding.root.setOnClickListener {
                    LocalData.sFocusedGenreClickListener.invoke(item.content.title)
                }
            }

        }
    }


    class ChannelViewHolder(private val binding: ItemChannelCategoryBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: CategoryChannel) {
            binding.tvCategoryTitle.text = item.name
            binding.hgvCategory.apply {
                setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT)

                adapter = HomeAdapter().apply {
                    submitList(item.list)
                }

                setItemSpacing(10)
            }
        }
    }


    class ChannelItemViewHolder(private val binding: ItemMiddleChannelBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: CategoryChannelItem) {
            Glide.with(binding.root.context).load(item.content.image).into(binding.channelLogo)
            binding.channelName.text = item.content.title
            binding.channelGroup.text = item.content.country
            binding.root.setOnClickListener {
                LocalData.channnelItemClickListener.invoke(item.content)
            }
            binding.root.setOnFocusChangeListener { view, hasFocus ->
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

        }
    }

    /**
     * Kategoriyaga oid filmlarni ko‘rsatish uchun ViewHolder.
     */
    class CategoryFilmsItemViewHolder(private val binding: ItemMovieBinding) :
        RecyclerView.ViewHolder(binding.root) {
        @SuppressLint("SetTextI18n")
        fun bind(item: CategoryDetails) {
            binding.topContainer.text = item.content.title.english
            binding.root.apply {

                setOnClickListener {
                    LocalData.listenerItemCategory.invoke(item)
                }
                setOnFocusChangeListener { view, hasFocus ->
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
            }
            binding.genreTv.text =
                "${item.content.source.name} · ${item.content.id} · ${item.content.format.name}"
//
            binding.itemImg.loadImage(item.content.coverImage.large)

        }
    }

    /**
     * Kategoriya elementlarini ko‘rsatish uchun ViewHolder.
     */
    class ItemCategoryViewHolder(private val binding: ItemCategoryBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: Category) {
            binding.tvCategoryTitle.text = item.name

            binding.hgvCategory.apply {
                setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT)
                adapter = HomeAdapter().apply {
                    submitList(item.list)
                }

                setItemSpacing(10)
            }
        }
    }

    /**
     * Adapterdagi ma'lumotlarni yangilaydi va o‘zgarishlarni hisoblab chiqaradi.
     */
    fun submitList(list: List<HomeData>) {
        val result = DiffUtil.calculateDiff(object : DiffUtil.Callback() {

            override fun getOldListSize(): Int = itemList.size

            override fun getNewListSize(): Int = list.size

            /**
             * Ikkala ro‘yxatdagi bir xil elementlarni tekshiradi (masalan, ID orqali).
             */
            override fun areItemsTheSame(
                oldItemPosition: Int, newItemPosition: Int
            ): Boolean {
                val oldItem = itemList[oldItemPosition]
                val newItem = list[newItemPosition]

                return when {
                    oldItem is BannerModel && newItem is BannerModel -> {
                        oldItem.data == newItem.data
                    }

                    oldItem is BannerItem && newItem is BannerItem -> {
                        oldItem.contentItem.mal_id == newItem.contentItem.mal_id
                    }

                    oldItem is Category && newItem is Category -> {
                        oldItem.name == newItem.name
                    }

                    oldItem is CategoryDetails && newItem is CategoryDetails -> {
                        oldItem.content.idMal == newItem.content.idMal
                    }

                    oldItem is CategoryGenreItem && newItem is CategoryGenreItem -> {
                        oldItem.content.image == newItem.content.image
                    }


                    else -> false
                }
            }

            /**
             * Ikkala ro‘yxatdagi elementlar mazmunining bir xil ekanligini tekshiradi.
             */
            override fun areContentsTheSame(
                oldItemPosition: Int, newItemPosition: Int
            ): Boolean {
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