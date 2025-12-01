package com.saikou.sozo_tv.parser

import com.saikou.sozo_tv.domain.model.MainModel
import com.saikou.sozo_tv.parser.anime.Video
import com.saikou.sozo_tv.parser.models.EpisodeData
import com.saikou.sozo_tv.parser.models.ShowResponse
import com.saikou.sozo_tv.parser.models.VideoOption
import com.saikou.sozo_tv.utils.readData
import com.saikou.sozo_tv.utils.saveData
import java.io.Serializable
import java.net.URLDecoder
import java.net.URLEncoder

abstract class BaseParser {

    /**
     * Name that will be shown in Source Selection
     * **/
    open val name: String = ""

    /**
     * Name used to save the ShowResponse selected by user or by autoSearch
     * **/
    open val saveName: String = ""

    /**
     * The main URL of the Site
     * **/
    open val hostUrl: String = ""

    /**
     * override as `true` if the site **only** has NSFW media
     * **/
    open val isNSFW = false

    /**
     * mostly redundant for official app, But override if you want to add different languages
     * **/
    open val language = "English"

    open var showUserText = ""
    open var showUserTextListener: ((String) -> Unit)? = null

    /**
     * Used to show messages & errors to the User, a useful way to convey what's currently happening or what was done.
     * **/
    fun setUserText(string: String) {
        showUserText = string
        showUserTextListener?.invoke(showUserText)
    }

    open suspend fun getEpisodeVideo(id: String, epId: String): List<VideoOption> = emptyList()

    abstract suspend fun loadEpisodes(
        id: String,
        page: Int = 1,
        showResponse: ShowResponse
    ): EpisodeData?

    open suspend fun extractVideo(url: String): String = ""

    abstract suspend fun search(query: String): List<ShowResponse>
    fun encode(input: String): String = URLEncoder.encode(input, "utf-8").replace("+", "%20")
    fun decode(input: String): String = URLDecoder.decode(input, "utf-8")
}
