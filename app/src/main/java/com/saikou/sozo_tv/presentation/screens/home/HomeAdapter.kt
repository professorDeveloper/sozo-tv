package com.saikou.sozo_tv.presentation.screens.home

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.TypedValue
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.model.GlideUrl
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.app.MyApp
import com.saikou.sozo_tv.data.model.ViewAllData
import com.saikou.sozo_tv.databinding.BannerItemBinding
import com.saikou.sozo_tv.databinding.ContentBannerBinding
import com.saikou.sozo_tv.databinding.EpisodeItemBinding
import com.saikou.sozo_tv.databinding.ItemCategoryBinding
import com.saikou.sozo_tv.databinding.ItemGenreBinding
import com.saikou.sozo_tv.databinding.ItemMiddleChannelBinding
import com.saikou.sozo_tv.databinding.ItemMovieBinding
import com.saikou.sozo_tv.databinding.ItemViewAllBinding
import com.saikou.sozo_tv.domain.model.BannerItem
import com.saikou.sozo_tv.domain.model.BannerModel
import com.saikou.sozo_tv.domain.model.Category
import com.saikou.sozo_tv.domain.model.CategoryChannel
import com.saikou.sozo_tv.domain.model.CategoryChannelItem
import com.saikou.sozo_tv.domain.model.CategoryDetails
import com.saikou.sozo_tv.domain.model.CategoryGenre
import com.saikou.sozo_tv.domain.model.CategoryGenreItem
import com.saikou.sozo_tv.domain.model.HistoryHome
import com.saikou.sozo_tv.domain.model.HistoryHomeItem
import com.saikou.sozo_tv.presentation.screens.home.vh.ViewHolderFactory
import com.saikou.sozo_tv.utils.LocalData
import com.saikou.sozo_tv.utils.applyTvFocusScale
import com.saikou.sozo_tv.utils.loadImage

/** The pages live in the ViewPager2's inner RecyclerView, not as direct children. */
private fun ViewPager2.focusPage(position: Int) {
    val pages = getChildAt(0) as? RecyclerView ?: return
    pages.findViewHolderForAdapterPosition(position)?.itemView?.requestFocus()
}

class HomeAdapter(private val itemList: MutableList<HomeData> = mutableListOf()) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

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
        const val VIEW_HISTORY = 9
        const val VIEW_HISTORY_ITEM = 10
        const val VIEW_ALL =32

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
    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        (holder as? BannerViewHolder)?.stopAutoAdvance()
    }

    override fun onViewDetachedFromWindow(holder: RecyclerView.ViewHolder) {
        super.onViewDetachedFromWindow(holder)
        (holder as? BannerViewHolder)?.stopAutoAdvance()
    }

    override fun onViewAttachedToWindow(holder: RecyclerView.ViewHolder) {
        super.onViewAttachedToWindow(holder)
        (holder as? BannerViewHolder)?.startAutoAdvance()
    }

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

            is HistoryViewHolder -> {
                if (item is HistoryHome) {
                    holder.bind(item)
                }
            }

            is HistoryItemViewHolder -> {
                if (item is HistoryHomeItem) {
                    holder.onBind(item)
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

            is ViewAllItemViewHolder -> {
                if (item is ViewAllData) holder.bind(item)
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

        private val handler = Handler(Looper.getMainLooper())
        private var childAdapter: HomeAdapter? = null

        private val autoAdvance = object : Runnable {
            override fun run() {
                val adapter = childAdapter
                if (adapter != null && adapter.itemCount > 1 && !binding.viewPager.hasFocus()) {
                    val current = binding.viewPager.currentItem
                    val next = if (current == adapter.itemCount - 1) 0 else current + 1
                    binding.viewPager.setCurrentItem(next, true)
                }
                handler.postDelayed(this, AUTO_ADVANCE_MS)
            }
        }

        fun bind(item: BannerModel) {
            stopAutoAdvance()

            val banners: ArrayList<BannerItem> = item.data as ArrayList<BannerItem>
            val adapter = childAdapter ?: HomeAdapter().also {
                childAdapter = it
                binding.viewPager.adapter = it
                binding.dotsIndicator.attachTo(binding.viewPager)
            }
            adapter.submitList(banners)

            startAutoAdvance()

            binding.viewPager.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) binding.viewPager.focusPage(binding.viewPager.currentItem)
            }
        }

        fun startAutoAdvance() {
            handler.removeCallbacks(autoAdvance)
            handler.postDelayed(autoAdvance, AUTO_ADVANCE_MS)
        }

        fun stopAutoAdvance() = handler.removeCallbacks(autoAdvance)

        private companion object {
            const val AUTO_ADVANCE_MS = 8000L
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
                LocalData.listenerItemBanner?.invoke(
                    item
                )

            }
            val activity = binding.root.context as? Activity
            if (activity == null || activity.isDestroyed || activity.isFinishing) {
                return
            }
            bindGenres(item)

            val imageUrl = if (item.contentItem.isMovie) {
                "${LocalData.IMDB_BACKDROP_PATH}${item.contentItem.image}"
            } else {
                item.contentItem.image
            }
            Glide.with(MyApp.context).load(GlideUrl(imageUrl))
                .diskCacheStrategy(DiskCacheStrategy.ALL).into(binding.bannerImg)

            binding.title.text = item.contentItem.title
            binding.description.text = item.contentItem.description
            binding.root.setOnKeyListener { _, keyCode, event ->
                if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                val pager = binding.root.findPager() ?: return@setOnKeyListener false
                val last = (pager.adapter?.itemCount ?: 0) - 1
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (pager.currentItem < last) pager.goToPage(pager.currentItem + 1)
                        true
                    }

                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (pager.currentItem > 0) {
                            pager.goToPage(pager.currentItem - 1)
                            true
                        } else {
                            false // first banner: let focus fall out to the navigation rail
                        }
                    }

                    else -> false
                }
            }

        }

        private fun bindGenres(item: BannerItem) {
            val container = binding.genreButtons
            container.removeAllViews()
            // Capped at three: the chips share the bottom edge with the page dots,
            // and a five-genre movie runs straight under them.
            val genres = if (item.contentItem.isMovie) {
                item.contentItem.genre_ids.orEmpty()
                    .mapNotNull { id -> LocalData.genreTmdb.find { it.id == id } }
                    .take(3)
            } else {
                emptyList()
            }
            container.isVisible = genres.isNotEmpty()
            val res = container.resources
            val chipPadding = res.getDimensionPixelSize(R.dimen.spacing_small)
            val chipGap = res.getDimensionPixelSize(R.dimen.spacing_xs)
            genres.forEach { genre ->
                val chip = TextView(container.context).apply {
                    text = genre.title
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, res.getDimension(R.dimen.tv_text_label))
                    setTextColor(Color.WHITE)
                    setPadding(chipPadding, chipPadding, chipPadding, chipPadding)
                    background = ContextCompat.getDrawable(context, R.drawable.background_button)
                    ellipsize = TextUtils.TruncateAt.END
                    maxLines = 1
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { marginEnd = chipGap }
                }
                container.addView(chip)
            }
        }

        /**
         * Focus has to follow the page or it dies with the one scrolled off. The
         * cut is deliberate: animating it fights the RecyclerView pulling the newly
         * focused page back on screen.
         */
        private fun ViewPager2.goToPage(target: Int) {
            setCurrentItem(target, false)
            post { focusPage(target) }
        }

        private fun View.findPager(): ViewPager2? {
            var p: ViewParent? = parent
            while (p != null) {
                if (p is ViewPager2) return p
                p = p.parent
            }
            return null
        }
    }

    class GenreViewHolder(private val binding: ItemCategoryBinding) :
        RecyclerView.ViewHolder(binding.root) {
        private val childAdapter = HomeAdapter()
        fun bind(item: CategoryGenre) {
            binding.tvCategoryTitle.text = item.name
            binding.hgvCategory.apply {
                setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT)

                // Reuse ONE child adapter so the row keeps its horizontal scroll/focus
                // position across recycling; only submitList (DiffUtil) on rebind.
                if (adapter !== childAdapter) adapter = childAdapter
                childAdapter.submitList(item.list)

                setItemSpacing(resources.getDimensionPixelSize(R.dimen.home_spacing))
            }
        }
    }

    class GenreItemViewHolder(private val binding: ItemGenreBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: CategoryGenreItem) {
            Glide.with(binding.root.context).load(item.content.image).into(binding.imgGenre)
            binding.topContainer.text = item.content.title
            binding.root.applyTvFocusScale()
            binding.root.setOnClickListener {
                LocalData.sFocusedGenreClickListener?.invoke(item.content.title)
            }
        }
    }

    class ChannelViewHolder(private val binding: ItemCategoryBinding) :
        RecyclerView.ViewHolder(binding.root) {
        private val childAdapter = HomeAdapter()
        fun bind(item: CategoryChannel) {
            binding.tvCategoryTitle.text = item.name
            binding.hgvCategory.apply {
                setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT)

                if (adapter !== childAdapter) adapter = childAdapter
                childAdapter.submitList(item.list)

                setItemSpacing(resources.getDimensionPixelSize(R.dimen.home_spacing))
            }
        }
    }

    class HistoryViewHolder(private val binding: ItemCategoryBinding) :
        RecyclerView.ViewHolder(binding.root) {
        private val childAdapter = HomeAdapter()
        fun bind(item: HistoryHome) {
            binding.tvCategoryTitle.text = item.name
            binding.hgvCategory.apply {
                setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT)
                if (adapter !== childAdapter) adapter = childAdapter
                childAdapter.submitList(item.list)

                setItemSpacing(resources.getDimensionPixelSize(R.dimen.home_spacing))
            }
        }
    }

    class HistoryItemViewHolder(private val binding: EpisodeItemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        @SuppressLint("SetTextI18n")
        fun onBind(item: HistoryHomeItem) {
            binding.apply {
                val getLocalEp = item.content
                binding.topContainer.text = getLocalEp.title
                val total = getLocalEp.totalDuration.toInt()
                progressBar.isVisible = total > 0
                if (total > 0) {
                    progressBar.max = total
                    progressBar.progress = getLocalEp.lastPosition.toInt().coerceIn(0, total)
                }
                binding.country.text = if (getLocalEp.currentSourceName.isNotEmpty()) {
                    "Ep: ${getLocalEp.epIndex + 1} || ${getLocalEp.currentSourceName}"
                } else {
                    "Episode ${getLocalEp.epIndex + 1}"
                }
                binding.root.applyTvFocusScale()
                binding.root.setOnClickListener {
                    LocalData.historyItemClickListenerr?.invoke(getLocalEp)
                }
                itemImg.loadImage(getLocalEp.image)

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
                LocalData.channnelItemClickListener?.invoke(item.content)
            }
            binding.root.applyTvFocusScale()

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
                applyTvFocusScale()
                setOnClickListener {
                    LocalData.listenerItemCategory?.invoke(item)
                }
            }
            // Was "SOURCE · <internal id> · FORMAT" - the id is a database key the
            // viewer has no use for, and source is a constant for every extension card.
            binding.genreTv.text = item.content.format.name
                .takeUnless { it.startsWith("UNKNOWN") }.orEmpty().replace('_', ' ')
            binding.itemImg.loadImage(item.content.coverImage.large)

        }
    }

    /**
     * Kategoriya elementlarini ko‘rsatish uchun ViewHolder.
     */
    class ItemCategoryViewHolder(private val binding: ItemCategoryBinding) :
        RecyclerView.ViewHolder(binding.root) {
        private val childAdapter = HomeAdapter()
        fun bind(item: Category) {
            binding.tvCategoryTitle.text = item.name

            val listWithViewAll: List<HomeData> = item.list + ViewAllData(
                rowId = item.rowId,
                categoryTitle = item.name,
                slug = item.slug
            )

            binding.hgvCategory.apply {
                setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT)
                if (adapter !== childAdapter) adapter = childAdapter
                childAdapter.submitList(listWithViewAll)
                setItemSpacing(resources.getDimensionPixelSize(R.dimen.home_spacing))
            }
        }
    }

    /**
     * "Barchasini ko'rish" kartasi uchun ViewHolder.
     * Bosilganda CategoryActivity ga o'tadi.
     */
    class ViewAllItemViewHolder(private val binding: ItemViewAllBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ViewAllData) {
            binding.root.apply {
                applyTvFocusScale()
                setOnClickListener {
                    LocalData.viewAllClickListenerrr?.invoke(item)
                }
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
                        // idMal is -1 for every extension-provided card, which made them
                        // all compare equal; id is the stable per-card identity.
                        oldItem.content.id == newItem.content.id
                    }

                    oldItem is CategoryGenreItem && newItem is CategoryGenreItem -> {
                        oldItem.content.image == newItem.content.image
                    }

                    oldItem is CategoryChannelItem && newItem is CategoryChannelItem -> {
                        oldItem.content.playLink == newItem.content.playLink
                    }

                    oldItem is HistoryHomeItem && newItem is HistoryHomeItem -> {
                        oldItem.content.categoryid == newItem.content.categoryid &&
                                oldItem.content.epIndex == newItem.content.epIndex
                    }

                    oldItem is ViewAllData && newItem is ViewAllData -> {
                        oldItem.rowId == newItem.rowId
                    }

                    oldItem is CategoryGenre && newItem is CategoryGenre -> {
                        oldItem.name == newItem.name
                    }

                    oldItem is CategoryChannel && newItem is CategoryChannel -> {
                        oldItem.name == newItem.name
                    }

                    oldItem is HistoryHome && newItem is HistoryHome -> {
                        oldItem.name == newItem.name
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
