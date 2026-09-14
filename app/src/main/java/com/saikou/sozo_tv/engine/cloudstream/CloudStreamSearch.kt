package com.saikou.sozo_tv.engine.cloudstream

import com.lagradost.cloudstream3.MainAPI

/** Paged API also delegates to legacy search implementations. Let failures reach the caller. */
internal suspend fun searchCloudStream(api: MainAPI, query: String) = api.search(query, 1)
