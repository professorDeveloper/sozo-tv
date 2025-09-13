package com.saikou.sozo_tv.services

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.database.FirebaseDatabase
import com.saikou.sozo_tv.domain.model.AppUpdate

class FirebaseService(
    private val firebaseDatabase: FirebaseDatabase
) {
    private val appUpdateRef = firebaseDatabase.reference.child("appUpdateTv")

    fun getAppUpdateInfo(): LiveData<AppUpdate?> {
        val appUpdateLiveData = MutableLiveData<AppUpdate?>()

        appUpdateRef.get()
            .addOnSuccessListener { snapshot ->
                val appUpdate = snapshot.getValue(AppUpdate::class.java)
                Log.d("FirebaseService", "compareVersions: ${appUpdate?.version}")
                appUpdateLiveData.postValue(appUpdate)
            }
            .addOnFailureListener {
                appUpdateLiveData.postValue(null)
            }

        return appUpdateLiveData
    }
}