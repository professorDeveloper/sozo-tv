package com.saikou.sozo_tv.utils

import android.content.Context
import android.content.SharedPreferences

class SearchHistoryManager(context: Context) {
    
    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    companion object {
        private const val PREFS_NAME = "search_history"
        private const val KEY_SEARCH_HISTORY = "recent_searches"
        private const val MAX_HISTORY_SIZE = 10
        private const val DELIMITER = "|||"
    }
    
    fun addSearchQuery(query: String) {
        val currentHistory = getSearchHistory().toMutableList()
        
        currentHistory.remove(query)
        
        currentHistory.add(0, query)
        
        if (currentHistory.size > MAX_HISTORY_SIZE) {
            currentHistory.removeAt(currentHistory.size - 1)
        }
        
        saveSearchHistory(currentHistory)
    }
    
    fun getSearchHistory(): List<String> {
        val historyString = sharedPreferences.getString(KEY_SEARCH_HISTORY, "") ?: ""
        return if (historyString.isEmpty()) {
            emptyList()
        } else {
            historyString.split(DELIMITER).filter { it.isNotEmpty() }
        }
    }
    
    fun removeSearchQuery(query: String) {
        val currentHistory = getSearchHistory().toMutableList()
        currentHistory.remove(query)
        saveSearchHistory(currentHistory)
    }
    
    fun clearAllHistory() {
        sharedPreferences.edit().remove(KEY_SEARCH_HISTORY).apply()
    }
    
    private fun saveSearchHistory(history: List<String>) {
        val historyString = history.joinToString(DELIMITER)
        sharedPreferences.edit().putString(KEY_SEARCH_HISTORY, historyString).apply()
    }
}
