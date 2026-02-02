package com.saikou.sozo_tv.parser.extractor

import com.saikou.sozo_tv.p_a_c_k_e_r.JsUnpacker
import com.saikou.sozo_tv.parser.models.Video
import com.saikou.sozo_tv.utils.Utils
import org.mozilla.javascript.Context

class MixDropExtractor : Extractor() {

    override val name = "MixDrop"
    override val mainUrl = "https://mixdrop.co"
    override val aliasUrls = listOf(
        "https://mixdrop.bz",
        "https://mixdrop.ag",
        "https://mixdrop.ch",
        "https://mixdrop.to",
        "https://mixdrop.cv",
        "https://mxdrop.to",
        "https://mixdrop.club",
        "https://m1xdrop.net"
    )
    override val rotatingDomain = listOf(
        Regex("^md[3bfyz][a-z0-9]*\\.[a-z0-9]+", RegexOption.IGNORE_CASE)
    )

    companion object {
        private const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"
    }

    override suspend fun extract(link: String): Video {
        val processedLink = link
            .replace("/f/", "/e/")
            .replace(".club/", ".ag/")
            .replace(Regex("^(https?://[^/]+/e/[^/?#]+).*$", RegexOption.IGNORE_CASE), "$1")

        val headers = mapOf(
            "User-Agent" to DEFAULT_USER_AGENT,
            "Referer" to mainUrl
        )

        val document = Utils.getJsoup(processedLink, headers)
        val html = document.toString()
        val packedJS = Regex("(eval\\(function\\(p,a,c,k,e,d\\)(.|\\n)*?)</script>")
            .find(html)
            ?.groupValues?.get(1)

        val script = JsUnpacker(packedJS).unpack() ?: html

        val srcRegex = Regex("""wurl.*?=.*?"(.*?)";""")
        val sourceUrl = srcRegex.find(script)?.groupValues?.get(1)
            ?: throw Exception("Source not found")

        val finalUrl = when {
            sourceUrl.startsWith("//") -> "https:$sourceUrl"
            sourceUrl.startsWith("http") -> sourceUrl
            else -> "https://$sourceUrl"
        }

        return Video(
            source = finalUrl,
            headers = mapOf(
                "User-Agent" to DEFAULT_USER_AGENT,
                "Referer" to mainUrl
            )
        )
    }

}