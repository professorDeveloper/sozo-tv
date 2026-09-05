package com.saikou.sozo_tv.domain.player

object SeasonNumber {

    private val PATTERN = Regex(
        """(?:\bs\s*(\d{1,2})\s*[.\-_ ]?\s*e\s*\d{1,3}\b)""" +
            """|(?:\b(?:season|сезон|сезона|fasl|mavsum)\s*[:.\-]?\s*(\d{1,2})\b)""" +
            """|(?:\b(\d{1,2})\s*[-\s]?\s*(?:fasl|mavsum|сезон)\b)""",
        RegexOption.IGNORE_CASE,
    )

    fun of(vararg sources: String?): Int? {
        for (source in sources) {
            if (source.isNullOrBlank()) continue
            val match = PATTERN.find(source) ?: continue
            val raw = match.groupValues.drop(1).firstOrNull { it.isNotEmpty() } ?: continue
            val n = raw.toIntOrNull() ?: continue
            if (n > 0) return n
        }
        return null
    }
}
