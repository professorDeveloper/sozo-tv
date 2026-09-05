package com.saikou.sozo_tv.domain.player

import java.util.Locale

object SubtitleChoice {

    fun indexFor(labels: List<String>, remembered: String?): Int {
        val wanted = remembered?.trim().orEmpty()
        if (wanted.isEmpty() || labels.isEmpty()) return -1

        labels.indexOfFirst { it == wanted }.let { if (it >= 0) return it }
        labels.indexOfFirst { it.equals(wanted, ignoreCase = true) }.let { if (it >= 0) return it }

        val key = keyOf(wanted)
        labels.indexOfFirst { keyOf(it) == key }.let { if (it >= 0) return it }

        val language = languageOf(wanted) ?: return -1
        return labels.indexOfFirst { languageOf(it) == language }
    }

    private fun keyOf(label: String): String =
        label.lowercase(Locale.ROOT).replace(DECORATION, "").filter { it.isLetterOrDigit() }

    private fun languageOf(label: String): String? {
        val head = label.lowercase(Locale.ROOT).replace(DECORATION, "").trim()
            .split(' ', '-', '_', '/', ',', '(')
            .firstOrNull { it.length >= 2 && it.all { c -> c in 'a'..'z' } }
            ?: return null

        if (head.length in 2..3) iso3(head)?.let { return it }
        return NAMES[head]?.let { iso3(it) }
    }

    private fun iso3(code: String): String? = runCatching {
        Locale.forLanguageTag(code).isO3Language.takeIf { it.isNotEmpty() }
    }.getOrNull()

    private val DECORATION = Regex("""[\[(](?:sdh|cc|forced|hi)[\])]""", RegexOption.IGNORE_CASE)

    /** Not from Locale.getAvailableLocales(): a stripped TV image resolves almost none of these. */
    private val NAMES: Map<String, String> = mapOf(
        "english" to "en", "arabic" to "ar", "russian" to "ru", "ukrainian" to "uk",
        "spanish" to "es", "portuguese" to "pt", "french" to "fr", "german" to "de",
        "italian" to "it", "turkish" to "tr", "persian" to "fa", "farsi" to "fa",
        "hindi" to "hi", "urdu" to "ur", "bengali" to "bn", "indonesian" to "id",
        "malay" to "ms", "thai" to "th", "vietnamese" to "vi", "japanese" to "ja",
        "korean" to "ko", "chinese" to "zh", "dutch" to "nl", "polish" to "pl",
        "romanian" to "ro", "greek" to "el", "hebrew" to "he", "swedish" to "sv",
        "norwegian" to "no", "danish" to "da", "finnish" to "fi", "czech" to "cs",
        "hungarian" to "hu", "bulgarian" to "bg", "serbian" to "sr", "croatian" to "hr",
        "uzbek" to "uz", "kazakh" to "kk", "azerbaijani" to "az", "tagalog" to "tl",
        "filipino" to "tl", "tamil" to "ta", "telugu" to "te", "malayalam" to "ml",
    )
}
