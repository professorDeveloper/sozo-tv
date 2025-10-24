package com.saikou.sozo_tv.domain.model

import com.animestudios.animeapp.type.MediaSource
import com.saikou.sozo_tv.data.model.anilist.CoverImage

data class DetailModel(
    val id:Int,
    val malId:Int,
    val coverImage:CoverImage,
    val bannerImage:String,
    val description:String,
    val title:String,

    val episodes:Int?,
    val genres:List<String?>?,
    val extraLinks:List<String?>?,
    val studios:List<String?>?,
    val seasonYear:Int?,
    val mediaSource: MediaSource?,
    val airingSchedule: AiringSchedule = AiringSchedule(),
    val isAdult: Boolean = false,
    val isSeries: Boolean = false,
)
