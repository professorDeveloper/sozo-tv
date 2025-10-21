package com.saikou.sozo_tv.data.remote

import android.util.Log
import com.saikou.sozo_tv.utils.Utils
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.select.Elements
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlin.math.log

const val BASE_URL = "https://www.imdb.com/"

fun String.extractViId(): String? {
    val regex = Regex("""/(vi\d+)/""")
    val match = regex.find(this)
    return match?.groupValues?.get(1)
}

class IMDBScraping {
    fun getTrailerLink(trailerUrl: String): String {
        Log.d("GGG", "getTrailerLink: Trailer url :$trailerUrl ")
        val document = Utils.getJsoup(
            BASE_URL + "video/embed/$trailerUrl",
            mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36",
                "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8"
            )
        )

        val scriptData = document.select("script#__NEXT_DATA__").first()?.html()
            ?: return "MP4 URL not found"

        val regex = Regex(""""url"\s*:\s*"([^"]*\.mp4\?[^"]*)"""")
        val matches = regex.findAll(scriptData)

        val mp4Urls = matches.map { it.groupValues[1] }.toList()

        return if (mp4Urls.isNotEmpty()) {
            println("Found MP4 URLs:")
            return mp4Urls[0].toString()
        } else {
            println("MP4 URL not found")
            return "data"
        }
    }

    suspend fun getTrailer(item: SearchItem): Pair<String, CastResponse> {
        val document = Utils.getJsoup(
            item.detailsUrl,
            mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36",
                "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9",
            )
        )


        val scriptData = document.select("script#__NEXT_DATA__").first()?.html() ?: ""

        val regex = Regex("""(https://[^"']+\.m3u8[^"']*)""")
        val matchResult = regex.find(scriptData)
        val secondResponse = getCasts(document)
        if (matchResult != null) {
            println("Founded m3u8 URL: ${matchResult.value}")
            Log.d("MMM", "getTrailer: {matchResult.value}")
            return Pair(matchResult.value, secondResponse)
        } else {
            println("m3u8 URL notfound")
            Log.d("MMM", "getTrailer:m3u8 URL notfound ")
            return Pair("m3u8 URL notfound", secondResponse)
        }

    }

    data class SearchItem(
        val title: String,
        val year: String,
        val cast: String,
        val imageUrl: String,
        val detailsUrl: String
    )

    suspend fun searchMovie(query: String): SearchItem {
        val request = Utils.getJsoup(
            "$BASE_URL/find/?q=$query", mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36",
                "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9",
            )
        )

        val doc = request.body()

        val elements: Elements = doc.select("li.find-title-result")
        for (element in elements) {
            val title = element.select("a.ipc-metadata-list-summary-item__t").text()
            val year = element.select("ul.ipc-metadata-list-summary-item__tl li").text()
            val cast = element.select("ul.ipc-metadata-list-summary-item__stl li").text()
            val imageUrl = element.select("img.ipc-image").attr("src")
            val detailsUrl =
                "https://www.imdb.com" + element.select("a.ipc-metadata-list-summary-item__t")
                    .attr("href")
            println(detailsUrl)
            return SearchItem(title, year, cast, imageUrl, detailsUrl)
        }
        return SearchItem("", "", "", "", "")
    }

    fun getCasts(item: Document): CastResponse {
        val doc = item
        val castList = mutableListOf<CastItem>()

        val elements: Elements = doc.select("div[data-testid='title-cast-item']")
        for (element in elements) {
            val name = element.select("a[data-testid='title-cast-item__actor']").text()

            val character = element.select("span.sc-cd7dc4b7-4.zVTic").text()

            var imageUrl = element.select("img.ipc-image").attr("src")
            imageUrl = imageUrl.ifEmpty {
                "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcTuwuOLp_6JHHonDLbwBvqOFDxEI6kLoz6EVIqFJLWi5IonCPl7CUfDnzNyaN9pwhLSUTw&usqp=CAU"
            }

            val detailsUrl =
                "https://www.imdb.com" + element.select("a.ipc-lockup-overlay").attr("href")

            castList.add(
                CastItem(
                    name, character, ImageUrlFormatter.formatImageUrl(imageUrl), detailsUrl
                )
            )
        }

        return CastResponse(castList)
    }

    fun getPhotos(item: IMDBScraping.SearchItem): PhotosResponse {
        val document = Utils.getJsoup(
            item.detailsUrl,
            mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36",
                "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9",
            )
        )
        val doc = document
        val photos = mutableListOf<PhotoItem>()

        val elements: Elements = doc.select("a.sc-180dfae3-0.hDscSA")
        for (element in elements) {
            val imageUrl = element.select("img.ipc-image").attr("src")
            val altText = element.select("img.ipc-image").attr("alt")
            val detailsUrl = "https://www.imdb.com" + element.attr("href")

            photos.add(PhotoItem(imageUrl.replace(Regex("UX\\d+"), "UX920"), altText, detailsUrl))
        }


        return PhotosResponse(photos)
    }


}

data class CastResponse(
    val cast: List<CastItem>
)

data class CastItem(
    val name: String, val character: String, val imageUrl: String, val detailsUrl: String
)


object UnsafeOkHttpClient {
    fun getUnsafeOkHttpClient(): OkHttpClient {
        return try {
            // Create a trust manager that does not validate certificate chains
            val trustAllCerts = arrayOf<TrustManager>(
                object : X509TrustManager {
                    override fun checkClientTrusted(
                        chain: Array<X509Certificate>,
                        authType: String
                    ) {
                    }

                    override fun checkServerTrusted(
                        chain: Array<X509Certificate>,
                        authType: String
                    ) {
                    }

                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                }
            )

            // Install the all-trusting trust manager
            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, SecureRandom())
            val sslSocketFactory = sslContext.socketFactory

            // Build the OkHttpClient with the custom trust manager and hostname verifier
            OkHttpClient.Builder()
                .sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
                .hostnameVerifier { _, _ -> true }
                .build()
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

}

data class PhotosResponse(
    val photos: List<PhotoItem>
)

data class PhotoItem(
    val imageUrl: String, val altText: String, val detailsUrl: String
)

object ImageUrlFormatter {
    fun formatImageUrl(imageUrl: String): String {
        val updatedUrl = imageUrl.replace(Regex("UX\\d+"), "UX800")

        // CR orqasidagi oxirgi ikki raqamni 400 ga o'zgartirish
        return updatedUrl.replace(Regex("CR\\d+,\\d+,\\d+,\\d+"), "CR0,0,800,800")
    }
}

