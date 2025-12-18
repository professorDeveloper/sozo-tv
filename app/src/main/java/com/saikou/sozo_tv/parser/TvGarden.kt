package com.saikou.sozo_tv.parser

import com.saikou.sozo_tv.data.model.Category
import com.saikou.sozo_tv.data.model.Channel
import com.saikou.sozo_tv.data.model.Country
import com.saikou.sozo_tv.manager.GardenDataManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class TvGarden {
    suspend fun loadChannelCountries(): ArrayList<Country> {
        val dataManager = GardenDataManager.loadCountriesFromApi()
        return ArrayList(dataManager)
    }

    suspend fun getChannelsByCountry(country: Country): ArrayList<Channel> {
        val dataManager = GardenDataManager.loadChannelsForCountry(country.code)
        return ArrayList(dataManager)
    }

    suspend fun getCategories() = flow {
        val dataManager = GardenDataManager.loadCategoriesFromApi()
        emit(dataManager as ArrayList<Category>)
    }

    suspend fun getChannelsByCategory(category: Category) = flow<ArrayList<Channel>> {
        val dataManager = GardenDataManager.loadChannelsForCategory(category.key)
        emit(ArrayList(dataManager))
    }

}