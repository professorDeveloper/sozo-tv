package com.saikou.sozo_tv.services

import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.saikou.sozo_tv.data.model.NewsItem
import kotlinx.coroutines.tasks.await

class FirebaseService(firebaseDatabase: FirebaseDatabase) {

    private val newsRef: DatabaseReference = firebaseDatabase.getReference("news")

    suspend fun getNews(): List<NewsItem> {
        val snapshot = newsRef.get().await()
        return snapshot.children.mapNotNull { it.getValue(NewsItem::class.java) }
    }
}