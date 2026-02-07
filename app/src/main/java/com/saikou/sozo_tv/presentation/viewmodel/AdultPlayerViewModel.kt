package com.saikou.sozo_tv.presentation.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saikou.sozo_tv.parser.anime.HentaiMama
import com.saikou.sozo_tv.utils.Resource
import kotlinx.coroutines.launch

class AdultPlayerViewModel : ViewModel() {
    val adultSource = HentaiMama()

    var episodeLink: String? = null
    val episodeData = MutableLiveData<Resource<com.saikou.sozo_tv.parser.models.Video.Server>>()
    val extractData = MutableLiveData<Resource<com.saikou.sozo_tv.parser.models.Video>>()
    fun loadVideoServers(epLink: String) {
        episodeData.value = Resource.Loading
        episodeLink = epLink
        viewModelScope.launch {
            val kiwi = adultSource.loadVideoServers(epLink, null)
            episodeData.postValue(Resource.Success(kiwi))
        }
    }

    fun extractVideoFromKiwi(kiwi: com.saikou.sozo_tv.parser.models.Video.Server) {
        extractData.value = Resource.Loading
        viewModelScope.launch {
            val video = adultSource.extract(kiwi)
            extractData.postValue(Resource.Success(video))
        }
    }
}