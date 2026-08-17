package com.saikou.sozo_tv.presentation.screens.detail

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.CountDownTimer
import android.text.Html
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.adapters.CastAdapter
import com.saikou.sozo_tv.data.local.pref.PreferenceManager
import com.saikou.sozo_tv.databinding.ItemPlayCastBinding
import com.saikou.sozo_tv.databinding.ItemPlayDetailsHeaderBinding
import com.saikou.sozo_tv.databinding.ItemPlayDetailsSectionBinding
import com.saikou.sozo_tv.databinding.ItemPlayRecommendedBinding
import com.saikou.sozo_tv.domain.model.Cast
import com.saikou.sozo_tv.domain.model.DetailCategory
import com.saikou.sozo_tv.domain.model.MainModel
import com.saikou.sozo_tv.presentation.screens.category.CategoriesPageAdapter
import com.saikou.sozo_tv.presentation.screens.home.HomeAdapter
import com.saikou.sozo_tv.presentation.screens.home.vh.ViewHolderFactory
import com.saikou.sozo_tv.utils.LocalData
import com.saikou.sozo_tv.utils.LocalData.bookmark
import com.saikou.sozo_tv.utils.LocalData.castList
import com.saikou.sozo_tv.utils.LocalData.recommendedMovies
import com.saikou.sozo_tv.utils.LocalData.trailer
import com.saikou.sozo_tv.utils.gone
import com.saikou.sozo_tv.utils.loadImage
import com.saikou.sozo_tv.utils.toYear
import com.saikou.sozo_tv.utils.visible
import kotlin.math.abs
import kotlin.random.Random

class MovieDetailsAdapter(
    val itemList: MutableList<HomeAdapter.HomeData> = mutableListOf(),
    private val detailsButtonListener: DetailsInterface
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    interface DetailsInterface {
        fun onCancelButtonClicked()
        fun onCastItemClicked(item: Cast)
        fun onBookMarkClicked(itme: DetailCategory, bookmark: Boolean)
        fun onSoundButtonClicked(isOn: Boolean)
        fun onPauseButtonClicked(isPlay: Boolean)
        fun onWatchButtonClicked(
            item: DetailCategory,
            id: Int,
            url: String,
            title: String,
            isFree: Boolean,

            )

        fun onTrailerButtonClicked(item: DetailCategory)
    }

    companion object {
        const val DETAILS_ITEM_HEADER = 11
        const val DETAILS_ITEM_SECTION = 12
        const val DETAILS_ITEM_THIRD = 13
        const val DETAILS_ITEM_FOUR = 14
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
                holder.bind(castList, interfaceListener = detailsButtonListener)
            }
        }

    }

    class ItemPlayDetailsThirdViewHolder(private val binding: ItemPlayRecommendedBinding) :
        RecyclerView.ViewHolder(binding.root) {
        private val recommendedAdapter = CategoriesPageAdapter(isDetail = true)
        fun bind() {
            recommendedAdapter.setClickDetail {
                LocalData.focusChangedListenerPlayerg.invoke(it)
            }
            recommendedAdapter.updateCategoriesAll(recommendedMovies as ArrayList<MainModel>)
            recommendedAdapter.setCategoriesPageInterface(object :
                CategoriesPageAdapter.CategoriesPageInterface {
                override fun onCategorySelected(category: MainModel, position: Int) {
                }

            })
            // Reuse one adapter so async notifyItemChanged / recycling doesn't reset the
            // recommended row's scroll + focus.
            if (binding.recommendedRv.adapter !== recommendedAdapter) binding.recommendedRv.adapter = recommendedAdapter
            if (recommendedMovies.isEmpty()) {
                binding.textView5.gone()
            } else {
                binding.textView5.visible()
            }
        }
    }

    class ItemPlayDetailsSectionViewHolder(
        private val binding: ItemPlayDetailsSectionBinding,
    ) :
        RecyclerView.ViewHolder(binding.root) {
        private var currentLayoutId: Int? = null
        private var currentItem: DetailCategory? = null

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
                    Log.d("GGG", "setFocusChangeListener:${layoutResId} || $currentLayoutId ")
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
        private fun updateTextViews() {
            val yearContainer =
                binding.frame.findViewById<LinearLayout>(R.id.container_date) ?: null
            val countryContainer =
                binding.frame.findViewById<LinearLayout>(R.id.container_country) ?: null
            val genresContainer =
                binding.frame.findViewById<LinearLayout>(R.id.container_janr) ?: null
            val languageContainer =
                binding.frame.findViewById<LinearLayout>(R.id.language) ?: null
            val image = binding.frame.findViewById<ImageView>(R.id.film_image) ?: null
            val countDown = binding.frame.findViewById<TextView>(R.id.mediaCountdownText)
            val countDownText = binding.frame.findViewById<TextView>(R.id.mediaCountdown)
            if (currentItem?.content?.airingSchedule?.episode != -1 && currentItem?.content?.airingSchedule?.timeUntilAiring!! > 0) {
                countDown.visible()
                countDownText.visible()
                val timeInSeconds = currentItem?.content?.airingSchedule?.timeUntilAiring ?: 0
                countDown.text =
                    "Episode ${currentItem?.content?.airingSchedule?.episode} will be released in"
                val totalMillis = abs(timeInSeconds) * 1000L

                object : CountDownTimer(totalMillis, 1000) {
                    override fun onTick(millisUntilFinished: Long) {
                        countDownText.text = formatCountdown(millisUntilFinished / 1000)
                    }

                    override fun onFinish() {
                        countDownText.text = "Aired!"
                    }
                }.start()
            } else {
                countDown.gone()
                countDownText.gone()
            }

            currentItem?.let { item ->
                val descriptionTextView =
                    binding.frame.findViewById<TextView>(R.id.film_description_tv)
                descriptionTextView?.movementMethod = LinkMovementMethod.getInstance()
                if (LocalData.isAnimeEnabled) {
                    descriptionTextView?.text =
                        Html.fromHtml(item.content.description, Html.FROM_HTML_MODE_COMPACT)
                } else {
                    descriptionTextView.text = Html.fromHtml(
                        item.content.description + item.content.description + item.content.description + item.content.description,
                        Html.FROM_HTML_MODE_COMPACT
                    )
                }
                descriptionTextView?.isFocusable = false
                // Legacy "About Movie" metadata grid labels.
                val labelDate = binding.frame.findViewById<TextView?>(R.id.textView8)
                val labelCountry = binding.frame.findViewById<TextView?>(R.id.textView4)
                val labelLanguage = binding.frame.findViewById<TextView?>(R.id.textView9)
                val labelGenre = binding.frame.findViewById<TextView?>(R.id.labelGenre)

                languageContainer?.removeAllViews()
                countryContainer?.removeAllViews()
                yearContainer?.removeAllViews()
                genresContainer?.removeAllViews()

                // Description — hide when the provider gives none.
                val descriptionTextView2 =
                    binding.frame.findViewById<TextView?>(R.id.film_description_tv)
                descriptionTextView2?.isVisible = !item.content.description.isNullOrBlank()

                // Release Year / Studio / Category / Language are ALREADY rendered as chips in the
                // header (categoryContainer). Re-rendering them here produced a second metadata
                // layer that overlapped the header chips (faint "Studio"/"Category" labels ghosting
                // behind "Action / Adventure / Comedy"). Hide the whole duplicate grid so only the
                // header chips remain.
                labelDate?.isVisible = false
                yearContainer?.isVisible = false
                labelCountry?.isVisible = false
                countryContainer?.isVisible = false
                labelLanguage?.isVisible = false
                languageContainer?.isVisible = false
                labelGenre?.isVisible = false
                genresContainer?.isVisible = false

                image?.loadImage(item.content.coverImage.large)
            }

        }

        private fun formatCountdown(secondsInput: Long): String {
            var seconds = secondsInput
            val days = seconds / (24 * 3600)
            seconds %= 24 * 3600
            val hours = seconds / 3600
            seconds %= 3600
            val minutes = seconds / 60
            val secs = seconds % 60

            return "${days}d ${hours}h ${minutes}m ${secs}s"
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
        private var initialFocusDone = false

        @SuppressLint("SetTextI18n")
        fun bind(item: DetailCategory, interfaceListener: DetailsInterface) {
            val preferenceManager = PreferenceManager()
            binding.backBtn.setOnClickListener {
                interfaceListener.onCancelButtonClicked()
            }
            binding.bookmark.setOnClickListener {
                interfaceListener.onBookMarkClicked(item, bookmark)
            }
            binding.icBookmark.setImageResource(if (bookmark) R.drawable.ic_bookmark_fill else R.drawable.ic_bookmark)

            binding.watchButton.setOnClickListener {
                item.content.id.let {
                    interfaceListener.run {
                        onWatchButtonClicked(
                            item,
                            id = item.content.id,
                            url = item.content.bannerImage,
                            title = item.content.title,
                            isFree = true
                        )
                    }
                }
            }
            binding.filmDescriptionTv.text =
                item.content.description + " " + item.content.description + " " + item.content.description
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
            fun chip(label: String): TextView = TextView(binding.root.context).apply {
                text = label
                textSize = 12f
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { rightMargin = 7 * resources.displayMetrics.density.toInt() }
                setBackgroundResource(R.drawable.bg_cat_tv)
                setPadding(18, 10, 18, 10)
            }

            val genres = item.content.genres
                ?.filterNotNull()?.map { it.trim() }?.filter { it.isNotEmpty() }
                ?: emptyList()

            val container = binding.categoryContainer
            container.removeAllViews()

            // Real release year — omitted when the provider doesn't supply one.
            item.content.seasonYear?.let { if (it > 0) container.addView(chip(it.toString())) }
            genres.forEach { container.addView(chip(it)) }
            // Episode count — only when known.
            item.content.episodes?.let { if (it > 0) container.addView(chip("Episodes: $it")) }
            container.isVisible = container.childCount > 0

            binding.filmTitleTv.text = item.content.title
            binding.filmDescriptionTv.text = item.content.description
            binding.filmDescriptionTv.isVisible = !item.content.description.isNullOrBlank()

            // Land initial D-pad focus on Watch, not the Back button (leanback would otherwise
            // pick backBtn as the first focusable descendant). One-shot so bookmark toggles
            // (notifyItemChanged(0)) don't steal focus back to Watch every time.
            if (!initialFocusDone) {
                initialFocusDone = true
                binding.watchButton.post {
                    if (!binding.root.hasFocus()) binding.watchButton.requestFocus()
                }
            }
        }
    }

    class ItemPlayCastViewHolder(private val binding: ItemPlayCastBinding) :
        RecyclerView.ViewHolder(binding.root) {
        private val castAdapter = CastAdapter()
        @SuppressLint("SetTextI18n")
        fun bind(castREsponse: List<Cast>, interfaceListener: DetailsInterface) {
            castAdapter.setOnItemClickListener {
                interfaceListener.onCastItemClicked(it)
            }
            // Always clear the spinner once cast has resolved (empty OR not). Some providers —
            // notably the extension sources — don't expose character data, so an empty list is a
            // valid terminal state, not "still loading". Collapse the row instead of spinning.
            binding.castProgress.gone()
            if (castREsponse.isEmpty()) {
                binding.castRv.gone()
                binding.root.gone()
            } else {
                binding.root.visible()
                binding.castRv.visible()
            }
            castAdapter.submitCast(castREsponse)
            // Reuse one adapter so async notifyItemChanged / row recycling doesn't reset the
            // cast row's horizontal scroll + focused cell.
            if (binding.castRv.adapter !== castAdapter) binding.castRv.adapter = castAdapter
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

                return oldItem is DetailCategory && newItem is DetailCategory &&
                        oldItem.viewType == newItem.viewType &&
                        oldItem.content.id == newItem.content.id
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

    fun updateTrailer(it: String) {
        trailer = it
        notifyItemChanged(0)
    }

    fun updateBookmark(it: Boolean?) {
        bookmark = it ?: false
        notifyItemChanged(0)
    }
//
//    fun submitCast(cast: CastResponse?) {
//        this.castResponse.clear()
//        this.castResponse.addAll(cast!!.cast!!)
//        notifyItemChanged(3)
//    }
}