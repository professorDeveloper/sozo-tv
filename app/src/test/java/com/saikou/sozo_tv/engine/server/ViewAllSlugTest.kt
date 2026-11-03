package com.saikou.sozo_tv.engine.server

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ViewAllSlugTest {
    @Test fun sectionWithoutViewAllHasNoTarget() {
        assertNull(viewAllSlug(null))
        assertNull(viewAllSlug(JSONObject()))
        assertNull(viewAllSlug(JSONObject().put("type", JSONObject.NULL).put("slug", "")))
    }

    @Test fun typeAloneIsATarget() {
        assertEquals("latest::", viewAllSlug(JSONObject().put("type", "latest")))
    }

    @Test fun typeAndSlugAreJoined() {
        assertEquals("category::ongoing", viewAllSlug(JSONObject().put("type", "category").put("slug", "ongoing")))
    }
}
