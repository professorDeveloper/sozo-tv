package com.saikou.sozo_tv.presentation.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saikou.sozo_tv.parser.AnimePahe
import com.saikou.sozo_tv.parser.models.EpisodeData
import com.saikou.sozo_tv.parser.models.ShowResponse
import com.saikou.sozo_tv.utils.Resource
import kotlinx.coroutines.launch

class EpisodeViewModel : ViewModel() {
    val episodeData: MutableLiveData<Resource<EpisodeData>> =
        MutableLiveData<Resource<EpisodeData>>(Resource.Idle)
    val dataFound: MutableLiveData<Resource<ShowResponse>> = MutableLiveData()
    val errorData: MutableLiveData<String> = MutableLiveData()
    private val animePahe = AnimePahe()
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

    fun findEpisodes(title: String, romanji: String) {
        viewModelScope.launch {
            val medias = animePahe.search(title)
            if (medias.isNotEmpty()) {
                val media = medias[0]
                dataFound.value = Resource.Success(media)
            } else {
                dataFound.value = Resource.Error(Exception("Media not found wrong title or romanji"))
            }
        }
    }
}