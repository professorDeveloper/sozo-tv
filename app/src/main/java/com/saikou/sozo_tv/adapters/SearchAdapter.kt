package com.saikou.sozo_tv.adapters

import android.annotation.SuppressLint
import android.graphics.Color
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import androidx.core.view.isVisible
import com.saikou.sozo_tv.data.extensions.ExtensionContentRegistry
import com.saikou.sozo_tv.databinding.SearchItemBinding
import com.saikou.sozo_tv.domain.model.SearchModel

class SearchAdapter :
    RecyclerView.Adapter<SearchAdapter.MovieViewHolder>() {
    private var movieList = ArrayList<SearchModel>()
    var query: String = ""

    /** Only meaningful when results span providers; a single-source search knows. */
    var showSource: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            notifyItemRangeChanged(0, movieList.size)
        }

    private lateinit var itemClickeddListener: (movie: SearchModel) -> Unit
    fun setOnItemClickListener(listener: (movie: SearchModel) -> Unit) {
        itemClickeddListener = listener
    }

    private var itemLongClickListener: ((movie: SearchModel) -> Unit)? = null
    fun setOnItemLongClickListener(listener: (movie: SearchModel) -> Unit) {
        itemLongClickListener = listener
    }

    inner class MovieViewHolder(private val binding: SearchItemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        @SuppressLint("SetTextI18n", "NewApi")
        fun bind(movie: SearchModel) {
            // Which source found this. In an all-sources search the same title
            // comes back from several, and without this the duplicates look
            // like a bug rather than a choice of where to play from.
            val source = movie.id
                ?.let { ExtensionContentRegistry.resolve(it) }
                ?.provider
                ?.substringAfter(':')
                ?.takeIf { it.isNotBlank() }
            binding.movieSource.isVisible = showSource && source != null
            binding.movieSource.text = source.orEmpty()

            binding.root.setOnClickListener {
                itemClickeddListener.invoke(movie)
            }
            binding.root.setOnLongClickListener {
                itemLongClickListener?.invoke(movie)
                itemLongClickListener != null
            }

            val mainText = movie.title ?: "Unknown Title"
            val spannableString = SpannableString(mainText)

            if (query.isNotEmpty()) {
                // Compute the offset against the SAME string we span. Using a lowercased copy
                // can shift indices (e.g. 'İ' -> 2 UTF-16 units) and overflow -> setSpan crash.
                val start = mainText.indexOf(query, ignoreCase = true)
                if (start != -1) {
                    val end = start + query.length
                    spannableString.setSpan(
                        ForegroundColorSpan(Color.WHITE),
                        start,
                        end,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }

            binding.movieTitle.text = spannableString

            val imageUrl = movie.image ?: ""
            Glide.with(binding.root.context)
                .load(imageUrl)
                .apply(
                    RequestOptions()
                        .transform(RoundedCorners(16))
                        .centerCrop()
                )
                .into(binding.moviePoster)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MovieViewHolder {
        val binding = SearchItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MovieViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MovieViewHolder, position: Int) {
        holder.bind(movieList[position])
    }

    override fun getItemCount(): Int = movieList.size

    fun updateData(newMovies: List<SearchModel>) {
        movieList.clear()
        movieList.addAll(newMovies)
        notifyDataSetChanged()
    }

    fun setQueryText(query: String) {
        this.query = query
        notifyDataSetChanged()
    }


}
