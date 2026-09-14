package com.saikou.sozo_tv.data.extensions

/** All installed sources, with the selected source first and duplicate ids queried once. */
internal fun searchCandidates(
    providers: List<ExtProvider>,
    active: String?,
    limit: Int = Int.MAX_VALUE,
): List<ExtProvider> = providers.distinctBy { it.id }
    .sortedByDescending { it.id == active }
    .take(limit.coerceAtLeast(0))
