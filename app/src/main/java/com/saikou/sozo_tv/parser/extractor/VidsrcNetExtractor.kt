package com.saikou.sozo_tv.parser.extractor

import android.util.Base64
import com.saikou.sozo_tv.converter.JsoupConverterFactory
import com.saikou.sozo_tv.parser.models.Video
import org.jsoup.nodes.Document
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Url

class VidsrcNetExtractor : Extractor() {

    override val name = "Vidsrc.net"
    override val mainUrl = "https://vidsrc-embed.ru"

    override fun server(videoType: Video.Type): Video.Server {
        return Video.Server(
            id = name,
            name = name,
            src = when (videoType) {
                is Video.Type.Episode -> "$mainUrl/embed/tv?tmdb=${videoType.tvShow.id}&season=${videoType.season.number}&episode=${videoType.number}"
                is Video.Type.Movie -> "$mainUrl/embed/movie?tmdb=${videoType.id}"
            },
        )
    }

    override suspend fun extract(link: String): Video {
        val service = Service.build(mainUrl)

        val iframedoc = service.get(link)
            .selectFirst("iframe#player_iframe")?.attr("src")
            ?.let { if (it.startsWith("//")) "https:$it" else it }
            ?: throw Exception("Can't retrieve rcp")

        val doc = service.get(iframedoc, referer = link)
        val directM3u8 = Regex("""file:\s*["']([^"']+\.m3u8[^"']*)["']""")
            .find(doc.toString())?.groupValues?.get(1)

        if (directM3u8 != null) {
            val cleanUrl = cleanAndReplaceUrl(directM3u8)
            return Video(
                source = cleanUrl,
                subtitles = emptyList(),
                headers = mapOf("Referer" to iframedoc)
            )
        }

        val prorcp = Regex("src: '(/prorcp/.*?)'")
            .find(doc.toString())?.groupValues?.get(1)
            ?.let { iframedoc.substringBefore("/rcp") + it }
            ?: throw Exception("Can't retrieve prorcp")

        val script = service.get(
            prorcp,
            referer = iframedoc
        ).toString()

        val playerId = findPlayerId(script)
        if (playerId == null) {
            val fallbackM3u8 = Regex("""https?://[^"'\s]+\.m3u8""")
                .find(script)?.value
            println("Player ID not found, fallback m3u8: $script")
            if (fallbackM3u8 != null) {
                return Video(
                    source = cleanAndReplaceUrl(fallbackM3u8),
                    subtitles = emptyList(),
                    headers = mapOf("Referer" to iframedoc)
                )
            }
            throw Exception("Can't retrieve player ID or m3u8")
        }

        val encryptedSource =
            Regex("""<div id="$playerId" style="display:none;">\s*(.*?)\s*</div>""")
                .find(script)?.groupValues?.get(1)
                ?: throw Exception("Can't retrieve encrypted source")

        val decryptedData = decrypt(playerId, encryptedSource)
        val streamUrl = decryptedData.split(" or ")
            .firstOrNull()
            ?.let { cleanAndReplaceUrl(it) }
            ?: throw Exception("No stream found after decryption")

        return Video(
            source = streamUrl,
            subtitles = emptyList(),
            headers = mapOf("Referer" to iframedoc)
        )
    }

    private fun findPlayerId(script: String): String? {
        val pattern1 = Regex("Playerjs.*file:\\s*([a-zA-Z0-9]*?)\\s*,")
        val match1 = pattern1.find(script)?.groupValues?.get(1)
        if (!match1.isNullOrEmpty()) return match1
        val pattern2 = Regex("new Playerjs\\(\\{.*?file:\\s*([a-zA-Z0-9]+)")
        val match2 = pattern2.find(script)?.groupValues?.get(1)
        if (!match2.isNullOrEmpty()) return match2

        val pattern3 = Regex("""new Playerjs\(.*?file:\s*([a-zA-Z0-9]+)\s*""")
        val match3 = pattern3.find(script)?.groupValues?.get(1)
        if (!match3.isNullOrEmpty()) return match3

        val pattern4 = Regex("""file:\s*([a-zA-Z0-9]+)\s*""")
        val allMatches = pattern4.findAll(script).toList()
        return allMatches.lastOrNull()?.groupValues?.get(1)
    }

    private fun cleanAndReplaceUrl(url: String): String {
        val vSubs = mapOf(
            "v1" to "shadowlandschronicles.com",
            "v2" to "cloudnestra.com",
            "v3" to "thepixelpioneer.com",
            "v4" to "putgate.org",
            "v5" to "quibblezoomfable.com"
        )

        var result = url
        vSubs.forEach { (key, value) ->
            result = result.replace("{$key}", value)
        }
        result = result.replace(Regex("\\{[a-z]\\d+\\}"), "quibblezoomfable.com")
        val urls = result.split(" or ")
        return urls.first().trim()
    }

    private fun decrypt(id: String, encrypted: String): String {
        return when (id) {
            "NdonQLf1Tzyx7bMG" -> NdonQLf1Tzyx7bMG(encrypted)
            "sXnL9MQIry" -> sXnL9MQIry(encrypted)
            "IhWrImMIGL" -> IhWrImMIGL(encrypted)
            "xTyBxQyGTA" -> xTyBxQyGTA(encrypted)
            "ux8qjPHC66" -> ux8qjPHC66(encrypted)
            "eSfH1IRMyL" -> eSfH1IRMyL(encrypted)
            "KJHidj7det" -> KJHidj7det(encrypted)
            "o2VSUnjnZl" -> o2VSUnjnZl(encrypted)
            "Oi3v1dAlaM" -> Oi3v1dAlaM(encrypted)
            "TsA2KGDGux" -> TsA2KGDGux(encrypted)
            "JoAHUMCLXV" -> JoAHUMCLXV(encrypted)
            else -> throw Exception("Encryption type not implemented yet: $id")
        }
    }

    // Dekriyatsiya funksiyalari (oldingi kabi)
    private fun NdonQLf1Tzyx7bMG(a: String): String {
        val b = 3
        val c = mutableListOf<String>()
        for (d in a.indices step b) {
            c.add(a.substring(d, minOf(d + b, a.length)))
        }
        return c.reversed().joinToString("")
    }

    private fun sXnL9MQIry(a: String): String {
        val b = "pWB9V)[*4I`nJpp?ozyB~dbr9yt!_n4u"
        val d = a.chunked(2).map { it.toInt(16).toChar() }.joinToString("")
        var c = ""
        for (e in d.indices) {
            c += (d[e].code xor b[e % b.length].code).toChar()
        }
        var e = ""
        for (ch in c) {
            e += (ch.code - 3).toChar()
        }
        return String(Base64.decode(e, Base64.DEFAULT))
    }

    private fun IhWrImMIGL(a: String): String {
        val b = a.reversed()
        val c = b.map { ch ->
            when {
                (ch in 'a'..'m') || (ch in 'A'..'M') -> (ch.code + 13).toChar()
                (ch in 'n'..'z') || (ch in 'N'..'Z') -> (ch.code - 13).toChar()
                else -> ch
            }
        }.joinToString("")
        val d = c.reversed()
        return String(Base64.decode(d, Base64.DEFAULT))
    }

    private fun xTyBxQyGTA(a: String): String {
        val b = a.reversed()
        val c = b.filterIndexed { index, _ -> index % 2 == 0 }
        return Base64.decode(c, Base64.DEFAULT).toString(Charsets.UTF_8)
    }

    private fun ux8qjPHC66(a: String): String {
        val b = a.reversed()
        val c = "X9a(O;FMV2-7VO5x;Ao\u0005:dN1NoFs?j,"
        val d = b.chunked(2).map { it.toInt(16).toChar() }.joinToString("")
        var e = ""
        for (i in d.indices) {
            e += (d[i].code xor c[i % c.length].code).toChar()
        }
        return e
    }

    private fun eSfH1IRMyL(a: String): String {
        val b = a.reversed()
        val c = b.map { (it.code - 1).toChar() }.joinToString("")
        val d = c.chunked(2).map { it.toInt(16).toChar() }.joinToString("")
        return d
    }

    private fun KJHidj7det(a: String): String {
        val b = a.substring(10, a.length - 16)
        val c = "3SAY~#%Y(V%>5d/Yg\"\$G[Lh1rK4a;7ok"
        val d = String(Base64.decode(b, Base64.DEFAULT))
        val e = c.repeat((d.length + c.length - 1) / c.length).take(d.length)
        var f = ""
        for (i in d.indices) {
            f += (d[i].code xor e[i].code).toChar()
        }
        return f
    }

    private fun o2VSUnjnZl(a: String): String {
        val shift = 3
        return a.map { char ->
            when (char) {
                in 'a'..'z' -> {
                    val shifted = char - shift
                    if (shifted < 'a') shifted + 26 else shifted
                }

                in 'A'..'Z' -> {
                    val shifted = char - shift
                    if (shifted < 'A') shifted + 26 else shifted
                }

                else -> char
            }
        }.joinToString("")
    }

    private fun Oi3v1dAlaM(a: String): String {
        val b = a.reversed()
        val c = b.replace("-", "+").replace("_", "/")
        val d = String(Base64.decode(c, Base64.DEFAULT))
        var e = ""
        val f = 5
        for (ch in d) {
            e += (ch.code - f).toChar()
        }
        return e
    }

    private fun TsA2KGDGux(a: String): String {
        val b = a.reversed()
        val c = b.replace("-", "+").replace("_", "/")
        val d = String(Base64.decode(c, Base64.DEFAULT))
        var e = ""
        val f = 7
        for (ch in d) {
            e += (ch.code - f).toChar()
        }
        return e
    }

    private fun JoAHUMCLXV(a: String): String {
        val b = a.reversed()
        val c = b.replace("-", "+").replace("_", "/")
        val d = String(Base64.decode(c, Base64.DEFAULT))
        var e = ""
        val f = 3
        for (ch in d) {
            e += (ch.code - f).toChar()
        }
        return e
    }

    private interface Service {
        companion object {
            fun build(baseUrl: String): Service {
                val retrofit = Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .addConverterFactory(JsoupConverterFactory.create())
                    .build()
                return retrofit.create(Service::class.java)
            }
        }

        @GET
        suspend fun get(
            @Url url: String,
            @Header("referer") referer: String = "",
        ): Document
    }
}