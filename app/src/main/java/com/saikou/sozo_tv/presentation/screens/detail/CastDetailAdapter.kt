package com.saikou.sozo_tv.presentation.screens.detail

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.Shape
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AnimationUtils
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.palette.graphics.Palette
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.app.MyApp
import com.saikou.sozo_tv.databinding.ItemCastRecommendedBinding
import com.saikou.sozo_tv.databinding.ItemCategoryDetailsHeaderBinding
import com.saikou.sozo_tv.databinding.ItemPlayDetailsSectionBinding
import com.saikou.sozo_tv.domain.model.CastAdapterModel
import com.saikou.sozo_tv.domain.model.MainModel
import com.saikou.sozo_tv.presentation.screens.category.CategoriesPageAdapter
import com.saikou.sozo_tv.presentation.screens.home.HomeAdapter
import com.saikou.sozo_tv.presentation.screens.home.vh.ViewHolderFactory
import com.saikou.sozo_tv.utils.LocalData
import com.saikou.sozo_tv.utils.LocalData.characterBookmark
import com.saikou.sozo_tv.utils.LocalData.recommendedMoviesCast
import com.saikou.sozo_tv.utils.gone
import com.saikou.sozo_tv.utils.loadImage
import com.saikou.sozo_tv.utils.setupGridLayoutForCategories
import com.saikou.sozo_tv.utils.visible
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class CastDetailAdapter(
    val itemList: MutableList<HomeAdapter.HomeData> = mutableListOf(),
    private val detailsButtonListener: DetailsInterface
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    interface DetailsInterface {
        fun onCancelButtonClicked()
        fun onFavoriteButtonClicked(item: CastAdapterModel)
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
//        private var isOn = false
//        private var isPlay = true

        @SuppressLint("SetTextI18n")
        fun bind(item: CastAdapterModel, interfaceListener: DetailsInterface) {
            binding.backBtn.setOnClickListener {
                interfaceListener.onCancelButtonClicked()
            }
            binding.favoriteBtn.setOnClickListener {
                interfaceListener.onFavoriteButtonClicked(item)
            }
            binding.favoriteBtn.setOnFocusChangeListener { view, hasFocus ->
                val animation = when {
                    hasFocus -> AnimationUtils.loadAnimation(
                        binding.favoriteBtn.context,
                        R.anim.zoom_in
                    )

                    else -> AnimationUtils.loadAnimation(
                        binding.favoriteBtn.context,
                        R.anim.zoom_out
                    )
                }
                binding.favoriteBtn.startAnimation(animation)
                animation.fillAfter = true
            }
            binding.favoriteBtn.animate()
                .scaleX(1.05f)
                .scaleY(1.05f)
                .setDuration(200)
                .withEndAction {
                    binding.favoriteBtn.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(200)
                        .start()
                }
                .start()

            if (characterBookmark) {
                binding.favoriteBtn.setImageResource(R.drawable.ic_favorite_filled)
            } else {
                binding.favoriteBtn.setImageResource(R.drawable.ic_favorite_outline)
            }

            if (item.age.isNotEmpty()) {
                binding.ageBadge.visible()
                binding.characterAge.text = "Age: ${item.age}"
                binding.ageBadge.animate()
                    .scaleX(1.05f)
                    .scaleY(1.05f)
                    .setDuration(200)
                    .withEndAction {
                        binding.ageBadge.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(200)
                            .start()
                    }
                    .start()
            } else {
                binding.ageBadge.gone()
            }

            if (item.favorites != -1) {
                binding.favoritesBadge.visible()
                binding.favoritesCount.text = item.favorites.toString()
                // Add heart pulse animation
                binding.favoritesBadge.animate()
                    .scaleX(1.1f)
                    .scaleY(1.1f)
                    .setDuration(300)
                    .withEndAction {
                        binding.favoritesBadge.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(300)
                            .start()
                    }
                    .start()
            } else {
                binding.favoritesBadge.gone()
            }

            binding.characterImage.loadImage(item.image)
            binding.characterImage.alpha = 0f
            binding.characterImage.animate()
                .alpha(1f)
                .setDuration(500)
                .start()

            binding.characterName.text = item.name
            binding.characterName.alpha = 0f
            binding.characterName.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(600)
                .setStartDelay(200)
                .start()

            binding.characterLastName.text = item.gender.ifEmpty { item.media[0].title }
            binding.characterLastName.alpha = 0f
            binding.characterLastName.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(600)
                .setStartDelay(400)
                .start()

            Glide.with(MyApp.context).asBitmap().load(item.image)
                .into(object : CustomTarget<Bitmap>() {
                    override fun onResourceReady(
                        resource: Bitmap, transition: Transition<in Bitmap>?
                    ) {
                        val scaledBitmap = if (resource.width > 200 || resource.height > 200) {
                            Bitmap.createScaledBitmap(resource, 200, 200, true)
                        } else resource

                        Palette.from(scaledBitmap).generate { palette ->
                            val dominantColor =
                                palette?.getDominantColor(Color.BLACK) ?: Color.BLACK
                            val vibrantColor =
                                palette?.getVibrantColor(dominantColor) ?: dominantColor

                            val lightVibrantColor =
                                palette?.getLightVibrantColor(Color.WHITE) ?: Color.WHITE

                            val mainBg = ContextCompat.getColor(
                                binding.root.context, R.color.main_background
                            )

                            val gradient = GradientDrawable(
                                GradientDrawable.Orientation.TOP_BOTTOM,
                                intArrayOf(dominantColor, mainBg)
                            )
                            gradient.cornerRadius = 0f
                            binding.root.background = gradient

                            addStarAnimation(binding.root, lightVibrantColor)
                            addBottomStars(binding.root, lightVibrantColor)
                            addRightSideElements(binding.root, vibrantColor)

                            // Add character glow using dominant color
                            addCharacterGlow(binding.characterImage, dominantColor)
                        }
                    }

                    override fun onLoadCleared(placeholder: Drawable?) {
                        binding.root.clearAnimation()
                    }

                    private fun adjustColorAlpha(color: Int, alpha: Float): Int {
                        val a = (Color.alpha(color) * alpha).toInt()
                        val r = Color.red(color)
                        val g = Color.green(color)
                        val b = Color.blue(color)
                        return Color.argb(a, r, g, b)
                    }

                    private fun addStarAnimation(view: View, starColor: Int) {
                        val starCount = 8
                        val random = Random(System.currentTimeMillis())

                        for (i in 0 until starCount) {
                            val starType = random.nextInt(3) // 0: small, 1: medium, 2: large
                            val starSize = when (starType) {
                                0 -> 12
                                1 -> 16
                                2 -> 20
                                else -> 14
                            }

                            val star = View(view.context).apply {
                                layoutParams = ViewGroup.LayoutParams(starSize, starSize)
                                background = createStarDrawable(starColor, starType)
                                alpha = 0f
                                x =
                                    (view.width * 0.6f) + random.nextFloat() * (view.width * 0.4f - starSize)
                                y = random.nextFloat() * (view.height * 0.4f)
                                elevation = 4f
                            }

                            if (view is ViewGroup) {
                                view.addView(star)

                                // Enhanced twinkling animation with floating effect
                                val twinkleAnimator =
                                    ObjectAnimator.ofFloat(star, "alpha", 0f, 0.9f, 0f).apply {
                                        duration = 2000 + random.nextInt(1000).toLong()
                                        repeatCount = ObjectAnimator.INFINITE
                                        startDelay = random.nextInt(1500).toLong()
                                        interpolator = AccelerateDecelerateInterpolator()
                                    }

                                // Floating animation
                                val floatAnimator = ObjectAnimator.ofFloat(
                                    star, "translationY",
                                    0f, -15f + random.nextFloat() * 30f
                                ).apply {
                                    duration = 4000 + random.nextInt(2000).toLong()
                                    repeatCount = ObjectAnimator.INFINITE
                                    repeatMode = ObjectAnimator.REVERSE
                                    interpolator = LinearInterpolator()
                                }

                                // Subtle rotation for sparkle effect
                                val rotateAnimator =
                                    ObjectAnimator.ofFloat(star, "rotation", 0f, 360f).apply {
                                        duration = 6000 + random.nextInt(3000).toLong()
                                        repeatCount = ObjectAnimator.INFINITE
                                        interpolator = LinearInterpolator()
                                    }

                                AnimatorSet().apply {
                                    playTogether(twinkleAnimator, floatAnimator, rotateAnimator)
                                    start()
                                }
                            }
                        }
                    }

                    private fun addBottomStars(view: View, starColor: Int) {
                        val starCount = 6
                        val random = Random(System.currentTimeMillis())

                        for (i in 0 until starCount) {
                            val starType = random.nextInt(2)
                            val starSize = when (starType) {
                                0 -> 8
                                1 -> 12
                                else -> 10
                            }

                            val star = View(view.context).apply {
                                layoutParams = ViewGroup.LayoutParams(starSize, starSize)
                                background = createStarDrawable(starColor, starType)
                                alpha = 0f
                                x = random.nextFloat() * (view.width - starSize)
                                y =
                                    (view.height * 0.75f) + random.nextFloat() * (view.height * 0.25f - starSize)
                                elevation = 2f
                            }

                            if (view is ViewGroup) {
                                view.addView(star)

                                val twinkleAnimator =
                                    ObjectAnimator.ofFloat(star, "alpha", 0f, 0.6f, 0f).apply {
                                        duration = 3000 + random.nextInt(1500).toLong()
                                        repeatCount = ObjectAnimator.INFINITE
                                        startDelay = random.nextInt(2000).toLong()
                                        interpolator = AccelerateDecelerateInterpolator()
                                    }

                                val floatAnimator = ObjectAnimator.ofFloat(
                                    star, "translationX",
                                    0f, -10f + random.nextFloat() * 20f
                                ).apply {
                                    duration = 5000 + random.nextInt(2000).toLong()
                                    repeatCount = ObjectAnimator.INFINITE
                                    repeatMode = ObjectAnimator.REVERSE
                                    interpolator = LinearInterpolator()
                                }

                                AnimatorSet().apply {
                                    playTogether(twinkleAnimator, floatAnimator)
                                    start()
                                }
                            }
                        }
                    }

                    private fun addRightSideElements(view: View, elementColor: Int) {
                        val elementCount = 4
                        val random = Random(System.currentTimeMillis())

                        for (i in 0 until elementCount) {
                            val elementType = random.nextInt(3)
                            val elementSize = when (elementType) {
                                0 -> 6
                                1 -> 10
                                2 -> 14
                                else -> 8
                            }

                            val element = View(view.context).apply {
                                layoutParams = ViewGroup.LayoutParams(elementSize, elementSize)
                                background = when (elementType) {
                                    0 -> createDotDrawable(adjustColorAlpha(Color.WHITE, 0.7f))
                                    1 -> createStarDrawable(elementColor, 0)
                                    2 -> createSparkleDrawable(adjustColorAlpha(elementColor, 0.8f))
                                    else -> createDotDrawable(Color.WHITE)
                                }
                                alpha = 0f
                                x =
                                    (view.width * 0.85f) + random.nextFloat() * (view.width * 0.15f - elementSize)
                                y = (view.height * 0.3f) + random.nextFloat() * (view.height * 0.4f)
                                elevation = 3f
                            }

                            if (view is ViewGroup) {
                                view.addView(element)

                                val fadeAnimator =
                                    ObjectAnimator.ofFloat(element, "alpha", 0f, 0.8f, 0f).apply {
                                        duration = 2500 + random.nextInt(1000).toLong()
                                        repeatCount = ObjectAnimator.INFINITE
                                        startDelay = random.nextInt(1000).toLong()
                                        interpolator = AccelerateDecelerateInterpolator()
                                    }

                                val scaleAnimator =
                                    ObjectAnimator.ofFloat(element, "scaleX", 0.8f, 1.2f, 0.8f)
                                        .apply {
                                            duration = 3000 + random.nextInt(1000).toLong()
                                            repeatCount = ObjectAnimator.INFINITE
                                            interpolator = AccelerateDecelerateInterpolator()
                                        }

                                val scaleYAnimator =
                                    ObjectAnimator.ofFloat(element, "scaleY", 0.8f, 1.2f, 0.8f)
                                        .apply {
                                            duration = 3000 + random.nextInt(1000).toLong()
                                            repeatCount = ObjectAnimator.INFINITE
                                            interpolator = AccelerateDecelerateInterpolator()
                                        }

                                AnimatorSet().apply {
                                    playTogether(fadeAnimator, scaleAnimator, scaleYAnimator)
                                    start()
                                }
                            }
                        }
                    }

                    private fun createDotDrawable(color: Int): Drawable {
                        return GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(color)
                        }
                    }

                    private fun createSparkleDrawable(color: Int): Drawable {
                        return ShapeDrawable(object : Shape() {
                            override fun draw(canvas: Canvas, paint: Paint) {
                                paint.color = color
                                paint.isAntiAlias = true
                                paint.style = Paint.Style.FILL

                                val centerX = width / 2f
                                val centerY = height / 2f
                                val size = minOf(width, height) / 2f

                                // Draw cross sparkle
                                canvas.drawLine(
                                    centerX - size,
                                    centerY,
                                    centerX + size,
                                    centerY,
                                    paint
                                )
                                canvas.drawLine(
                                    centerX,
                                    centerY - size,
                                    centerX,
                                    centerY + size,
                                    paint
                                )

                                val diagonalSize = size * 0.7f
                                canvas.drawLine(
                                    centerX - diagonalSize, centerY - diagonalSize,
                                    centerX + diagonalSize, centerY + diagonalSize, paint
                                )
                                canvas.drawLine(
                                    centerX - diagonalSize, centerY + diagonalSize,
                                    centerX + diagonalSize, centerY - diagonalSize, paint
                                )
                            }
                        })
                    }

                    private fun createStarDrawable(color: Int, starType: Int): Drawable {
                        return when (starType) {
                            0 -> {
                                createStarShape(adjustColorAlpha(Color.WHITE, 0.9f), 4, 6f, 3f)
                            }

                            1 -> {
                                createStarShape(adjustColorAlpha(Color.WHITE, 0.8f), 5, 8f, 4f)
                            }

                            2 -> {
                                LayerDrawable(
                                    arrayOf(
                                        createStarShape(adjustColorAlpha(color, 0.4f), 6, 12f, 6f),

                                        createStarShape(Color.WHITE, 6, 10f, 5f)
                                    )
                                )
                            }

                            else -> createStarDrawable(color, 0)
                        }
                    }

                    private fun createStarShape(
                        color: Int,
                        points: Int,
                        outerRadius: Float,
                        innerRadius: Float
                    ): ShapeDrawable {
                        return ShapeDrawable(object : Shape() {
                            override fun draw(canvas: Canvas, paint: Paint) {
                                paint.color = color
                                paint.isAntiAlias = true
                                paint.style = Paint.Style.FILL

                                val path = Path()
                                val centerX = width / 2f
                                val centerY = height / 2f

                                val angleStep = (2 * Math.PI / (points * 2)).toFloat()
                                var angle = -Math.PI.toFloat() / 2


                                for (i in 0 until points * 2) {
                                    val radius = if (i % 2 == 0) outerRadius else innerRadius
                                    val x = centerX + (radius * cos(angle))
                                    val y = centerY + (radius * sin(angle))

                                    if (i == 0) {
                                        path.moveTo(x, y)
                                    } else {
                                        path.lineTo(x, y)
                                    }
                                    angle += angleStep
                                }
                                path.close()

                                paint.style = Paint.Style.FILL
                                canvas.drawPath(path, paint)

                                paint.style = Paint.Style.STROKE
                                paint.strokeWidth = 1f
                                paint.color = Color.WHITE
                                canvas.drawPath(path, paint)
                            }
                        })
                    }

                    private fun addCharacterGlow(imageView: ImageView, glowColor: Int) {

                        val scaleAnimator =
                            ObjectAnimator.ofFloat(imageView, "scaleX", 1f, 1.05f, 1f).apply {
                                duration = 3000
                                repeatCount = ObjectAnimator.INFINITE
                            }
                        val scaleYAnimator =
                            ObjectAnimator.ofFloat(imageView, "scaleY", 1f, 1.05f, 1f).apply {
                                duration = 3000
                                repeatCount = ObjectAnimator.INFINITE
                            }

                        AnimatorSet().apply {
                            playTogether(scaleAnimator, scaleYAnimator)
                            start()
                        }
                    }
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
                    oldItem is CastAdapterModel && newItem is CastAdapterModel && oldItem.viewType == DETAILS_ITEM_HEADER && newItem.viewType == DETAILS_ITEM_HEADER && oldItem.viewType == DETAILS_ITEM_THIRD && newItem.viewType == DETAILS_ITEM_THIRD -> {
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

    fun updateBookmark(it: Boolean) {
        characterBookmark = it
        notifyItemChanged(0)
    }


}