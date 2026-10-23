package com.saikou.sozo_tv.adapters

import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListUpdateCallback
import com.saikou.sozo_tv.domain.model.SearchModel
import org.junit.Assert.*
import org.junit.Test

class SearchResultDiffTest {
    private fun movie(id: Int?, title: String = "Title $id") =
        SearchModel(id, title, "poster-$id", null, null, null)

    private fun updates(old: List<SearchModel>, new: List<SearchModel>): IntArray {
        val counts = IntArray(4)
        DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = old.size
            override fun getNewListSize() = new.size
            override fun areItemsTheSame(a: Int, b: Int) = SearchResultDiff.areItemsTheSame(old[a], new[b])
            override fun areContentsTheSame(a: Int, b: Int) = SearchResultDiff.areContentsTheSame(old[a], new[b])
        }).dispatchUpdatesTo(object : ListUpdateCallback {
            override fun onInserted(position: Int, count: Int) { counts[0] += count }
            override fun onRemoved(position: Int, count: Int) { counts[1] += count }
            override fun onMoved(fromPosition: Int, toPosition: Int) { counts[2]++ }
            override fun onChanged(position: Int, count: Int, payload: Any?) { counts[3] += count }
        })
        return counts
    }

    @Test fun incrementalProviderResultsKeepExistingThousandPostersBound() {
        val old = (0 until 1000).map { movie(it) }
        assertArrayEquals(intArrayOf(50, 0, 0, 0), updates(old, old + (1000 until 1050).map { movie(it) }))
    }

    @Test fun unchangedRefreshDispatchesNothingAndTitleChangeOnlyOneCard() {
        val old = (0 until 1000).map { movie(it) }
        assertArrayEquals(intArrayOf(0, 0, 0, 0), updates(old, old.toList()))
        val next = old.toMutableList().apply { this[77] = this[77].copy(title = "New title") }
        assertArrayEquals(intArrayOf(0, 0, 0, 1), updates(old, next))
    }

    @Test fun unknownIdsDoNotMergeUnrelatedResults() {
        assertFalse(SearchResultDiff.areItemsTheSame(movie(null, "First"), movie(null, "Second")))
        assertTrue(SearchResultDiff.areItemsTheSame(movie(3), movie(3).copy(title = "Updated")))
    }
}
