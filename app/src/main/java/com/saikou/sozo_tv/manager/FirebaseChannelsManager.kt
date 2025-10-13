package com.saikou.sozo_tv.manager

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.saikou.sozo_tv.domain.model.CategoryChannel
import com.saikou.sozo_tv.domain.model.CategoryChannelItem
import com.saikou.sozo_tv.domain.model.ChannelResponseItem
import com.saikou.sozo_tv.presentation.screens.home.HomeAdapter

object FirebaseChannelsManager {
    private const val CHANNELS_REF = "liveChannels"

    fun saveChannelsToRealtimeDatabase(channels: CategoryChannel, onComplete: (Boolean, String?) -> Unit) {
        val database = FirebaseDatabase.getInstance()
        val liveChannelsRef = database.getReference(CHANNELS_REF)

        liveChannelsRef.setValue(channels)
            .addOnSuccessListener {
                onComplete(true, null)
            }
            .addOnFailureListener { exception ->
                onComplete(false, exception.message)
            }
    }

    fun getChannelsFromRealtimeDatabase(onDataReceived: (CategoryChannel?) -> Unit) {
        val database = FirebaseDatabase.getInstance()
        val liveChannelsRef = database.getReference(CHANNELS_REF)

        liveChannelsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val channels = snapshot.getValue(CategoryChannel::class.java)
                    onDataReceived(channels)
                } else {
                    // Firebase is empty, use default channels
                    val defaultChannels = initializeDefaultChannels()
                    saveChannelsToRealtimeDatabase(defaultChannels) { success, error ->
                        if (success) {
                            onDataReceived(defaultChannels)
                        } else {
                            onDataReceived(null)
                            println("Error saving default channels: $error")
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                onDataReceived(null)
                println("Error fetching channels: ${error.message}")
            }
        })
    }

    fun initializeDefaultChannels(): CategoryChannel {
        return  CategoryChannel(
            "Live Channels",
            arrayListOf(

                CategoryChannelItem(
                    viewType = HomeAdapter.VIEW_CHANNEL_ITEM,
                    content = ChannelResponseItem(
                        "AZ(Anime) TV",
                        "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcRFrVHz2E1vfLyFJBToLKASI2geqwZkLWeRMw&s",
                        "https://stmv1.srvif.com/loadingtv/loadingtv/playlist.m3u8",
                        "SPAIN"
                    )
                ),
                CategoryChannelItem(
                    viewType = HomeAdapter.VIEW_CHANNEL_ITEM,
                    content = ChannelResponseItem(
                        "3ABNKids.us",
                        "https://media.sketchfab.com/models/a79c29a6ee474763ac227986614228d1/thumbnails/a8f762b932e04423903093f9cd49e2ce/09d8de0716254931bc6e110d78e7f15a.jpeg",
                        "https://3abn-live.akamaized.net/hls/live/2010550/Kids/master.m3u8",
                        "US"
                    )
                ),
            )
        )
    }
}