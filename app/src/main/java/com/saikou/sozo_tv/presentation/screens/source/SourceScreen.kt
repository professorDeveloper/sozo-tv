package com.saikou.sozo_tv.presentation.screens.source

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.saikou.sozo_tv.adapters.SourceAdapter
import com.saikou.sozo_tv.data.model.SubSource
import com.saikou.sozo_tv.databinding.SourceScreenBinding
import androidx.leanback.widget.VerticalGridView
import com.saikou.sozo_tv.R

class SourceScreen : Fragment() {
    private var _binding: SourceScreenBinding? = null
    private val binding get() = _binding!!
    private lateinit var dbRef: DatabaseReference
    private lateinit var adapter: SourceAdapter

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

        adapter = SourceAdapter(
            onClick = { sub -> /* Handle click */ },
            onDelete = { sub -> }
        )

        binding.sourceRv.adapter = adapter
        binding.progressBar.visibility = View.VISIBLE
        binding.sourcePlaceHolder.root.visibility = View.GONE
        loadSources()
    }

    private fun loadSources() {
        dbRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                binding.progressBar.visibility = View.GONE
                if (!snapshot.exists() || snapshot.children.count() == 0) {
                    binding.sourcePlaceHolder.root.visibility = View.VISIBLE
                    binding.sourceRv.visibility = View.GONE
                } else {
                    val list = arrayListOf<SubSource>()
                    for (child in snapshot.children) {
                        child.getValue(SubSource::class.java)?.let { list.add(it) }
                    }
                    binding.sourcePlaceHolder.root.visibility = View.GONE
                    binding.sourceRv.visibility = View.VISIBLE
                    adapter.updateList(list)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                binding.progressBar.visibility = View.GONE
                binding.sourcePlaceHolder.root.visibility = View.VISIBLE
                binding.sourceRv.visibility = View.GONE
            }
        })
    }


}