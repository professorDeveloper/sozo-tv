package com.saikou.sozo_tv.parser

import com.saikou.sozo_tv.data.model.Category
import com.saikou.sozo_tv.data.model.Channel
import com.saikou.sozo_tv.data.model.Country
import com.saikou.sozo_tv.manager.GardenDataManager

class TvGarden {
    suspend fun loadChannelCountries(): ArrayList<Country> {
        val dataManager = GardenDataManager.loadCountriesFromApi()
        return ArrayList(dataManager)
    }

    suspend fun getChannelsByCountry(country: Country): ArrayList<Channel> {
        val dataManager = GardenDataManager.loadChannelsForCountry(country.code)
        return ArrayList(dataManager)
    }

    suspend fun getCategories(): ArrayList<Category> {
        val dataManager = GardenDataManager.loadCategoriesFromApi()
        return ArrayList(dataManager)
    }

    suspend fun getChannelsByCategory(category: Category): ArrayList<Channel> {
        val dataManager = GardenDataManager.loadChannelsForCategory(category.key)
        return ArrayList(dataManager)
    }
}