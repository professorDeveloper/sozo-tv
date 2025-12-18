package com.saikou.sozo_tv.parser

import com.saikou.sozo_tv.data.model.Category
import com.saikou.sozo_tv.data.model.Channel
import com.saikou.sozo_tv.data.model.Country
import com.saikou.sozo_tv.manager.GardenDataManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach

class TvGarden {
    suspend fun loadChannelCountries() = GardenDataManager.loadCountriesFromApi()

    suspend fun getChannelsByCountry(country: Country) =
        GardenDataManager.loadChannels(country.code, isCountry = true)

    fun getCategories() = GardenDataManager.loadCategoriesFromApi()

    suspend fun getChannelsByCategory(category: Category) =
        GardenDataManager.loadChannels(category.key, isCountry = false)

}