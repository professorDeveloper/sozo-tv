package com.saikou.sozo_tv.utils

import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.saikou.sozo_tv.data.model.Source
import com.saikou.sozo_tv.data.model.SubSource

fun loadSources(onResult: (ArrayList<Source>) -> Unit) {
    val database = FirebaseDatabase.getInstance().getReference("sources")
    database.addListenerForSingleValueEvent(object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            val sourcesList = arrayListOf<Source>()

            if (snapshot.exists()) {
                for (child in snapshot.children) {
                    val source = child.getValue(Source::class.java)
                    if (source != null) {
                        sourcesList.add(source)
                    }
                }
                onResult(sourcesList)
            } else {
                val defaultList = arrayListOf(
                    Source(
                        country = "NATIVE",
                        list = arrayListOf(
                            SubSource("pahe", "ANIMEPAHE", "native"),
                            SubSource("aniwatch", "ANIWATCH", "native")
                        )
                    )
                )

                database.setValue(defaultList)
                    .addOnSuccessListener { onResult(defaultList) }
                    .addOnFailureListener { onResult(defaultList) } // offline fallback
            }
        }

        override fun onCancelled(error: DatabaseError) {
            val fallback = arrayListOf(
                Source(
                    country = "NATIVE",
                    list = arrayListOf(
                        SubSource("pahe", "ANIMEPAHE", "native"),
                        SubSource("aniwatch", "ANIWATCH", "native")
                    )
                )
            )
            onResult(fallback)
        }
    })
}
