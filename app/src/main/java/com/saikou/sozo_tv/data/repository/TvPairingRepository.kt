package com.saikou.sozo_tv.data.repository

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.util.UUID

class TvPairingRepository(
    private val db: FirebaseDatabase
) {
    private val root = db.reference.child("tv_pair_sessions")

    data class Session(
        val sid: String, val qrPayload: String
    )

    fun createSession(
        tvDeviceId: String, ttlMs: Long = 2 * 60_000L, onResult: (Result<Session>) -> Unit
    ) {
        val sid = UUID.randomUUID().toString().replace("-", "").take(10).uppercase()
        val now = System.currentTimeMillis()

        val data = mapOf(
            "status" to "waiting",
            "createdAt" to now,
            "expiresAt" to (now + ttlMs),
            "tvDeviceId" to tvDeviceId
        )

        root.child(sid).setValue(data).addOnSuccessListener {
            val payload = """{"t":"tv_pair","sid":"$sid","v":1}"""
            onResult(Result.success(Session(sid, payload)))
        }.addOnFailureListener { e ->
            onResult(Result.failure(e))
        }
    }

    fun listenToken(
        sid: String, onToken: (String) -> Unit, onError: (String) -> Unit
    ): ValueEventListener {
        val ref = root.child(sid).child("token")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val token = snapshot.getValue(String::class.java)
                if (!token.isNullOrBlank()) onToken(token)
            }

            override fun onCancelled(error: DatabaseError) {
                onError(error.message)
            }
        }

        ref.addValueEventListener(listener)
        return listener
    }

    fun deleteSession(sid: String) {
        root.child(sid).removeValue()
    }

    fun stopListenToken(sid: String, listener: ValueEventListener) {
        root.child(sid).child("token").removeEventListener(listener)
    }

    fun markPaired(sid: String) {
        root.child(sid).removeValue()
    }
}
