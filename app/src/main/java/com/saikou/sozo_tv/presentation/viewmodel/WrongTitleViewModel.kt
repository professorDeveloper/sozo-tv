package com.saikou.sozo_tv.presentation.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saikou.sozo_tv.domain.repository.EpisodeRepository
import com.saikou.sozo_tv.parser.anime.AnimePahe
import com.saikou.sozo_tv.parser.anime.HentaiMama
import com.saikou.sozo_tv.parser.models.ShowResponse
import com.saikou.sozo_tv.utils.LocalData
import com.saikou.sozo_tv.utils.Resource
import kotlinx.coroutines.launch

class WrongTitleViewModel(private val repo: EpisodeRepository) : ViewModel() {
    val dataFound: MutableLiveData<Resource<List<ShowResponse>>> = MutableLiveData()
    private val animePahe = AnimePahe()
    private val adultSource = HentaiMama()


    fun findEpisodes(title: String, isAdult: Boolean = false) {

        viewModelScope.launch {
            if (LocalData.isAnimeEnabled) {
                if (isAdult) {
                    dataFound.value = Resource.Loading
                    val medias = adultSource.search(title)
                    if (medias.isNotEmpty()) {
                        dataFound.value = Resource.Success(medias)
                    } else {
                        dataFound.value =
                            Resource.Error(Exception("Media not found wrong title or romanji"))
                    }
                } else {
                    dataFound.value = Resource.Loading
                    val medias = animePahe.search(title)
                    if (medias.isNotEmpty()) {
                        dataFound.value = Resource.Success(medias)
                    } else {
                        dataFound.value =
                            Resource.Error(Exception("Media not found wrong title or romanji"))
                    }
                }
            }
        }
    }
}