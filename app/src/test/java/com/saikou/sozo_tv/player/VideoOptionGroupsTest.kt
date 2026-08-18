package com.saikou.sozo_tv.player

import com.saikou.sozo_tv.domain.player.VideoOptionGroups
import com.saikou.sozo_tv.parser.models.AudioType
import com.saikou.sozo_tv.parser.models.VideoOption
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoOptionGroupsTest {

    private fun opt(server: String, res: String) = VideoOption(
        videoUrl = "https://x/$server/$res",
        fansub = server,
        resolution = res,
        audioType = AudioType.SUB,
        quality = res,
        isActive = true,
        fullText = "$server $res",
    )

    private val options = listOf(
        opt("Alpha", "1080p"),
        opt("Alpha", "720p"),
        opt("Beta", "720p"),
        opt("Beta", "480p"),
        opt("Beta", "1080p"),
    )

    @Test
    fun `servers are distinct and keep source order`() {
        assertEquals(listOf("Alpha", "Beta"), VideoOptionGroups.servers(options))
    }

    @Test
    fun `indices point back into the original list`() {
        assertEquals(listOf(2, 3, 4), VideoOptionGroups.indicesFor(options, "Beta"))
    }

    @Test
    fun `switching server keeps the same resolution when it exists`() {
        assertEquals(2, VideoOptionGroups.switchTo(options, currentIndex = 1, server = "Beta"))
    }

    @Test
    fun `switching to a server without that resolution takes its highest`() {
        val sparse = listOf(opt("Alpha", "1080p"), opt("Beta", "480p"), opt("Beta", "720p"))
        assertEquals(2, VideoOptionGroups.switchTo(sparse, currentIndex = 0, server = "Beta"))
    }

    @Test
    fun `switching to an unknown server changes nothing`() {
        assertEquals(1, VideoOptionGroups.switchTo(options, currentIndex = 1, server = "Nope"))
    }

    @Test
    fun `a blank host name still groups under one label`() {
        val blanks = listOf(opt("", "1080p"), opt("   ", "720p"))
        assertEquals(listOf("Default"), VideoOptionGroups.servers(blanks))
    }

    @Test
    fun `resolution is read from either field`() {
        assertEquals(1080, VideoOptionGroups.resolutionOf(opt("A", "1080p")))
        val odd = VideoOption(
            videoUrl = "u", fansub = "A", resolution = "auto", audioType = AudioType.SUB,
            quality = "HD 720", isActive = true, fullText = "",
        )
        assertEquals(720, VideoOptionGroups.resolutionOf(odd))
    }

    @Test
    fun `serverOf is safe on an out of range index`() {
        assertEquals("", VideoOptionGroups.serverOf(options, 99))
    }
}
