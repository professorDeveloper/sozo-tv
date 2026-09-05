package com.saikou.sozo_tv.domain.player

import java.util.Locale

object TranslationTarget {

    fun forLocale(locale: Locale): String {
        val code = locale.language.lowercase(Locale.ROOT)
        return if (code.isBlank() || code == "en") "uz" else code
    }

    fun displayName(code: String): String {
        val name = runCatching { Locale.forLanguageTag(code).displayLanguage }.getOrNull().orEmpty()
        if (name.isBlank() || name.equals(code, ignoreCase = true)) return code.uppercase(Locale.ROOT)
        return name.replaceFirstChar { it.uppercase() }
    }
}
