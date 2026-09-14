package com.saikou.sozo_tv.extensions

import com.saikou.sozo_tv.data.extensions.*
import org.junit.Assert.*
import org.junit.Test

class SearchCandidatesTest {
    @Test fun `global search includes sources beyond first twelve across groups`() {
        val providers = (1..30).map { ExtProvider("p$it", "Source $it", group = if(it < 15) ExtGroup.SERVER else ExtGroup.CLOUDSTREAM) }
        val selected = searchCandidates(providers + providers.first(), "p30")
        assertEquals(30, selected.size)
        assertEquals("p30", selected.first().id)
        assertEquals(providers.map { it.id }.toSet(), selected.map { it.id }.toSet())
    }
    @Test fun `explicit caller limit still keeps active provider first`() {
        val providers = (1..20).map { ExtProvider("p$it", "Source $it", group=ExtGroup.SERVER) }
        assertEquals(listOf("p20", "p1"), searchCandidates(providers,"p20",2).map { it.id })
    }
}
