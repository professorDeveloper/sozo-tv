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
import com.saikou.sozo_tv.databinding.SearchItemBinding
import com.saikou.sozo_tv.parser.models.ShowResponse
import com.saikou.sozo_tv.utils.gone

class WrongTitleSearchAdapter() :
    RecyclerView.Adapter<WrongTitleSearchAdapter.MovieViewHolder>() {
    private var movieList = ArrayList<ShowResponse>()
    var query: String = ""

    private lateinit var itemClickeddListener: (movie: ShowResponse) -> Unit
    fun setOnItemClickListener(listener: (movie: ShowResponse) -> Unit) {
        itemClickeddListener = listener
    }

    inner class MovieViewHolder(private val binding: SearchItemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        @SuppressLint("SetTextI18n", "NewApi")
        fun bind(movie: ShowResponse) {

            val mainText = movie.name.lowercase()
            val spannableString = SpannableString(mainText)
            val start = mainText.indexOf(query)

            if (start != -1) {
                val end = start.plus(query.length)
                spannableString.setSpan(
                    ForegroundColorSpan(Color.WHITE),
                    start,
                    end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            binding.root.setOnClickListener {
                itemClickeddListener.invoke(movie)
            }
            binding.imdbRating.gone()
            binding.movieDetails.gone()
            binding.subscribeButton.gone()
            binding.movieTitle.text = spannableString
            Glide.with(binding.root.context).load(movie.coverUrl).into(binding.moviePoster)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MovieViewHolder {
        val binding = SearchItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MovieViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MovieViewHolder, position: Int) {
        holder.bind(movieList[position])
    }

    override fun getItemCount(): Int = if (movieList.size > 5) 5 else movieList.size

    fun updateData(newMovies: List<ShowResponse>) {
        movieList.clear()
        movieList.addAll(newMovies)
        notifyDataSetChanged()
    }

    fun setQueryText(query: String) {
        this.query = query
    }

    fun refreshItems() {
        notifyDataSetChanged()
    }
}
