package com.saikou.sozo_tv.presentation.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saikou.sozo_tv.parser.AnimePahe
import com.saikou.sozo_tv.parser.models.ShowResponse
import com.saikou.sozo_tv.utils.Resource
import kotlinx.coroutines.launch

class WrongTitleViewModel:ViewModel() {
    val dataFound: MutableLiveData<Resource<List<ShowResponse>>> = MutableLiveData()
    private val animePahe = AnimePahe()
    fun findEpisodes(title: String, ) {
        viewModelScope.launch {
            dataFound.value = Resource.Loading
            val medias = animePahe.search(title)
            if (medias.isNotEmpty()) {
                dataFound.value = Resource.Success(medias)
            } else {
                dataFound.value = Resource.Error(Exception("Media not found wrong title or romanji"))
            }
        }
    }
}