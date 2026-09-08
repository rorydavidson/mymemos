package com.keltruc.mymemos.network

import com.keltruc.mymemos.network.auth.TokenStore
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RefreshAuthenticatorTest {

    private val server = MockWebServer()

    private class FakeTokenStore(var token: String?, val pat: Boolean = false) : TokenStore {
        var cookieBlob: String? = null
        override suspend fun accessToken() = token
        override suspend fun updateAccessToken(token: String, expiresAt: String?) { this.token = token }
        override suspend fun isPersonalAccessToken() = pat
        override suspend fun cookies() = cookieBlob
        override suspend fun saveCookies(serialised: String) { cookieBlob = serialised }
    }

    @Before fun setUp() = server.start()
    @After fun tearDown() = server.shutdown()

    @Test
    fun `expired token is refreshed once and the request retried`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"code":16,"message":"unauthenticated"}"""))
        server.enqueue(MockResponse().setBody("""{"accessToken":"fresh","expiresAt":"2026-09-09T00:00:00Z"}"""))
        server.enqueue(MockResponse().setBody("""{"memos":[{"name":"memos/a","content":"hi"}],"nextPageToken":""}"""))

        val store = FakeTokenStore("stale")
        val api = MemosApiFactory().create(server.url("/").toString(), store).api
        val page = api.listMemos()

        assertEquals(1, page.memos.size)
        assertEquals("fresh", store.token)
        assertEquals(3, server.requestCount)
        assertEquals("Bearer stale", server.takeRequest().getHeader("Authorization"))
        val refresh = server.takeRequest()
        assertEquals("/api/v1/auth/refresh", refresh.path)
        assertEquals("Bearer fresh", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `personal access tokens are never refreshed`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"code":16,"message":"unauthenticated"}"""))

        val store = FakeTokenStore("pat-token", pat = true)
        val api = MemosApiFactory().create(server.url("/").toString(), store).api
        val failed = runCatching { api.listMemos() }

        assertTrue(failed.isFailure)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `refresh cookie set by sign-in is persisted and sent on refresh`() = runBlocking {
        server.enqueue(
            MockResponse()
                .addHeader("Set-Cookie", "memos.refresh-token=r1; Path=/; HttpOnly; Max-Age=86400")
                .setBody("""{"user":{"name":"users/rory","username":"rory"},"accessToken":"a1"}"""),
        )
        server.enqueue(MockResponse().setResponseCode(401).setBody("{}"))
        server.enqueue(MockResponse().setBody("""{"accessToken":"a2"}"""))
        server.enqueue(MockResponse().setBody("""{"memos":[]}"""))

        val store = FakeTokenStore(null)
        val api = MemosApiFactory().create(server.url("/").toString(), store).api
        val signIn = api.signIn(com.keltruc.mymemos.network.dto.SignInRequestDto(
            com.keltruc.mymemos.network.dto.PasswordCredentialsDto("rory", "pw"),
        ))
        store.token = signIn.accessToken
        assertTrue(store.cookieBlob!!.contains("memos.refresh-token=r1"))

        api.listMemos()
        server.takeRequest(); server.takeRequest()
        val refresh = server.takeRequest()
        assertTrue(refresh.getHeader("Cookie")!!.contains("memos.refresh-token=r1"))
    }

    @Test
    fun `resource names with slashes are not url-encoded in paths`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"name":"memos/abc","content":"x"}"""))
        val api = MemosApiFactory().create(server.url("/").toString(), FakeTokenStore("t")).api
        api.getMemo("memos/abc")
        assertEquals("/api/v1/memos/abc", server.takeRequest().path)
    }
}
