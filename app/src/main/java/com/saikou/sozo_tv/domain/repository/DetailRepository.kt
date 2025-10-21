package com.saikou.sozo_tv.domain.repository

import com.saikou.sozo_tv.domain.model.Cast
import com.saikou.sozo_tv.domain.model.CastDetailModel
import com.saikou.sozo_tv.domain.model.DetailModel
import com.saikou.sozo_tv.domain.model.MainModel

interface DetailRepository {
    suspend fun loadAnimeDetail(id: Int): Result<DetailModel>
    suspend fun loadMovieDetail(id: Int): Result<DetailModel>
    suspend fun loadSeriesDetail(id: Int): Result<DetailModel>
    suspend fun loadRandomAnime(): Result<List<MainModel>>
    suspend fun loadCast(id: Int): Result<List<Cast>>
    suspend fun loadAnimeRelations(id: Int): Result<List<MainModel>>
    suspend fun characterDetail(id: Int): Result<CastDetailModel>
}