package com.lagradost

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The contract CloudStream plugins compile against.
 *
 * ## Why this test exists
 *
 * This app embeds `recloudstream:library`, the plugin-facing half of CloudStream. Plugins are
 * built against the whole APP, so they freely reference classes that live only in CloudStream's
 * application module — `MainActivity`, `PluginManager`, the AniList sync provider. Those are
 * declared under `com.lagradost.cloudstream3` so the references resolve.
 *
 * When one is missing, nothing says so at build time. Dalvik resolves on first touch,
 * `PluginHost.loadCs3` catches every Throwable, and the result is a plugin that downloads,
 * registers no provider and appears nowhere in the Sources screen. Five of the 80 extensions in
 * the phisher repo were dead this way, three of them on a hardcoded "unsupported" list.
 *
 * Every name and descriptor below was read off those plugins' own dex method tables, so this is
 * not a guess at a useful API — it is the set they actually call. Descriptors are asserted, not
 * just names: a class that exists with the wrong parameter or return type is a
 * `NoSuchMethodError`, which kills a plugin exactly as thoroughly as a missing class.
 *
 * Deleting a member because it "looks unused" breaks plugin loading with no compile error and no
 * other test failure anywhere, which is what this file is here to prevent.
 *
 * Reflection rather than direct calls on purpose: the point is that each member exists under the
 * exact name a plugin looks it up by, which a direct call would not prove for anything Kotlin
 * renames.
 */
class CloudStreamStubContractTest {

    private fun clazz(name: String): Class<*> =
        try {
            Class.forName(name)
        } catch (e: ClassNotFoundException) {
            throw AssertionError("Plugins reference $name — it must exist", e)
        }

    /** Assert a method exists with exactly [params] and [returns], the way a dex ref binds. */
    private fun requireMethod(
        owner: String,
        method: String,
        returns: Class<*>,
        vararg params: Class<*>,
    ) {
        val found = try {
            clazz(owner).getMethod(method, *params)
        } catch (e: NoSuchMethodException) {
            throw AssertionError(
                "$owner must expose $method(${params.joinToString { it.simpleName }})", e,
            )
        }
        assertEquals("$owner.$method must return ${returns.simpleName}", returns, found.returnType)
    }

    private fun requireField(owner: String, field: String) {
        val c = clazz(owner)
        val found = c.fields.any { it.name == field } || c.declaredFields.any { it.name == field }
        assertTrue("$owner must expose the field $field", found)
    }

    // ---------------------------------------------------------------- app --

    @Test
    fun `MainActivity exposes the events plugins subscribe to`() {
        // StreamPlay, StremioX and Ultima subscribe during load(); a missing MainActivity takes
        // the whole plugin down with it. The dex shows Companion as a FIELD on MainActivity —
        // which is how Kotlin compiles a companion object — and the getters on the Companion.
        requireField("com.lagradost.cloudstream3.MainActivity", "Companion")
        val companion = "com.lagradost.cloudstream3.MainActivity\$Companion"
        val event = clazz("com.lagradost.cloudstream3.utils.Event")
        requireMethod(companion, "getAfterPluginsLoadedEvent", event)
        requireMethod(companion, "getBookmarksUpdatedEvent", event)
        requireMethod(companion, "getReloadLibraryEvent", event)
    }

    @Test
    fun `Event can be published to and subscribed to`() {
        val name = "com.lagradost.cloudstream3.utils.Event"
        requireMethod(name, "invoke", Void.TYPE, Any::class.java)
        requireMethod(name, "plusAssign", Void.TYPE, Function1::class.java)
    }

    @Test
    fun `an Event reaches its subscribers`() {
        // The class alone would satisfy the loader, but a plugin that subscribes and is never
        // called back is a different bug from one that cannot load, and only this catches it.
        val seen = mutableListOf<Boolean>()
        val event = com.lagradost.cloudstream3.utils.Event<Boolean>()
        event += { seen += it }
        event(true)
        assertEquals(listOf(true), seen)
    }

    @Test
    fun `UiText renders to a String`() {
        requireMethod(
            "com.lagradost.cloudstream3.utils.UiText",
            "asString",
            String::class.java,
            android.content.Context::class.java,
        )
    }

    // ------------------------------------------------------------ plugins --

    @Test
    fun `PluginManager exposes the registry members plugins call`() {
        val name = "com.lagradost.cloudstream3.plugins.PluginManager"
        requireField(name, "INSTANCE")
        requireMethod(name, "getPluginsOnline", Array<com.lagradost.cloudstream3.plugins.PluginData>::class.java)
        requireMethod(name, "getPlugins", Map::class.java)
        requireMethod(name, "unloadPlugin", Void.TYPE, String::class.java)
        requireMethod(
            name, "getPluginPath", java.io.File::class.java,
            android.content.Context::class.java, String::class.java, String::class.java,
        )
        requireMethod(
            name, "loadSinglePlugin", Any::class.java,
            android.content.Context::class.java, String::class.java,
            kotlin.coroutines.Continuation::class.java,
        )
    }

    @Test
    fun `PluginData keeps the shape Ultima copies`() {
        // copy$default carries the whole primary constructor in its descriptor, so dropping a
        // field a plugin never reads still breaks the plugin that calls copy().
        val name = "com.lagradost.cloudstream3.plugins.PluginData"
        requireMethod(name, "getInternalName", String::class.java)
        requireMethod(name, "getFilePath", String::class.java)
        requireMethod(name, "getUrl", String::class.java)
        clazz(name).getDeclaredConstructor(
            String::class.java, String::class.java, Boolean::class.javaPrimitiveType,
            String::class.java, Int::class.javaPrimitiveType,
        )
    }

    @Test
    fun `PluginManager answers a plugin looking for its own file`() {
        // The one thing this registry really does: a plugin reads its own .cs3 path off
        // getPluginsOnline() to load a resource out of it.
        val manager = com.lagradost.cloudstream3.plugins.PluginManager
        manager.record("ContractTest", "/data/cs3/ContractTest@1.cs3")
        try {
            val entry = manager.getPluginsOnline().first { it.internalName == "ContractTest" }
            assertEquals("/data/cs3/ContractTest@1.cs3", entry.filePath)
        } finally {
            manager.forget("ContractTest")
        }
        assertTrue(
            "forget() must drop the entry",
            manager.getPluginsOnline().none { it.internalName == "ContractTest" },
        )
    }

    @Test
    fun `the repository types meta plugins walk are present`() {
        val repoData = com.lagradost.cloudstream3.ui.settings.extensions.RepositoryData::class.java
        requireField("com.lagradost.cloudstream3.plugins.RepositoryManager", "INSTANCE")
        requireMethod(
            "com.lagradost.cloudstream3.plugins.RepositoryManager",
            "getRepositories",
            Array<com.lagradost.cloudstream3.ui.settings.extensions.RepositoryData>::class.java,
        )
        requireMethod(
            "com.lagradost.cloudstream3.plugins.RepositoryManager", "getRepoPlugins",
            Any::class.java, repoData, kotlin.coroutines.Continuation::class.java,
        )
        requireMethod("com.lagradost.cloudstream3.plugins.SitePlugin", "getInternalName", String::class.java)
        requireMethod("com.lagradost.cloudstream3.plugins.SitePlugin", "getUrl", String::class.java)
        requireMethod(
            "com.lagradost.cloudstream3.plugins.PluginWrapper", "getPlugin",
            com.lagradost.cloudstream3.plugins.SitePlugin::class.java,
        )
        requireMethod(
            "com.lagradost.cloudstream3.plugins.PluginWrapper", "getRepositoryData", repoData,
        )
        requireMethod(repoData.name, "getUrl", String::class.java)
    }

    // ------------------------------------------------------- syncproviders --

    @Test
    fun `AccountManager hands out a non-null AniList client`() {
        // Plugins chain straight off it without a null check, so a null here trades a no-op for
        // a NullPointerException inside the plugin.
        requireMethod(
            "com.lagradost.cloudstream3.syncproviders.AccountManager\$Companion",
            "getAniListApi",
            com.lagradost.cloudstream3.syncproviders.providers.AniListApi::class.java,
        )
        assertNotNull(
            "aniListApi must never be null",
            com.lagradost.cloudstream3.syncproviders.AccountManager.aniListApi,
        )
    }

    @Test
    fun `SyncRepo takes a SyncAPI and reports nobody signed in`() {
        // The constructor descriptor is what StreamPlay and TorraStream link against; a
        // SyncRepo(Object) would resolve as a class and then fail on the call.
        val api = com.lagradost.cloudstream3.syncproviders.AccountManager.aniListApi
        val repo = com.lagradost.cloudstream3.syncproviders.SyncRepo(api)
        assertEquals(
            null,
            com.lagradost.cloudstream3.syncproviders.SyncRepo::class.java
                .getMethod("authUser").invoke(repo),
        )
    }

    @Test
    fun `the sync library types are present`() {
        val base = "com.lagradost.cloudstream3.syncproviders.SyncAPI"
        requireMethod(
            "$base\$LibraryList", "getName", com.lagradost.cloudstream3.utils.UiText::class.java,
        )
        requireMethod("$base\$LibraryList", "getItems", List::class.java)
        requireMethod("$base\$LibraryMetadata", "getAllLibraryLists", List::class.java)
        // SyncIdName is NOT declared here — the `library` artifact ships it, and a second copy
        // is a duplicate class at dex time. This asserts the one plugins actually resolve.
        for (constant in listOf("Anilist", "MyAnimeList", "Trakt")) {
            requireField("com.lagradost.cloudstream3.syncproviders.SyncIdName", constant)
        }
    }

    @Test
    fun `the AniList response shapes plugins destructure are present`() {
        val base = "com.lagradost.cloudstream3.syncproviders.providers.AniListApi"
        // Both title shapes: AniList returns `title` under two schemas depending on the query,
        // and missing either one is as fatal as missing all of them.
        requireMethod("$base\$MediaTitle", "getRomaji", String::class.java)
        requireMethod("$base\$MediaTitle", "getEnglish", String::class.java)
        requireMethod("$base\$Title", "getRomaji", String::class.java)
        requireMethod("$base\$Title", "getEnglish", String::class.java)

        requireMethod("$base\$CoverImage", "getMedium", String::class.java)
        requireMethod("$base\$CoverImage", "getLarge", String::class.java)
        requireMethod("$base\$CoverImage", "getExtraLarge", String::class.java)
        requireMethod("$base\$MediaCoverImage", "getLarge", String::class.java)
        requireMethod("$base\$RecommendedMedia", "getId", Int::class.javaObjectType)
        requireMethod("$base\$RecommendedMedia", "getTitle", clazz("$base\$MediaTitle"))
        requireMethod("$base\$RecommendedMedia", "getCoverImage", clazz("$base\$MediaCoverImage"))
        requireMethod("$base\$Recommendation", "getMediaRecommendation", clazz("$base\$RecommendedMedia"))
        requireMethod("$base\$RecommendationEdge", "getNode", clazz("$base\$Recommendation"))
        requireMethod("$base\$RecommendationConnection", "getEdges", List::class.java)
        requireMethod("$base\$LikePageInfo", "getHasNextPage", Boolean::class.javaObjectType)
        requireMethod("$base\$SeasonNextAiringEpisode", "getEpisode", Int::class.javaObjectType)
    }

    @Test
    fun `AniListApi is assignable to SyncAPI`() {
        // StreamPlay passes it straight into SyncRepo(SyncAPI). A class that exists but is not
        // assignable fails verification just as loudly as a missing one.
        assertTrue(
            com.lagradost.cloudstream3.syncproviders.SyncAPI::class.java.isAssignableFrom(
                com.lagradost.cloudstream3.syncproviders.providers.AniListApi::class.java,
            ),
        )
    }

    // -------------------------------------------------------------- utils --

    @Test
    fun `the odds and ends meta plugins reach for are present`() {
        requireField("com.lagradost.cloudstream3.utils.AppContextUtils", "INSTANCE")
        requireMethod(
            "com.lagradost.cloudstream3.utils.AppContextUtils", "setDefaultFocus", Void.TYPE,
            androidx.appcompat.app.AlertDialog::class.java, Int::class.javaPrimitiveType!!,
        )
        requireMethod(
            "com.lagradost.cloudstream3.utils.DataStoreHelper\$ResumeWatchingResult",
            "getId", Int::class.javaObjectType,
        )
        requireMethod(
            "com.lagradost.cloudstream3.utils.DataStoreHelper\$ResumeWatchingResult",
            "getParentId", Int::class.javaObjectType,
        )
        requireField("com.lagradost.cloudstream3.ui.home.HomeViewModel", "Companion")
        requireMethod(
            "com.lagradost.cloudstream3.ui.home.HomeViewModel\$Companion", "getResumeWatching",
            Any::class.java, kotlin.coroutines.Continuation::class.java,
        )
    }

    @Test
    fun `AtomicMutableList actually stores what is added to it`() {
        // A no-op collection would be a silent wrong answer rather than a missing feature —
        // a plugin adds to one and expects to read it back.
        val list = com.lagradost.cloudstream3.utils.AtomicMutableList<String>()
        list.add("x")
        assertTrue("AtomicMutableList must retain its contents", list.contains("x"))
    }
}
