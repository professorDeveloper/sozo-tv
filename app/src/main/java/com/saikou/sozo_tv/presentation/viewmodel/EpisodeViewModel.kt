package com.saikou.sozo_tv.presentation.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saikou.sozo_tv.data.local.entity.WatchHistoryEntity
import com.saikou.sozo_tv.domain.repository.WatchHistoryRepository
import com.saikou.sozo_tv.parser.base.BaseParser
import com.saikou.sozo_tv.parser.models.EpisodeData
import com.saikou.sozo_tv.parser.models.ShowResponse
import com.saikou.sozo_tv.parser.sources.AnimeSources
import com.saikou.sozo_tv.utils.Resource
import kotlinx.coroutines.launch

class EpisodeViewModel(
    private val watchHistoryRepository: WatchHistoryRepository,
) : ViewModel() {
    val episodeData: MutableLiveData<Resource<EpisodeData>> =
        MutableLiveData<Resource<EpisodeData>>(Resource.Idle)
    val dataFound: MutableLiveData<Resource<ShowResponse>> = MutableLiveData()

    var parser: BaseParser = AnimeSources.getCurrent()
    var epListFromLocal = ArrayList<WatchHistoryEntity>()

    fun loadEpisodeByPage(page: Int, id: String, showResponse: ShowResponse) {
        episodeData.value = Resource.Loading
        viewModelScope.launch {
            val result = parser.loadEpisodes(id, page, showResponse)
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
            epListFromLocal.addAll(watchHistoryRepository.getAllHistory())
        }
    }

    fun findEpisodes(title: String, isAdult: Boolean = false) {
        viewModelScope.launch {
            dataFound.value = Resource.Loading
            val medias = parser.search(title)
            if (medias.isNotEmpty()) {
                getEpisodesLocal()
                dataFound.value = Resource.Success(medias[0])
            } else {
                dataFound.value =
                    Resource.Error(Exception("Media not found wrong title or romanji"))
            }
        }
    }
}
