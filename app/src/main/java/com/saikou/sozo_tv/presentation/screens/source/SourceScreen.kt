package com.saikou.sozo_tv.presentation.screens.source

import android.annotation.SuppressLint
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
import com.saikou.sozo_tv.data.local.pref.PreferenceManager
import com.saikou.sozo_tv.parser.sources.SourceManager
import com.saikou.sozo_tv.utils.readData
import com.saikou.sozo_tv.utils.saveData

class SourceScreen : Fragment() {
    private var _binding: SourceScreenBinding? = null
    private val binding get() = _binding!!
    private lateinit var dbRef: DatabaseReference
    private lateinit var adapter: SourceAdapter
    private var currentSelectedSource = readData<String>("subSource") ?: ""
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = SourceScreenBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dbRef = FirebaseDatabase.getInstance().getReference("sources")

        adapter = SourceAdapter(
            onClick = { sub ->
                binding.textView6.text = "Current Selected Source: ${sub.title}"
                saveData("subSource", sub.sourceId)
            },
        )
        binding.sourceRv.adapter = adapter
        binding.progressBar.visibility = View.VISIBLE
        binding.sourcePlaceHolder.root.visibility = View.GONE
        loadSources()
    }

    private fun loadSources() {
        dbRef.addListenerForSingleValueEvent(object : ValueEventListener {
            @SuppressLint("SetTextI18n")
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
                    saveData("sources", list)
                    binding.sourcePlaceHolder.root.visibility = View.GONE
                    binding.sourceRv.visibility = View.VISIBLE
                    adapter.updateList(list)
                    adapter.setSelectedIndex(
                        currentSelectedSource
                    )
                    val selected = list.find { it.sourceId == currentSelectedSource }
                    if (selected != null) {
                        SourceManager.setCurrentSource(selected.sourceId)
                        saveData("subSource", selected.sourceId)
                        binding.textView6.text = "Current Selected Source: ${selected.title}"
                    } else {
                        binding.textView6.text = "Source"
                    }
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