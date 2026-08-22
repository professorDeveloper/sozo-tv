package com.saikou.sozo_tv.presentation.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saikou.sozo_tv.data.local.dao.ChannelDao
import com.saikou.sozo_tv.data.model.Category
import com.saikou.sozo_tv.data.model.Channel
import com.saikou.sozo_tv.data.model.Country
import com.saikou.sozo_tv.parser.TvGarden
import com.saikou.sozo_tv.utils.Resource
import kotlinx.coroutines.flow.catch
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
            tvGarden.getCategories()
                .onEach { categories.postValue(Resource.Success(it)) }
                .catch { categories.postValue(Resource.Error(it)) }
                .launchIn(viewModelScope)
        }
    }

    /**
     * Countries were fetched into a flow that nobody collected — the `.onEach`
     * built a new flow and dropped it, so the state stayed on Loading and the
     * By Country tab spun forever. Collecting it is the whole fix.
     */
    fun loadChannelCountries() {
        countries.postValue(Resource.Loading)
        viewModelScope.launch {
            tvGarden.loadChannelCountries()
                .onEach { countries.postValue(Resource.Success(it)) }
                .catch { countries.postValue(Resource.Error(it)) }
                .launchIn(viewModelScope)
        }
    }

    fun loadChannelsByCategory(category: Category) {
        viewModelScope.launch {
            tvGarden.getChannelsByCategory(category)
                .onEach { channels.postValue(it) }
                .catch { channels.postValue(emptyList()) }
                .launchIn(viewModelScope)
        }
    }

    /** Uncollected for the same reason as [loadChannelCountries]. */
    fun loadChannelsByCountry(country: Country) {
        viewModelScope.launch {
            tvGarden.getChannelsByCountry(country)
                .onEach { channels.postValue(it) }
                .catch { channels.postValue(emptyList()) }
                .launchIn(viewModelScope)
        }
    }
}
