package com.saikou.sozo_tv.manager

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.saikou.sozo_tv.domain.model.CategoryChannel
import com.saikou.sozo_tv.domain.model.CategoryChannelItem
import com.saikou.sozo_tv.domain.model.ChannelResponseItem
import com.saikou.sozo_tv.presentation.screens.home.HomeAdapter
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

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
                    try {
                        val name = snapshot.child("name").getValue(String::class.java) ?: ""
                        val viewType = snapshot.child("viewType").getValue(Int::class.java) ?: 0

                        val list = arrayListOf<CategoryChannelItem>()
                        val listSnapshot = snapshot.child("list")

                        for (child in listSnapshot.children) {
                            val contentSnapshot = child.child("content")

                            val title = contentSnapshot.child("title").getValue(String::class.java) ?: ""
                            val image = contentSnapshot.child("image").getValue(String::class.java) ?: ""
                            val playLink = contentSnapshot.child("playLink").getValue(String::class.java) ?: ""
                            val country = contentSnapshot.child("country").getValue(String::class.java) ?: ""
                            val viewTypeItem = child.child("viewType").getValue(Int::class.java) ?: 0

                            val item = CategoryChannelItem(
                                viewType = viewTypeItem,
                                content = ChannelResponseItem(title, image, playLink, country)
                            )
                            list.add(item)
                        }

                        val channel = CategoryChannel(
                            name = name,
                            list = list,
                            viewType = viewType
                        )

                        onDataReceived(channel)

                    } catch (e: Exception) {
                        e.printStackTrace()
                        onDataReceived(null)
                    }

                } else {
                    val defaultChannels = initializeDefaultChannels()
                    saveChannelsToRealtimeDatabase(defaultChannels) { success, error ->
                        if (success) {
                            onDataReceived(defaultChannels)
                        } else {
                            onDataReceived(null)
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                onDataReceived(null)
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

    fun getChannelsFlow(): Flow<CategoryChannel?> = callbackFlow {
        val database = FirebaseDatabase.getInstance()
        val liveChannelsRef = database.getReference(CHANNELS_REF)

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    if (snapshot.exists()) {
                        val name = snapshot.child("name").getValue(String::class.java) ?: ""
                        val viewType = snapshot.child("viewType").getValue(Int::class.java) ?: 0

                        val list = arrayListOf<CategoryChannelItem>()
                        val listSnapshot = snapshot.child("list")

                        for (child in listSnapshot.children) {
                            val contentSnapshot = child.child("content")

                            val title = contentSnapshot.child("title").getValue(String::class.java) ?: ""
                            val image = contentSnapshot.child("image").getValue(String::class.java) ?: ""
                            val playLink = contentSnapshot.child("playLink").getValue(String::class.java) ?: ""
                            val country = contentSnapshot.child("country").getValue(String::class.java) ?: ""
                            val viewTypeItem = child.child("viewType").getValue(Int::class.java) ?: 0

                            val item = CategoryChannelItem(
                                viewType = viewTypeItem,
                                content = ChannelResponseItem(title, image, playLink, country)
                            )
                            list.add(item)
                        }

                        val channel = CategoryChannel(name, list, viewType)
                        trySend(channel)
                    } else {
                        val defaultChannels = initializeDefaultChannels()
                        saveChannelsToRealtimeDatabase(defaultChannels) { success, error ->
                            if (success) trySend(defaultChannels)
                            else trySend(null)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    trySend(null)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(null)
            }
        }

        liveChannelsRef.addListenerForSingleValueEvent(listener)

        awaitClose { liveChannelsRef.removeEventListener(listener) }
    }

}