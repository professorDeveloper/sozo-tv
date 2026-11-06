package com.saikou.sozo_tv.history

import com.google.gson.Gson
import com.saikou.sozo_tv.data.local.pref.ActiveProfileStore
import com.saikou.sozo_tv.data.remote.lists.*
import com.saikou.sozo_tv.data.repository.UserListsRepository
import com.saikou.sozo_tv.domain.model.HouseholdProfile
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class UserListsConcurrencyTest {
    @Test fun `slow refresh cannot overwrite queued addition`() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val repo = repository { request ->
            if (request.method == "GET") {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
                """{"items":[]}"""
            } else "{}"
        }
        val refresh = async(Dispatchers.Default) { repo.refresh(UserListKind.WATCH_LATER) }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        val add = async(Dispatchers.Default) {
            repo.add(UserListKind.WATCH_LATER, UserListItem("p", "show", "Show"))
        }
        release.countDown()
        refresh.await()
        add.await()
        assertEquals(listOf("show"), repo.cached(UserListKind.WATCH_LATER).map { it.contentUrl })
    }

    @Test fun `signout discards in flight refresh`() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val repo = repository {
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS))
            """{"items":[{"provider":"p","contentUrl":"private-show"}]}"""
        }
        val refresh = async(Dispatchers.Default) { repo.refresh(UserListKind.WATCH_LATER) }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        repo.clear()
        release.countDown()
        assertTrue(refresh.await().isEmpty())
        assertTrue(repo.cached(UserListKind.WATCH_LATER).isEmpty())
    }

    @Test fun `queued old profile tap is discarded after profile switch`() = runBlocking {
        val profiles = ActiveProfileStore(MemoryPreferences.memoryPrefs(), Gson()).apply { set(HouseholdProfile("a", "A")) }
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val posts = java.util.concurrent.atomic.AtomicInteger()
        val repo = repository(profiles) { request ->
            if (request.method == "GET") {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
                """{"items":[]}"""
            } else { posts.incrementAndGet(); "{}" }
        }
        val refresh = async(Dispatchers.Default) { repo.refresh(UserListKind.WATCH_LATER) }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        val queued = async(start = CoroutineStart.UNDISPATCHED) {
            repo.add(UserListKind.WATCH_LATER, UserListItem("p", "old-profile-show"))
        }
        profiles.set(HouseholdProfile("b", "B"))
        release.countDown()
        refresh.await(); queued.await()
        assertEquals(0, posts.get())
        assertTrue(repo.cached(UserListKind.WATCH_LATER).isEmpty())
    }

    private fun repository(profiles: ActiveProfileStore = ActiveProfileStore(MemoryPreferences.memoryPrefs(), Gson()), handler: (Request) -> String): UserListsRepository {
        val gson = Gson()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body(handler(chain.request()).toResponseBody()).build()
        }.build()
        return UserListsRepository(
            MemoryPreferences.memoryPrefs(),
            UserListsClient(client, gson, "https://example.test/api") { "token" },
            profiles,
        )
    }
}
