package com.saikou.sozo_tv.utils

import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.saikou.sozo_tv.data.model.Source
import com.saikou.sozo_tv.data.model.SubSource
data class SourceUi(
    val country: String,
    val list: ArrayList<SubSource>
)