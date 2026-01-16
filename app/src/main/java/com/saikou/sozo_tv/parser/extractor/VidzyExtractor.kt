package com.saikou.sozo_tv.parser.extractor

import com.saikou.sozo_tv.p_a_c_k_e_r.JsUnpacker
import com.saikou.sozo_tv.parser.models.Video
import com.saikou.sozo_tv.utils.Utils

class VidzyExtractor : Extractor() {

    override val name = "Vidzy"
    override val mainUrl = "https://vidzy.org"

    override suspend fun extract(link: String): Video {
        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36",
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
            "Accept-Language" to "en-US,en;q=0.5",
            "Referer" to mainUrl
        )

        val html = Utils.get(link, headers)

        val functionStart = "function(p,a,c,k,e,d){"
        val htmlAfterFunction = html.substringAfter(functionStart)

        if (htmlAfterFunction == html) {
            throw Exception("Packed JS not found - function not found")
        }

        val packedJSRegex = Regex("""(\}\s*\('.*?'\.split\('\|'\))""")
        val packedJSMatch = packedJSRegex.find(htmlAfterFunction)
            ?: throw Exception("Packed JS pattern not found")

        val packedJS = packedJSMatch.groupValues[1]

        val unPacked = JsUnpacker(packedJS).unpack() ?: ""

        val fileMatch = Regex("""src\s*:\s*["']([^"']+)["']""").find(unPacked)
        val streamUrl = fileMatch?.groupValues?.get(1)
            ?: throw Exception("No src found in unpacked JS")

        return Video(
            source = streamUrl,
            headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36",
                "Referer" to mainUrl
            )
        )
    }

}