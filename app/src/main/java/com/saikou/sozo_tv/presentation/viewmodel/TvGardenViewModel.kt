package com.saikou.sozo_tv.presentation.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saikou.sozo_tv.data.local.dao.ChannelDao
import com.saikou.sozo_tv.data.local.entity.ChannelsEntity
import com.saikou.sozo_tv.data.local.entity.CharacterEntity
import com.saikou.sozo_tv.data.model.Category
import com.saikou.sozo_tv.data.model.Channel
import com.saikou.sozo_tv.data.model.Country
import com.saikou.sozo_tv.domain.repository.CharacterBookmarkRepository
import com.saikou.sozo_tv.parser.TvGarden
import com.saikou.sozo_tv.utils.Resource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class TvGardenViewModel(
    private val dao: ChannelDao
) : ViewModel() {
    private val tvGarden = TvGarden()
    val categories: MutableLiveData<Resource<List<Category>>> = MutableLiveData()
    val countries: MutableLiveData<Resource<List<Country>>> = MutableLiveData()
    val channels: MutableLiveData<List<Channel>> = MutableLiveData()
    var isCountrySelected = false
    var currentSort = ""
    var isOpened = false
    fun loadChannelCategories() {
        categories.postValue(Resource.Loading)
        viewModelScope.launch {
            tvGarden.getCategories().let {
                it.onEach {
                    categories.postValue(Resource.Success(it))
                }.launchIn(viewModelScope)
            }
        }
    }


    fun loadChannelsByCategory(category: Category) {
        viewModelScope.launch {
            tvGarden.getChannelsByCategory(category).let {
                it.onEach {
                    channels.postValue(it)
                }
            }.launchIn(viewModelScope)
        }
    }

    fun loadChannelsByCountry(country: Country) {
        viewModelScope.launch {
            tvGarden.getChannelsByCountry(country).let {
                it.onEach {
                    channels.postValue(it)
                }
            }
        }
    }

    fun loadChannelCountries() {
        countries.postValue(Resource.Loading)
        viewModelScope.launch {
            tvGarden.loadChannelCountries().let {
                it.onEach {
                    countries.postValue(Resource.Success(it))
                }
            }
        }
    }
}