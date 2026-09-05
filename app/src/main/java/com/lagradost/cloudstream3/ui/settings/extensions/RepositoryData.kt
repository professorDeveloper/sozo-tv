package com.lagradost.cloudstream3.ui.settings.extensions

/**
 * A repository entry as CloudStream's extensions screen models it. See
 * [com.lagradost.cloudstream3.utils.Event]'s file for why these classes exist.
 *
 * Only ever handed out empty, by [com.lagradost.cloudstream3.plugins.RepositoryManager].
 */
data class RepositoryData(
    val name: String = "",
    val url: String = "",
)
