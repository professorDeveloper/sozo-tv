package com.saikou.sozo_tv.utils

//
//fun EventModelItem.toChannelResponseItem(): ChannelResponseItem {
//    return ChannelResponseItem(
//        audioList = this.clearkey,
//
//        category = this.category,
//        categoryProperty = this.id.toString(),
//        country = this.play_url,
//        description = "Unknown",
//        id = this.id,
//        image = this.logo,
//        language = "Unknown",
//        this.name,
//        "unknown",
//    )
//}
//
//fun List<EventModelItem>.toChannelsResponse(): ArrayList<ChannelResponseItem> =
//    this.map { it.toChannelResponseItem() }.toCollection(arrayListOf())
//
//
//fun ChannelResponseItem.toEventModelItem(): EventModelItem {
//    return EventModelItem(
//        this.category,
//        this.audioList,
//        this.id,
//        this.image,
//        this.name,
//        this.country
//    )
//}