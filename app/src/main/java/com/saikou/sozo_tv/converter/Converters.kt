package com.saikou.sozo_tv.converter

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.saikou.sozo_tv.data.local.entity.EpisodeInfoEntity

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromEpisodeList(value: ArrayList<EpisodeInfoEntity>): String {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toEpisodeList(value: String): ArrayList<EpisodeInfoEntity> {
        val listType = object : TypeToken<ArrayList<EpisodeInfoEntity>>() {}.type
        return gson.fromJson(value, listType)
    }
}
