package com.saikou.sozo_tv.presentation.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saikou.sozo_tv.data.extensions.ExtensionContentRegistry
import com.saikou.sozo_tv.data.local.entity.WatchHistoryEntity
import com.saikou.sozo_tv.domain.repository.WatchHistoryRepository
import com.saikou.sozo_tv.parser.base.BaseParser
import com.saikou.sozo_tv.parser.models.EpisodeData
import com.saikou.sozo_tv.parser.models.ShowResponse
import com.saikou.sozo_tv.parser.sources.AnimeSources
import com.saikou.sozo_tv.parser.sources.ExtensionParser
import com.saikou.sozo_tv.utils.LocalData
import com.saikou.sozo_tv.utils.Resource
import kotlinx.coroutines.launch

class EpisodeViewModel(
    private val watchHistoryRepository: WatchHistoryRepository,
) : ViewModel() {
    val episodeData: MutableLiveData<Resource<EpisodeData>> =
        MutableLiveData<Resource<EpisodeData>>(Resource.Idle)
    val dataFound: MutableLiveData<Resource<ShowResponse>> = MutableLiveData()

    var parser: BaseParser = AnimeSources.getCurrent()
    var epListFromLocal = ArrayList<WatchHistoryEntity>()

    fun loadEpisodeByPage(page: Int, id: String, showResponse: ShowResponse) {
        episodeData.value = Resource.Loading
        viewModelScope.launch {
            val result = parser.loadEpisodes(id, page, showResponse)
            if (result != null) {
                episodeData.value = Resource.Success(result)
            } else {
                episodeData.value = Resource.Error(Exception("Episode not found"))
            }
        }
    }

    private fun getEpisodesLocal() {
        viewModelScope.launch {
            epListFromLocal.clear()
            epListFromLocal.addAll(watchHistoryRepository.getAllHistory())
        }
    }

    /** Legacy title search — retained only for the (now unused) WrongTitleDialog. */
    fun findEpisodes(title: String, isAdult: Boolean = false) {
        viewModelScope.launch {
            dataFound.value = Resource.Loading
            val medias = parser.search(title)
            if (medias.isNotEmpty()) {
                getEpisodesLocal()
                dataFound.value = Resource.Success(medias[0])
            } else {
                dataFound.value =
                    Resource.Error(Exception("Media not found wrong title or romanji"))
            }
        }
    }

    /**
     * Open the EXACT media the user selected. [mediaId] is the registry id that carries the
     * content's own (provider, url), so we load that directly instead of searching by title
     * (which could match the wrong entry). Falls back to a title search only for legacy ids
     * that have no registry entry.
     */
    fun loadMedia(mediaId: Int, fallbackTitle: String) {
        viewModelScope.launch {
            dataFound.value = Resource.Loading
            val entry = ExtensionContentRegistry.resolve(mediaId)
            if (entry != null && entry.provider.isNotEmpty() && entry.url.isNotEmpty()) {
                getEpisodesLocal()
                dataFound.value = Resource.Success(
                    ShowResponse(
                        name = entry.title ?: fallbackTitle,
                        link = ExtensionParser.encodeLink(entry.provider, entry.url),
                        coverUrl = entry.thumbnail ?: LocalData.anime404,
                    )
                )
            } else {
                val medias = parser.search(fallbackTitle)
                if (medias.isNotEmpty()) {
                    getEpisodesLocal()
                    dataFound.value = Resource.Success(medias[0])
                } else {
                    dataFound.value = Resource.Error(Exception("Media not found"))
                }
            }
        }
    }
}
