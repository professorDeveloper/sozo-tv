package com.saikou.sozo_tv.player

import com.saikou.sozo_tv.domain.player.SeasonNumber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SeasonNumberTest {

    @Test
    fun `reads the s-e form`() {
        assertEquals(3, SeasonNumber.of("Dark S03E05 1080p"))
        assertEquals(2, SeasonNumber.of("show.s2.e10.web"))
    }

    @Test
    fun `reads a spelled out season in several languages`() {
        assertEquals(4, SeasonNumber.of("The Boys Season 4"))
        assertEquals(2, SeasonNumber.of("Ведьмак сезон 2"))
        assertEquals(5, SeasonNumber.of("Qaytish 5-fasl"))
        assertEquals(3, SeasonNumber.of("3 mavsum"))
    }

    @Test
    fun `a digit in the title is not a season`() {
        assertNull(SeasonNumber.of("3 Body Problem"))
        assertNull(SeasonNumber.of("Attack on Titan"))
    }

    @Test
    fun `the first source that names a season wins`() {
        assertEquals(2, SeasonNumber.of(null, "", "Series S02E01", "Season 9"))
    }

    @Test
    fun `nothing to read gives null`() {
        assertNull(SeasonNumber.of(null, "", "   "))
    }
}
