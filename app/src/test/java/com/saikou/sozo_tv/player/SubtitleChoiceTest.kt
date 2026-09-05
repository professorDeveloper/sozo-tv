package com.saikou.sozo_tv.player

import com.saikou.sozo_tv.domain.player.SubtitleChoice
import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleChoiceTest {
    @Test
    fun `the label as written wins`() {
        val labels = listOf("English", "Arabic", "Spanish")

        assertEquals(1, SubtitleChoice.indexFor(labels, "Arabic"))
    }

    @Test
    fun `case alone does not lose the choice`() {
        assertEquals(1, SubtitleChoice.indexFor(listOf("English", "ARABIC"), "Arabic"))
    }

    @Test
    fun `a three-letter code matches the language it names`() {
        assertEquals(1, SubtitleChoice.indexFor(listOf("English", "ara"), "Arabic"))
        assertEquals(0, SubtitleChoice.indexFor(listOf("en", "Arabic"), "English"))
    }

    @Test
    fun `an SDH marker appearing or disappearing does not change the language`() {
        assertEquals(1, SubtitleChoice.indexFor(listOf("Arabic", "English [SDH]"), "English"))
    }

    @Test
    fun `a script suffix does not hide the language`() {
        val labels = listOf("English", "Arabic - العربية")

        assertEquals(1, SubtitleChoice.indexFor(labels, "ar"))
    }

    @Test
    fun `nothing remembered is not a match`() {
        assertEquals(-1, SubtitleChoice.indexFor(listOf("English"), null))
        assertEquals(-1, SubtitleChoice.indexFor(listOf("English"), "  "))
    }

    @Test
    fun `a language that is simply absent reports no match rather than the first row`() {
        assertEquals(-1, SubtitleChoice.indexFor(listOf("English", "Spanish"), "Arabic"))
    }

    @Test
    fun `an exact row is preferred over another row of the same language`() {
        val labels = listOf("English [SDH]", "English")

        assertEquals(1, SubtitleChoice.indexFor(labels, "English"))
    }
}
