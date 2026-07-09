package com.saikou.sozo_tv.services

import android.util.Log
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.saikou.sozo_tv.data.model.NewsItem
import com.saikou.sozo_tv.domain.model.AppUpdate
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.cancellation.CancellationException

class FirebaseService(firebaseDatabase: FirebaseDatabase) {
    private val appUpdateRef = firebaseDatabase.reference.child("appUpdateTv")

    /**
     * One-shot read of the remote update descriptor; null when the node is empty or unreadable.
     * Suspending rather than LiveData-returning so the caller can put a timeout on it — the
     * splash blocks on the answer, and a Realtime Database read neither succeeds nor fails while
     * the client is stuck mid-handshake.
     */
    suspend fun fetchAppUpdate(): AppUpdate? = try {
        appUpdateRef.get().await().getValue(AppUpdate::class.java)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (t: Throwable) {
        Log.w("FirebaseService", "app update check failed", t)
        null
    }


    private val newsRef: DatabaseReference = firebaseDatabase.getReference("news")

    suspend fun getNews(): List<NewsItem> {
        val snapshot = newsRef.get().await()
        return snapshot.children.mapNotNull { it.getValue(NewsItem::class.java) }
    }
}