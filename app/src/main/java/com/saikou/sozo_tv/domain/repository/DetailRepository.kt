package com.saikou.sozo_tv.domain.repository

import com.saikou.sozo_tv.domain.model.DetailModel

interface DetailRepository {
    suspend fun loadAnimeDetail(id:Int):Result<DetailModel>
}