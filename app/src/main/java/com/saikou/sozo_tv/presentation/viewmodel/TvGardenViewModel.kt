package com.saikou.sozo_tv.presentation.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saikou.sozo_tv.data.model.Category
import com.saikou.sozo_tv.data.model.Channel
import com.saikou.sozo_tv.data.model.Country
import com.saikou.sozo_tv.parser.TvGarden
import com.saikou.sozo_tv.presentation.screens.tv_garden.TvGardenScreen
import kotlinx.coroutines.launch

class TvGardenViewModel:ViewModel() {
    private val tvGarden = TvGarden()
     val categories:MutableLiveData<List<Category>> = MutableLiveData()
     val countries:MutableLiveData<List<Country>> = MutableLiveData()
     val channels:MutableLiveData<List<Channel>> = MutableLiveData()
    fun loadChannelCategories() {
        viewModelScope.launch {
            tvGarden.getCategories().let {
                categories.postValue(it)
            }
        }
    }

    fun loadChannelsByCategory(category: Category){
        viewModelScope.launch {
            tvGarden.getChannelsByCategory(category).let {
                channels.postValue(it)
            }
        }
    }

    fun loadChannelsByCountry(country: Country){
        viewModelScope.launch {
            tvGarden.getChannelsByCountry(country).let {
                channels.postValue(it)
            }
        }
    }
    fun loadChannelCountries(){
        viewModelScope.launch {
            tvGarden.loadChannelCountries().let {
                countries.postValue(it)
            }
        }
    }
}