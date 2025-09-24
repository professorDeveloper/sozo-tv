package com.saikou.sozo_tv.utils

import android.content.Context
import android.content.SharedPreferences

class SearchHistoryManager(context: Context) {
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("search_history", Context.MODE_PRIVATE)

    companion object {
        private const val SEARCH_HISTORY_KEY = "search_history"
        private const val MAX_HISTORY_SIZE = 10

        private val DEFAULT_SEARCHES = listOf(
            "Naruto",
            "One Piece",
            "Attack on Titan",
            "Demon Slayer",
            "My Hero Academia"
        )
    }

    fun getSearchHistory(): List<String> {
        val historySet = sharedPreferences.getStringSet(SEARCH_HISTORY_KEY, emptySet()) ?: emptySet()
        val userHistory = historySet.toList()

        return if (userHistory.isEmpty()) {
            DEFAULT_SEARCHES
        } else {
            userHistory
        }
    }

    fun addSearchQuery(query: String) {
        val currentHistory = sharedPreferences.getStringSet(SEARCH_HISTORY_KEY, emptySet())?.toMutableSet() ?: mutableSetOf()

        // Remove if already exists to avoid duplicates
        currentHistory.remove(query)

        // Add to the beginning
        val newHistory = mutableSetOf<String>()
        newHistory.add(query)
        newHistory.addAll(currentHistory)

        // Keep only the most recent searches
        val limitedHistory = newHistory.take(MAX_HISTORY_SIZE).toSet()

        sharedPreferences.edit()
            .putStringSet(SEARCH_HISTORY_KEY, limitedHistory)
            .apply()
    }

    fun removeSearchQuery(query: String) {
        val currentHistory = sharedPreferences.getStringSet(SEARCH_HISTORY_KEY, emptySet())?.toMutableSet() ?: mutableSetOf()
        currentHistory.remove(query)

        sharedPreferences.edit()
            .putStringSet(SEARCH_HISTORY_KEY, currentHistory)
            .apply()
    }

    fun clearAllHistory() {
        sharedPreferences.edit()
            .remove(SEARCH_HISTORY_KEY)
            .apply()
    }

    fun isUsingDefaultSearches(): Boolean {
        val historySet = sharedPreferences.getStringSet(SEARCH_HISTORY_KEY, emptySet()) ?: emptySet()
        return historySet.isEmpty()
    }
}
