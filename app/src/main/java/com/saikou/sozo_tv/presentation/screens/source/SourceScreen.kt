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
import com.saikou.sozo_tv.data.local.pref.PreferenceManager
import com.saikou.sozo_tv.data.model.SubSource
import com.saikou.sozo_tv.databinding.SourceScreenBinding
import com.saikou.sozo_tv.utils.LocalData.SOURCE
import com.saikou.sozo_tv.utils.readData
import com.saikou.sozo_tv.utils.saveData

import androidx.lifecycle.lifecycleScope
import com.saikou.sozo_tv.R
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class SourceScreen : Fragment() {
    private var _binding: SourceScreenBinding? = null
    private val binding get() = _binding!!
    private lateinit var dbRef: DatabaseReference
    private lateinit var adapter: SourceAdapter

    private val currentSelectedSource by lazy { PreferenceManager().getString(SOURCE) }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = SourceScreenBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        dbRef = FirebaseDatabase.getInstance().getReference("sources")

        adapter = SourceAdapter(onClick = { sub ->
            binding.textView6.text = getString(R.string.current_selected_source, sub.title)
            saveData(SOURCE, sub.sourceId)
            adapter.setSelectedIndex(sub.sourceId)
        })
        binding.sourceRv.adapter = adapter

        showLoading()

        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                fetchSourcesOnce()
            }.onSuccess { list ->
                showSources(list)
            }.onFailure {
                showErrorState()
            }
        }
    }

    private suspend fun fetchSourcesOnce(): List<SubSource> {
        val snapshot = dbRef.get().await()
        return snapshot.children.mapNotNull { it.getValue(SubSource::class.java) }
    }

    @SuppressLint("SetTextI18n")
    private fun showSources(list: List<SubSource>) {
        binding.progressBar.visibility = View.GONE

        if (list.isEmpty()) {
            binding.sourcePlaceHolder.root.visibility = View.VISIBLE
            binding.sourceRv.visibility = View.GONE
            return
        }

        binding.sourcePlaceHolder.root.visibility = View.GONE
        binding.sourceRv.visibility = View.VISIBLE

        adapter.updateList(ArrayList(list))
        adapter.setSelectedIndex(currentSelectedSource)

        val selected = list.find { it.sourceId == currentSelectedSource }
        binding.textView6.text = if (selected != null) requireActivity().getString(
            R.string.current_selected_source,
            selected.title
        )
        else getString(R.string.source)
    }

    private fun showLoading() {
        binding.progressBar.visibility = View.VISIBLE
        binding.sourcePlaceHolder.root.visibility = View.GONE
        binding.sourceRv.visibility = View.VISIBLE
    }

    private fun showErrorState() {
        binding.progressBar.visibility = View.GONE
        binding.sourcePlaceHolder.root.visibility = View.VISIBLE
        binding.sourceRv.visibility = View.GONE
    }

}
