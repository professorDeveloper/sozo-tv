package com.saikou.sozo_tv.parser.models

import java.io.Serializable

data class ShowResponse(
    val name: String,
    val link: String,
    val coverUrl: String,
    val otherNames: List<String> = listOf(),
    val total: Int? = null,
    val extra : Map<String,String>?=null,
) : Serializable {

}
