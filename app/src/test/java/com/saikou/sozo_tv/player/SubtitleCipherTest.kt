package com.saikou.sozo_tv.player

import com.saikou.sozo_tv.data.extensions.ExtParser
import com.saikou.sozo_tv.domain.player.SubtitleCipher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class SubtitleCipherTest {
    private val cipher = SubtitleCipher("aes-128-cbc", "AmSmZVcH93UQUezi", "ReBKWW8cqdjPEnF6", "cue-text", "base64")

    @Test
    fun `kisskh cue text is decrypted and timing is kept`() {
        val body = "1\r\n00:00:01,000 --> 00:00:02,500\r\nSq/yslS8glo0CoTCWZBT0GepZqSNY4KqATqtSAO1798=\r\n\r\n"

        val out = cipher.decrypt(body)

        assertEquals("1\r\n00:00:01,000 --> 00:00:02,500\r\n<i>♫ Set off ♫</i>\n\r\n", out)
    }

    @Test
    fun `a wrong key fails instead of showing ciphertext`() {
        val body = "1\n00:00:01,000 --> 00:00:02,500\nSq/yslS8glo0CoTCWZBT0GepZqSNY4KqATqtSAO1798=\n"
        try {
            cipher.copy(key = "8056483646328763").decrypt(body)
            fail("expected failure")
        } catch (_: IOException) {
        }
    }

    @Test
    fun `an unknown algorithm is refused`() {
        try {
            cipher.copy(alg = "aes-256-gcm").decrypt("1\n00:00:01,000 --> 00:00:02,000\nx\n")
            fail("expected failure")
        } catch (_: IOException) {
        }
    }

    @Test
    fun `the media payload carries the cipher to the subtitle`() {
        val media = ExtParser.media(
            """{"videoSources":[{"videoUrl":"https://cdn/v.m3u8"}],"subtitles":[""" +
                """{"file":"https://sub/a.srt.txt1","label":"English","cipher":{"alg":"aes-128-cbc","key":"AmSmZVcH93UQUezi","iv":"ReBKWW8cqdjPEnF6","scope":"cue-text","encoding":"base64"}},""" +
                """{"file":"https://sub/b.srt","label":"Korean"}]}"""
        )

        assertEquals(cipher, media.subtitles[0].cipher)
        assertNull(media.subtitles[1].cipher)
    }
}
