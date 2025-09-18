package com.saikou.sozo_tv.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saikou.sozo_tv.data.local.entity.WatchHistoryEntity
import com.saikou.sozo_tv.domain.repository.WatchHistoryRepository
import com.saikou.sozo_tv.parser.AnimePahe
import com.saikou.sozo_tv.parser.HentaiMama
import com.saikou.sozo_tv.parser.models.EpisodeData
import com.saikou.sozo_tv.parser.models.ShowResponse
import com.saikou.sozo_tv.utils.Resource
import kotlinx.coroutines.launch

class EpisodeViewModel(private val watchHistoryRepository: WatchHistoryRepository) : ViewModel() {
    val episodeData: MutableLiveData<Resource<EpisodeData>> =
        MutableLiveData<Resource<EpisodeData>>(Resource.Idle)
    val dataFound: MutableLiveData<Resource<ShowResponse>> = MutableLiveData()
    private val animePahe = AnimePahe()
    private val adultSource = HentaiMama()

    var epListFromLocal = ArrayList<WatchHistoryEntity>()


    fun loadAdultEpisodes(link: String) {
        episodeData.value = Resource.Loading
        viewModelScope.launch {
            adultSource.loadEpisodes(link, null).let {
                if (it.isEmpty()){
                    episodeData.value = Resource.Error(Exception("Episode not found"))
                }else {
                    episodeData.value = Resource.Success(EpisodeData(1, it, 1, 1, "", -1, null, -1, -1))
                }
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
            viewModelScope.launch {
                val medias = adultSource.search(title)
                Log.d("GGG", "findEpisodes:${medias} ")
                if (medias.isNotEmpty()) {
                    val media = medias[0]
                    dataFound.value = Resource.Success(media)
                } else {
                    Log.d("GGG", "findEpisodes 666 } ")
                    dataFound.value =  Resource.Error(Exception("Media not found wrong title or romanji"))
                }
            }
        }
    }
}