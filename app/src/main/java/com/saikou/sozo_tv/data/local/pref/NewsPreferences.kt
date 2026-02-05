package com.saikou.sozo_tv.data.local.pref

import android.content.Context

class NewsPreferences(private val context: Context) {
    private val prefs = context.getSharedPreferences("news_prefs", Context.MODE_PRIVATE)

    fun markAsRead(newsId: String) {
        prefs.edit().putBoolean("read_$newsId", true).apply()
    }

    fun isRead(newsId: String): Boolean {
        return prefs.getBoolean("read_$newsId", false)
    }

    fun markAsUnread(newsId: String) {
        prefs.edit().remove("read_$newsId").apply()
    }

    fun getUnreadCount(newsIds: List<String>): Int {
        return newsIds.count { !isRead(it) }
    }

    fun clearAllReadStatus() {
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith("read_") }.forEach { key ->
            editor.remove(key)
        }
        editor.apply()
    }
}
