package com.saikou.sozo_tv.presentation.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saikou.sozo_tv.parser.HentaiMama
import com.saikou.sozo_tv.parser.Video
import com.saikou.sozo_tv.parser.VideoServer
import com.saikou.sozo_tv.parser.models.Kiwi
import com.saikou.sozo_tv.utils.Resource
import kotlinx.coroutines.launch

class AdultPlayerViewModel : ViewModel() {
    val adultSource = HentaiMama()

    var videoServer: Video? = null
    var episodeLink: String? = null
    val episodeData = MutableLiveData<Resource<Kiwi>>()
    val extractData = MutableLiveData<Resource<Video>>()
    fun loadVideoServers(epLink: String) {
        episodeData.value = Resource.Loading
        episodeLink = epLink
        viewModelScope.launch {
            val kiwi = adultSource.loadVideoServers(epLink, null)
            episodeData.postValue(Resource.Success(kiwi))
        }
    }
    fun extractVideoFromKiwi(kiwi: Kiwi){
        extractData.value = Resource.Loading
        viewModelScope.launch {
            val video = adultSource.extract(kiwi)
            extractData.postValue(Resource.Success(video))
        }
    }
}