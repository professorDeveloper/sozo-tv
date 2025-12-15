package com.saikou.sozo_tv.parser.movie.helper

import android.annotation.SuppressLint
import android.util.Log
import com.saikou.sozo_tv.utils.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Base64

object SourceHelper {
    fun String.normalizeStreamUrl(): String {
        val cleaned = this.replace(Regex("[^\\x20-\\x7E]"), "")

        val plIndex = cleaned.indexOf("/pl/")
        if (plIndex == -1) return cleaned

        val plPart = cleaned.substring(plIndex)
        return "https://tmstr2.thrumbleandjaxon.com$plPart"
    }
    private fun httpsify(url: String): String {
        return if (url.startsWith("//")) "https:$url" else url
    }

    suspend fun extractIframeUrl(url: String): String? = withContext(Dispatchers.IO) {

        val takeIf = httpsify(
            Utils.getJsoup(url).select("iframe").attr("src")
        ).takeIf { it.isNotEmpty() }
        Log.d("GGG", "extractIframeUrl: ${takeIf}")
        return@withContext takeIf
    }

    @SuppressLint("NewApi")
    fun base64Decode(input: String): ByteArray {
        return Base64.getDecoder().decode(input)
    }

    @SuppressLint("NewApi")
    fun base64Encode(input: String): String {
        return Base64.getEncoder().encodeToString(input.toByteArray())
    }

    val decryptMethods: Map<String, (String) -> String> = mapOf("TsA2KGDGux" to { input ->
        val decoded = String(
            base64Decode(
                input.reversed().replace("-", "+").replace("_", "/")
            )
        )
        decoded.map { (it.code - 7).toChar() }.joinToString("")
    },

        "ux8qjPHC66" to { input ->
            val reversed = input.reversed()
            val hex = reversed.chunked(2).map { it.toInt(16).toChar() }.joinToString("")
            val key = "X9a(O;FMV2-7VO5x;Ao\u0005:dN1NoFs?j,"
            hex.mapIndexed { i, ch ->
                (ch.code xor key[i % key.length].code).toChar()
            }.joinToString("")
        },

        "xTyBxQyGTA" to { input ->
            val filtered = input.reversed().filterIndexed { i, _ -> i % 2 == 0 }
            String(base64Decode(filtered))
        },

        "IhWrImMIGL" to { input ->
            val rot13 = input.reversed().map { ch ->
                when {
                    ch in 'a'..'m' || ch in 'A'..'M' -> (ch.code + 13).toChar()
                    ch in 'n'..'z' || ch in 'N'..'Z' -> (ch.code - 13).toChar()
                    else -> ch
                }
            }.joinToString("")
            String(base64Decode(rot13.reversed()))
        },

        "o2VSUnjnZl" to { input ->
            val map =
                ("xyzabcdefghijklmnopqrstuvwXYZABCDEFGHIJKLMNOPQRSTUVW" zip "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ").toMap()
            input.map { map[it] ?: it }.joinToString("")
        },

        "eSfH1IRMyL" to { input ->
            val shifted = input.reversed().map { (it.code - 1).toChar() }.joinToString("")
            shifted.chunked(2).map { it.toInt(16).toChar() }.joinToString("")
        },

        "Oi3v1dAlaM" to { input ->
            val decoded = String(
                base64Decode(
                    input.reversed().replace("-", "+").replace("_", "/")
                )
            )
            decoded.map { (it.code - 5).toChar() }.joinToString("")
        },

        "sXnL9MQIry" to { input ->
            val xorKey = "pWB9V)[*4I`nJpp?ozyB~dbr9yt!_n4u"
            val hex = input.chunked(2).map { it.toInt(16).toChar() }.joinToString("")
            val xored = hex.mapIndexed { i, ch ->
                (ch.code xor xorKey[i % xorKey.length].code).toChar()
            }.joinToString("")
            val shifted = xored.map { (it.code - 3).toChar() }.joinToString("")
            String(base64Decode(shifted))
        },

        "JoAHUMCLXV" to { input ->
            val decoded = String(
                base64Decode(
                    input.reversed().replace("-", "+").replace("_", "/")
                )
            )
            decoded.map { (it.code - 3).toChar() }.joinToString("")
        },

        "KJHidj7det" to { input ->
            val decoded = String(
                base64Decode(input.drop(10).dropLast(16))
            )
            val key = """3SAY~#%Y(V%>5d/Yg${'$'}G[Lh1rK4a;7ok"""
            decoded.mapIndexed { i, ch ->
                (ch.code xor key[i % key.length].code).toChar()
            }.joinToString("")
        },

        "playerjs" to { x ->
            try {
                var a = x.drop(2)

                val b1: (String) -> String = { base64Encode(it) }
                val b2: (String) -> String = { String(base64Decode(it)) }

                val patterns = listOf(
                    "*,4).(_)()", "33-*.4/9[6", ":]&*1@@1=&", "=(=:19705/", "%?6497.[:4"
                )

                patterns.forEach { k ->
                    a = a.replace("/@#@/" + b1(k), "")
                }

                b2(a)
            } catch (e: Exception) {
                "Failed to decode: ${e.message}"
            }
        })

}