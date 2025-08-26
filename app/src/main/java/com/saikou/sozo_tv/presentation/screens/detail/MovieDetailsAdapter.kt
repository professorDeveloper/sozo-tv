package com.saikou.sozo_tv.presentation.screens.detail

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.text.Html
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.adapters.CastAdapter
import com.saikou.sozo_tv.databinding.ItemPlayCastBinding
import com.saikou.sozo_tv.databinding.ItemPlayDetailsHeaderBinding
import com.saikou.sozo_tv.databinding.ItemPlayDetailsSectionBinding
import com.saikou.sozo_tv.databinding.ItemPlayRecommendedBinding
import com.saikou.sozo_tv.domain.model.Cast
import com.saikou.sozo_tv.domain.model.CategoryDetails
import com.saikou.sozo_tv.domain.model.DetailCategory
import com.saikou.sozo_tv.domain.model.MainModel
import com.saikou.sozo_tv.presentation.screens.category.CategoriesPageAdapter
import com.saikou.sozo_tv.presentation.screens.home.HomeAdapter
import com.saikou.sozo_tv.presentation.screens.home.vh.ViewHolderFactory
import com.saikou.sozo_tv.utils.LocalData
import com.saikou.sozo_tv.utils.LocalData.castList
import com.saikou.sozo_tv.utils.LocalData.recommendedMovies
import com.saikou.sozo_tv.utils.gone
import com.saikou.sozo_tv.utils.loadImage
import com.saikou.sozo_tv.utils.toYear
import com.saikou.sozo_tv.utils.visible
import kotlin.random.Random

class MovieDetailsAdapter(
    val itemList: MutableList<HomeAdapter.HomeData> = mutableListOf(),
    private val detailsButtonListener: DetailsInterface
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    // Placeholder for the third ite
//    val castResponse = mutableListOf<CastItem>()

    interface DetailsInterface {
        fun onCancelButtonClicked()
        fun onBookMarkClicked(itme: DetailCategory)
        fun onSoundButtonClicked(isOn: Boolean)
        fun onPauseButtonClicked(isPlay: Boolean)
        fun onWatchButtonClicked(
            item: DetailCategory,
            id: Int,
            url: String,
            title: String,
            isFree: Boolean
        )

        fun onTrailerButtonClicked(item: DetailCategory)
    }

    companion object {
        const val DETAILS_ITEM_HEADER = 11
        const val DETAILS_ITEM_SECTION = 12
        const val DETAILS_ITEM_THIRD = 13 // New ViewType for the third item
        const val DETAILS_ITEM_FOUR = 14 // New ViewType for the third item
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            DETAILS_ITEM_HEADER -> ViewHolderFactory.create(parent, viewType)
            DETAILS_ITEM_SECTION -> ViewHolderFactory.create(parent, viewType)
            DETAILS_ITEM_THIRD -> {
                val binding = ItemPlayCastBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                ItemPlayCastViewHolder(binding)
            }

            DETAILS_ITEM_FOUR -> {
                val binding = ItemPlayRecommendedBinding.inflate(
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
                if (item is DetailCategory && item.viewType == DETAILS_ITEM_HEADER) {
                    holder.bind(item, interfaceListener = detailsButtonListener)
                }
            }

            is ItemPlayDetailsSectionViewHolder -> {
                if (item is DetailCategory && item.viewType == DETAILS_ITEM_SECTION) {
                    holder.bind(item)
                }
            }

            is ItemPlayDetailsThirdViewHolder -> {
                holder.bind()
            }

            is ItemPlayCastViewHolder -> {
                holder.bind(castList)
            }
        }

    }


    class ItemPlayDetailsThirdViewHolder(private val binding: ItemPlayRecommendedBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind() {
            val adapter = CategoriesPageAdapter(isDetail = true)
            adapter.setClickDetail {
                LocalData.focusChangedListenerPlayerg.invoke(it)
            }
            Log.d("REcommmended List", "updateTextViews:${recommendedMovies} ")
            adapter.updateCategoriesAll(recommendedMovies as ArrayList<MainModel>)
            adapter.setCategoriesPageInterface(object :
                CategoriesPageAdapter.CategoriesPageInterface {
                override fun onCategorySelected(category: MainModel, position: Int) {
                }

            })
            binding.recommendedRv.adapter = adapter
        }
    }

    class ItemPlayDetailsSectionViewHolder(
        private val binding: ItemPlayDetailsSectionBinding,
    ) :
        RecyclerView.ViewHolder(binding.root) {
        private var currentLayoutId: Int? = null
        private var currentItem: DetailCategory? = null
        private var isAboutFilmMode = true

        init {
            setFocusChangeListener(
                binding.aboutFilmTv,
                binding.indicator1,
                R.layout.item_container_about_film
            )

        }

        fun bind(item: DetailCategory) {
            replaceLayout(R.layout.item_container_about_film, binding.root.context)
            currentItem = item
            if (currentLayoutId != R.layout.item_container_about_film) {
                replaceLayout(R.layout.item_container_about_film, binding.root.context)
            } else {
                updateTextViews()
            }
        }

        private fun setFocusChangeListener(view: View, indicator: View, layoutResId: Int) {
            view.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    Log.d("GGG", "setFocusChangeListener:${layoutResId} || ${currentLayoutId} ")
                    if (currentLayoutId != layoutResId) {
                        indicator.visibility = View.VISIBLE
                        replaceLayout(layoutResId, binding.root.context)
                        updateTextViews()
                    }
                } else {
                    indicator.visibility = View.INVISIBLE
                }
            }
        }

        private fun replaceLayout(layoutResId: Int, context: Context) {
            binding.frame.removeAllViews()
            View.inflate(context, layoutResId, binding.frame)
            currentLayoutId = layoutResId
        }

        @SuppressLint("SetTextI18n", "NewApi")
        private fun updateTextViews(isAbout: Boolean = true) {
            val yearContainer =
                binding.frame.findViewById<LinearLayout>(R.id.container_date) ?: null
            val countryContainer =
                binding.frame.findViewById<LinearLayout>(R.id.container_country) ?: null
            val genresContainer =
                binding.frame.findViewById<LinearLayout>(R.id.container_janr) ?: null
            val languageContainer =
                binding.frame.findViewById<LinearLayout>(R.id.language) ?: null
            val image = binding.frame.findViewById<ImageView>(R.id.film_image) ?: null

            currentItem?.let { item ->
                val descriptionTextView =
                    binding.frame.findViewById<TextView>(R.id.film_description_tv)
                descriptionTextView?.movementMethod = LinkMovementMethod.getInstance()
                descriptionTextView?.text =
                    Html.fromHtml(item.content.description, Html.FROM_HTML_MODE_COMPACT)
                descriptionTextView?.isFocusable = false
//                descriptionTextView?.isfocu
                languageContainer?.removeAllViews()
                countryContainer?.removeAllViews()
                yearContainer?.removeAllViews()
                genresContainer?.removeAllViews()
                if (currentItem?.content?.studios?.isNotEmpty() == true) {
                    val countryTextView = createCategoryTextView(
                        binding.root.context,
                        currentItem?.content?.studios?.get(0) ?: "Unknown"
                    )
                    countryContainer?.addView(countryTextView)
                } else {
                    val countryTextView = createCategoryTextView(
                        binding.root.context,
                        "Japan"
                    )
                    countryContainer?.addView(countryTextView)

                }
                val textView = createCategoryTextView(
                    binding.root.context,
                    text = currentItem?.content?.mediaSource?.name ?: "Unknown"
                )
                languageContainer?.addView(textView)
                val year = createCategoryTextView(
                    binding.root.context,
                    LocalData.years[Random.nextInt(0, LocalData.years.size)]!!.title.toYear()
                        .toString()
                )
                yearContainer?.addView(year)
                image?.loadImage(item.content.coverImage.large)


                if (currentItem?.content?.genres?.isNotEmpty() == true) {
                    currentItem?.content?.genres?.forEach { category ->
                        val trimmedCategory = category?.trim()

                        val genreTextView =
                            createCategoryTextView(
                                binding.root.context,
                                trimmedCategory ?: "Unknown"
                            )
                        genresContainer?.addView(genreTextView)
                    }
                }
            }


        }


        private fun createCategoryTextView(context: Context, text: String): TextView {
            return TextView(context).apply {
                this.text = text
                textSize = 10f
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    rightMargin = (7 * resources.displayMetrics.density).toInt()
                }
                setBackgroundResource(R.drawable.bg_cat_tv)
                setPadding(18, 10, 18, 10)
            }
        }
    }


    class ItemPlayDetailsHeaderViewHolder(private val binding: ItemPlayDetailsHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        private var isOn = false
        private var isPlay = true

        @SuppressLint("SetTextI18n")
        fun bind(item: DetailCategory, interfaceListener: DetailsInterface) {

            binding.backBtn.setOnClickListener {
                interfaceListener.onCancelButtonClicked()
            }
            binding.bookmark.setOnClickListener {
                interfaceListener.onBookMarkClicked(item)
            }
            binding.icBookmark.setImageResource(if (item.isBookmarked) R.drawable.ic_bookmark_fill else R.drawable.ic_bookmark)

            binding.watchButton.setOnClickListener {
                item.content.id?.let { it1 ->

                    interfaceListener.onWatchButtonClicked(
                        item,
                        id = item.content.id,
                        url = item.content.bannerImage ?: "",
                        title = item.content.title,
                        isFree = item.content.title.isEmpty()
                    )
                }
            }
            binding.filmDescriptionTv.text =
                item.content.description + " " + item.content.description + " " + item.content.description
            binding.trailerWatchButton.setOnClickListener {
                interfaceListener.onTrailerButtonClicked(item)
            }
            binding.buttonSound.setOnClickListener {
                if (!isOn) binding.iconSound.setImageResource(R.drawable.ic_sound) else binding.iconSound.setImageResource(
                    R.drawable.ic_no_sound
                )
                isOn = !isOn
                interfaceListener.onSoundButtonClicked(isOn)
            }

            binding.buttonPlay.setOnClickListener {
                if (!isPlay) binding.iconPlay.setImageResource(R.drawable.ic_play) else binding.iconPlay.setImageResource(
                    R.drawable.ic_play_for_button
                )
                isPlay = !isPlay
                interfaceListener.onPauseButtonClicked(isPlay)
            }
            val genres = if (item.content.genres?.isEmpty() == true) LocalData.genres.subList(
                0,
                3
            ) else item.content.genres ?: emptyList<String>()
            val date = LocalData.years[Random.nextInt(1, LocalData.years.size)].title.toYear()

            val container = binding.categoryContainer
            container.removeAllViews()
            val textView = TextView(binding.root.context).apply {
                text = date
                textSize = 12f
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    rightMargin = 7 * resources.displayMetrics.density.toInt()
                }
                setBackgroundResource(R.drawable.bg_cat_tv)
                setPadding(18, 10, 18, 10)
            }
            container.addView(textView)
            genres.forEach { category ->
                val textView = TextView(binding.root.context).apply {
                    text = category
                    textSize = 12f
                    setTextColor(Color.WHITE)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        rightMargin = 7 * resources.displayMetrics.density.toInt()
                    }
                    setBackgroundResource(R.drawable.bg_cat_tv)
                    setPadding(16, 8, 16, 8)
                }
                container.addView(textView)
            }

            binding.filmTitleTv.text = item.content.title
            binding.filmDescriptionTv.text = item.content.description


        }
    }


    class ItemPlayCastViewHolder(private val binding: ItemPlayCastBinding) :
        RecyclerView.ViewHolder(binding.root) {
        @SuppressLint("SetTextI18n")
        fun bind(castREsponse: List<Cast>) {
            binding.root.visible()
            val castAdapter = CastAdapter()
            if (castREsponse.isEmpty()) {
                binding.root.gone()
                binding.castRv.visibility = View.INVISIBLE
                binding.castProgress.visible()
            } else {
                binding.root.visible()
                binding.castRv.visible()
                binding.castProgress.gone()
            }
            castAdapter.submitCast(castREsponse)
            binding.castRv.adapter = castAdapter
        }
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
                    oldItem is CategoryDetails && newItem is CategoryDetails && oldItem.viewType == DETAILS_ITEM_HEADER && newItem.viewType == DETAILS_ITEM_HEADER -> {
                        oldItem.content.id == newItem.content.id
                    }

                    oldItem is CategoryDetails && newItem is CategoryDetails && oldItem.viewType == DETAILS_ITEM_SECTION && newItem.viewType == DETAILS_ITEM_SECTION -> {
                        oldItem.content.id == newItem.content.id
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

    //
    fun submitRecommendedMovies(movies: List<MainModel>) {
        recommendedMovies.clear()
        recommendedMovies.addAll(movies)
        notifyItemChanged(3)
    }

    fun submitCast(cast: List<Cast>) {
        castList.clear()
        castList.addAll(cast)
        notifyItemChanged(2)
    }
//
//    fun submitCast(cast: CastResponse?) {
//        this.castResponse.clear()
//        this.castResponse.addAll(cast!!.cast!!)
//        notifyItemChanged(3)
//    }
}