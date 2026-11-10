package com.saikou.sozo_tv.player

import com.saikou.sozo_tv.domain.player.SourceSelection
import com.saikou.sozo_tv.parser.models.AudioType
import com.saikou.sozo_tv.parser.models.VideoOption
import org.junit.Assert.*
import org.junit.Test

class SourceSelectionTest {
    private fun option(url: String, quality: String, server: String = "A") =
        VideoOption(url, server, quality, AudioType.SUB, quality, false, fullText = quality)
    @Test fun rememberedQualitySurvivesProviderReorder() {
        val chosen = option("a", "1080p")
        assertEquals(1, SourceSelection.initial(listOf(option("b", "720p"), chosen), SourceSelection.label(chosen), 0))
    }
    @Test fun startupCapWinsOverPreviouslySelectedHigherQuality() {
        val high = option("a", "1080p")
        assertEquals(1, SourceSelection.initial(listOf(high, option("b", "720p"), option("c", "480p")), SourceSelection.label(high), 0, 720))
    }
    @Test fun dataSaverChoosesActualLowestNotFixed480() {
        val options = listOf(option("a", "1080p"), option("b", "720p"), option("c", "360p"))
        assertEquals(2, SourceSelection.initial(options, SourceSelection.label(options[0]), 0, -1))
    }
    @Test fun fallbackWrapsWithoutRetryingDuplicateUrls() {
        val options = listOf(option("a", "720p"), option("a", "1080p"), option("b", "720p"))
        assertNull(SourceSelection.next(options, 2, setOf("a", "b"), false))
        assertEquals(0, SourceSelection.next(options, 2, setOf("b"), false))
    }
    @Test fun codecFailurePrefersExplicitCompatibleAlternative() {
        val options = listOf(option("a", "1080p"), option("b", "1080p HEVC"), option("c", "720p AVC"))
        assertEquals(2, SourceSelection.next(options, 0, setOf("a"), true))
    }
    @Test fun rememberedServerWinsWhenItsResolutionIsMissing() {
        val dub = option("d1", "1080p", "Dub")
        val next = listOf(option("s1", "1080p", "Sub"), option("s2", "720p", "Sub"), option("d2", "720p", "Dub"))
        assertEquals(2, SourceSelection.initial(next, SourceSelection.label(dub), 0))
    }
}
