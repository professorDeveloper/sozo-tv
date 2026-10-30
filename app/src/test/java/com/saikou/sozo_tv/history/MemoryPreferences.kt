package com.saikou.sozo_tv.history

import android.content.SharedPreferences
import java.lang.reflect.Proxy

object MemoryPreferences {
    fun memoryPrefs(): SharedPreferences {
        val values = mutableMapOf<String, Any?>()
        lateinit var editor: SharedPreferences.Editor
        editor = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SharedPreferences.Editor::class.java)) { _, method, args ->
            when (method.name) {
                "putString", "putLong", "putInt", "putBoolean", "putFloat", "putStringSet" -> { values[args[0] as String] = args[1]; editor }
                "remove" -> { values.remove(args[0]); editor }
                "clear" -> { values.clear(); editor }
                "commit" -> true
                "apply" -> null
                else -> null
            }
        } as SharedPreferences.Editor
        return Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SharedPreferences::class.java)) { _, method, args ->
            when (method.name) {
                "edit" -> editor
                "getAll" -> values.toMap()
                "contains" -> values.containsKey(args[0])
                "getString", "getLong", "getInt", "getBoolean", "getFloat", "getStringSet" -> values[args[0]] ?: args[1]
                else -> null
            }
        } as SharedPreferences
    }
}
