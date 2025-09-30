package com.saikou.sozo_tv.presentation.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saikou.sozo_tv.data.model.Category
import com.saikou.sozo_tv.data.model.Channel
import com.saikou.sozo_tv.data.model.Country
import com.saikou.sozo_tv.parser.TvGarden
import com.saikou.sozo_tv.utils.Resource
import kotlinx.coroutines.launch

class TvGardenViewModel:ViewModel() {
    private val tvGarden = TvGarden()
     val categories:MutableLiveData<Resource<List<Category>>> = MutableLiveData()
     val countries:MutableLiveData<Resource<List<Country>>> = MutableLiveData()
     val channels:MutableLiveData<List<Channel>> = MutableLiveData()
    var isCountrySelected = false
    var currentSort = ""
    var isOpened = false
    fun loadChannelCategories() {
        categories.postValue(Resource.Loading)
        viewModelScope.launch {
            tvGarden.getCategories().let {
                categories.postValue(Resource.Success(it))
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
        countries.postValue(Resource.Loading)
        viewModelScope.launch {
            tvGarden.loadChannelCountries().let {
                countries.postValue(Resource.Success(it))
            }
        }
    }
}