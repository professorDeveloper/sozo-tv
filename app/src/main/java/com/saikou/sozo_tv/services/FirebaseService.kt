package com.saikou.sozo_tv.services

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.saikou.sozo_tv.data.model.NewsItem
import com.saikou.sozo_tv.domain.model.AppUpdate
import kotlinx.coroutines.tasks.await

class FirebaseService(firebaseDatabase: FirebaseDatabase) {
    private val appUpdateRef = firebaseDatabase.reference.child("appUpdateTv")

    fun getAppUpdateInfo(): LiveData<AppUpdate?> {
        val appUpdateLiveData = MutableLiveData<AppUpdate?>()

        appUpdateRef.get()
            .addOnSuccessListener { snapshot ->
                val appUpdate = snapshot.getValue(AppUpdate::class.java)
                Log.d("FirebaseService", "compareVersions: ${appUpdate?.versionCode}")
                appUpdateLiveData.postValue(appUpdate)
            }
            .addOnFailureListener {
                appUpdateLiveData.postValue(null)
            }

        return appUpdateLiveData
    }


    private val newsRef: DatabaseReference = firebaseDatabase.getReference("news")

    suspend fun getNews(): List<NewsItem> {
        val snapshot = newsRef.get().await()
        return snapshot.children.mapNotNull { it.getValue(NewsItem::class.java) }
    }
}