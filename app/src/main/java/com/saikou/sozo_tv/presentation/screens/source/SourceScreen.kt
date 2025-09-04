package com.saikou.sozo_tv.presentation.screens.source

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.adapters.SourceHeaderAdapter
import com.saikou.sozo_tv.adapters.SourcePageAdapter
import com.saikou.sozo_tv.data.model.Source
import com.saikou.sozo_tv.data.model.SubSource
import com.saikou.sozo_tv.databinding.SourceScreenBinding
import com.saikou.sozo_tv.utils.SourceUi

class SourceScreen : Fragment() {
    private var _binding: SourceScreenBinding? = null
    private val binding get() = _binding!!
    private lateinit var dbRef: DatabaseReference
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = SourceScreenBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dbRef = FirebaseDatabase.getInstance().getReference("sources")
        loadSources {
            val adapter = SourceHeaderAdapter()
            binding.sourceRv.adapter = adapter
            adapter.submitList(it)
        }
    }

    private fun loadSources(onResult: (ArrayList<SourceUi>) -> Unit) {
        dbRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    // Default data map
                    val defaultData: Map<String, Source> = mapOf(
                        "NATIVE" to Source(
                            country = "NATIVE",
                            list = mapOf(
                                "Hianime" to SubSource("hianime", "Hianime", "native"),
                                "aniwatch" to SubSource("aniwatch", "ANIWATCH", "native")
                            )
                        )
                    )
                    dbRef.setValue(defaultData).addOnSuccessListener {
                        onResult(defaultData.toUiList())
                    }
                } else {
                    onResult(snapshot.toUiList())
                }
            }

            override fun onCancelled(error: DatabaseError) {
                onResult(arrayListOf())
            }
        })
    }

    private fun Map<String, Source>.toUiList(): ArrayList<SourceUi> {
        val list = arrayListOf<SourceUi>()
        for ((_, value) in this) {
            list.add(SourceUi(value.country, ArrayList(value.list.values)))
        }
        return list
    }

    private fun DataSnapshot.toUiList(): ArrayList<SourceUi> {
        val list = arrayListOf<SourceUi>()
        for (child in children) {
            val country = child.child("country").getValue(String::class.java) ?: continue
            val subs = arrayListOf<SubSource>()
            for (subNode in child.child("list").children) {
                subNode.getValue(SubSource::class.java)?.let { subs.add(it) }
            }
            list.add(SourceUi(country, subs))
        }
        return list
    }
}

