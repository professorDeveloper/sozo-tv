package com.saikou.sozo_tv.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saikou.sozo_tv.data.local.entity.WatchHistoryEntity
import com.saikou.sozo_tv.domain.repository.EpisodeRepository
import com.saikou.sozo_tv.domain.repository.WatchHistoryRepository
import com.saikou.sozo_tv.parser.anime.AnimePahe
import com.saikou.sozo_tv.parser.anime.HentaiMama
import com.saikou.sozo_tv.parser.models.Data
import com.saikou.sozo_tv.parser.models.Episode
import com.saikou.sozo_tv.parser.models.EpisodeData
import com.saikou.sozo_tv.parser.models.ShowResponse
import com.saikou.sozo_tv.parser.movie.PlayImdb
import com.saikou.sozo_tv.utils.Resource
import com.saikou.sozo_tv.utils.toDomain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EpisodeViewModel(
    private val watchHistoryRepository: WatchHistoryRepository,
    private val repo: EpisodeRepository
) : ViewModel() {
    val episodeData: MutableLiveData<Resource<EpisodeData>> =
        MutableLiveData<Resource<EpisodeData>>(Resource.Idle)
    val dataFound: MutableLiveData<Resource<ShowResponse>> = MutableLiveData()
    private val animePahe = AnimePahe()
    private val adultSource = HentaiMama()
    var seasons = mapOf<Int, Int>()
    private val movieSource = PlayImdb()
    var epListFromLocal = ArrayList<WatchHistoryEntity>()


    fun loadAdultEpisodes(link: String) {
        episodeData.value = Resource.Loading
        viewModelScope.launch {
            adultSource.loadEpisodes(link, null).let {
                if (it.isEmpty()) {
                    episodeData.value = Resource.Error(Exception("Episode not found"))
                } else {
                    episodeData.value =
                        Resource.Success(EpisodeData(1, it, 1, 1, "", -1, null, -1, -1))
                }
            }
        }
    }

     var cachedEpisodes: List<Episode>? = null
     var cachedSeasons: Map<Int, Int> = emptyMap()
     var    firstCategoryDataObserver = MutableLiveData<Unit>()

    fun loadMovieSeriesEpisodes(imdbId: String, tmdbId: Int, season: Int) {

        viewModelScope.launch {
            try {
                episodeData.value = Resource.Loading
                val listData = ArrayList<Data>()

                val allEpisodes = cachedEpisodes ?: movieSource.getEpisodes(imdbId).also {
                    cachedEpisodes = it
                    cachedSeasons = it.groupingBy { it.season }.eachCount()
                }

                if (cachedSeasons.isEmpty()) {
                    cachedSeasons = allEpisodes.groupingBy { it.season }.eachCount()
                    if (seasons.isEmpty()) seasons = cachedSeasons
                }
                val backdrops = movieSource.getDetails(season, tmdbId)

                Log.d("GGG", "loadMovieSeriesEpisodes: season=$season")

                val episodes = allEpisodes.filter { it.season == season }

                episodes.forEachIndexed { index, episode ->
                    listData.add(
                        episode.toDomain().copy(
                            episode2 = episode.episode,
                            episode = index + 1,
                            title = episode.title,
                            snapshot = backdrops[index].originalUrl,
                            season = episode.season
                        )
                    )
                }

                episodeData.value = Resource.Success(
                    EpisodeData(
                        1,
                        listData,
                        1,
                        1,
                        "",
                        -1,
                        null,
                        -1,
                        1
                    )
                )
                firstCategoryDataObserver.postValue( Unit)

            } catch (e: Exception) {
                episodeData.postValue(Resource.Error(e))
            }
        }
    }

    fun loadEpisodeByPage(page: Int, id: String) {
        episodeData.value = Resource.Loading
        viewModelScope.launch {
            val result = animePahe.loadEpisodes(id, page)
            if (result != null) {
                episodeData.value = Resource.Success(result)
            } else {
                episodeData.value = Resource.Error(Exception("Episode not found"))
            }
        }
    }

    private fun getEpisodesLocal() {
        viewModelScope.launch {
            epListFromLocal.clear()
            epListFromLocal.addAll(watchHistoryRepository.getAllHistory() ?: arrayListOf())
        }
    }

    fun findImdbIdSeries(tdbId: String, title: String, image: String, isMovie: Boolean) {
        val current = dataFound.value
        if (current is Resource.Success && current.data.link.isNotEmpty()) return

        viewModelScope.launch {
            dataFound.postValue(Resource.Loading)
            val result = if (isMovie) {
                repo.extractImdbIdFromMovie(tdbId)
            } else {
                repo.extractImdbForSeries(tdbId)
            }

            if (result.isSuccess) {
                val imdbId = result.getOrNull()?.imdb_id
                if (imdbId != null) {
                    dataFound.postValue(
                        Resource.Success(
                            ShowResponse(
                                title,
                                link = imdbId,
                                image
                            )
                        )
                    )
                } else {
                    dataFound.postValue(Resource.Error(Exception("IMDb ID not found")))
                }
            } else {
                dataFound.postValue(Resource.Error(result.exceptionOrNull()!!))
            }
        }
    }

    fun findEpisodes(title: String, isAdult: Boolean = false) {
        if (!isAdult) {
            viewModelScope.launch {
                dataFound.value = Resource.Loading
                val medias = animePahe.search(title)
                if (medias.isNotEmpty()) {
                    val media = medias[0]
                    getEpisodesLocal()
                    dataFound.value = Resource.Success(media)
                } else {
                    dataFound.value =
                        Resource.Error(Exception("Media not found wrong title or romanji"))
                }
            }
        } else {

            dataFound.value = Resource.Loading
            viewModelScope.launch(Dispatchers.IO) { // <-- Add Dispatchers.IO
                Log.d("GGG", "findEpisodes:Tushdi ")
                val medias = adultSource.search(title)
                Log.d("GGG", "findEpisodes:${medias} ")
                if (medias.isNotEmpty()) {
                    val media = medias[0]
                    withContext(Dispatchers.Main) { // Switch back to main for UI updates
                        dataFound.value = Resource.Success(media)
                    }
                } else {
                    Log.d("GGG", "findEpisodes 666 } ")
                    withContext(Dispatchers.Main) {
                        dataFound.value =
                            Resource.Error(Exception("Media not found wrong title or romanji"))
                    }
                }
            }
        }
    }
}