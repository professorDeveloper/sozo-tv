package com.saikou.sozo_tv.presentation.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saikou.sozo_tv.parser.models.ShowResponse
import com.saikou.sozo_tv.parser.sources.AnimeSources
import com.saikou.sozo_tv.utils.Resource
import kotlinx.coroutines.launch

class WrongTitleViewModel() : ViewModel() {
    val dataFound: MutableLiveData<Resource<List<ShowResponse>>> = MutableLiveData()

    // Searches the active extension provider (the legacy hand-written parsers were removed).
    private val source = AnimeSources.getCurrent()

    fun findEpisodes(title: String, isAdult: Boolean = false) {
        viewModelScope.launch {
            dataFound.value = Resource.Loading
            val medias = source.search(title)
            if (medias.isNotEmpty()) {
                dataFound.value = Resource.Success(medias)
            } else {
                dataFound.value =
                    Resource.Error(Exception("Media not found wrong title or romanji"))
            }
        }
    }
}
