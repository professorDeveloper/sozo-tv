package com.saikou.sozo_tv.services

import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.saikou.sozo_tv.data.model.NewsItem
import kotlinx.coroutines.tasks.await

// The `appUpdateTv` node this class used to read is gone: update checks now go
// through the backend's /app-version, the same endpoint the phone uses, so the
// admin panel governs both apps. Shipping a TV update no longer means editing
// Firebase by hand while every other release goes through the admin screen.

class FirebaseService(firebaseDatabase: FirebaseDatabase) {

    private val newsRef: DatabaseReference = firebaseDatabase.getReference("news")

    suspend fun getNews(): List<NewsItem> {
        val snapshot = newsRef.get().await()
        return snapshot.children.mapNotNull { it.getValue(NewsItem::class.java) }
    }
}