package com.saikou.sozo_tv.player

import com.saikou.sozo_tv.domain.player.TranslationTarget
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class TranslationTargetTest {

    @Test
    fun `device language is the target`() {
        assertEquals("ru", TranslationTarget.forLocale(Locale("ru", "RU")))
        assertEquals("uz", TranslationTarget.forLocale(Locale("uz", "UZ")))
    }

    @Test
    fun `an english device falls back to uzbek`() {
        assertEquals("uz", TranslationTarget.forLocale(Locale.ENGLISH))
        assertEquals("uz", TranslationTarget.forLocale(Locale.US))
    }

    @Test
    fun `a blank language falls back to uzbek`() {
        assertEquals("uz", TranslationTarget.forLocale(Locale.ROOT))
    }

    @Test
    fun `display name is the language name`() {
        val prev = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
        try {
            assertEquals("Uzbek", TranslationTarget.displayName("uz"))
            assertEquals("Russian", TranslationTarget.displayName("ru"))
        } finally {
            Locale.setDefault(prev)
        }
    }

    @Test
    fun `an unknown code is shown upper-cased`() {
        assertEquals("ZZ", TranslationTarget.displayName("zz"))
    }
}
