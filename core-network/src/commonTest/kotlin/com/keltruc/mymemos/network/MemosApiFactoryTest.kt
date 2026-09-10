package com.keltruc.mymemos.network

import com.keltruc.mymemos.network.auth.TokenStore
import com.keltruc.mymemos.network.dto.PasswordCredentialsDto
import com.keltruc.mymemos.network.dto.SignInRequestDto
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.plugins.ResponseException
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The behaviour that used to live in RefreshAuthenticatorTest against MockWebServer. The
 * repositories still own the translation from a failed response to [ApiException], exactly
 * as they did with Retrofit's HttpException, so the tests below check both halves: what the
 * API throws, and what [ApiException.from] makes of it.
 */
class MemosApiFactoryTest {

    private class FakeTokenStore(var token: String?, val pat: Boolean = false) : TokenStore {
        var cookieBlob: String? = null
        var updates = 0
        override suspend fun accessToken() = token
        override suspend fun updateAccessToken(token: String, expiresAt: String?) {
            this.token = token
            updates++
        }
        override suspend fun isPersonalAccessToken() = pat
        override suspend fun cookies() = cookieBlob
        override suspend fun saveCookies(serialised: String) { cookieBlob = serialised }
    }

    /** Answers each call with the next scripted response and records what was asked. */
    private class Script(vararg responses: Pair<HttpStatusCode, String>) {
        private val queued = responses.toMutableList()
        val requests = mutableListOf<HttpRequestData>()
        var setCookieOnFirst: String? = null

        val engine = MockEngine { request ->
            requests += request
            val (status, body) = queued.removeFirstOrNull() ?: (HttpStatusCode.NotFound to "{}")
            val headers = if (requests.size == 1 && setCookieOnFirst != null) {
                headersOf(
                    HttpHeaders.ContentType to listOf("application/json"),
                    HttpHeaders.SetCookie to listOf(setCookieOnFirst!!),
                )
            } else {
                headersOf(HttpHeaders.ContentType, "application/json")
            }
            respond(body, status, headers)
        }
    }

    private fun ok(body: String) = HttpStatusCode.OK to body
    private fun unauthorised(body: String = """{"code":16,"message":"unauthenticated"}""") =
        HttpStatusCode.Unauthorized to body

    @Test
    fun expiredTokenIsRefreshedOnceAndTheRequestRetried() = runTest {
        val script = Script(
            unauthorised(),
            ok("""{"accessToken":"fresh","expiresAt":"2026-09-09T00:00:00Z"}"""),
            ok("""{"memos":[{"name":"memos/a","content":"hi"}],"nextPageToken":""}"""),
        )
        val store = FakeTokenStore("stale")
        val api = MemosApiFactory(script.engine).create("https://memos.example", store).api

        val page = api.listMemos()

        assertEquals(1, page.memos.size)
        assertEquals("fresh", store.token)
        assertEquals(1, store.updates, "the token should be written back exactly once")
        assertEquals(3, script.requests.size)
        assertEquals("Bearer stale", script.requests[0].headers[HttpHeaders.Authorization])
        assertEquals("/api/v1/auth/refresh", script.requests[1].url.encodedPath)
        assertEquals("Bearer fresh", script.requests[2].headers[HttpHeaders.Authorization])
    }

    @Test
    fun personalAccessTokensAreNeverRefreshed() = runTest {
        val script = Script(unauthorised())
        val store = FakeTokenStore("pat-token", pat = true)
        val api = MemosApiFactory(script.engine).create("https://memos.example", store).api

        val raw = assertFailsWith<ResponseException> { api.listMemos() }
        val failure = ApiException.from(raw, MemosApiFactory().json)

        assertTrue(failure.isUnauthenticated)
        assertEquals(1, script.requests.size, "no refresh should have been attempted")
    }

    @Test
    fun refreshCookieSetBySignInIsPersistedAndSentOnRefresh() = runTest {
        val script = Script(
            ok("""{"user":{"name":"users/rory","username":"rory"},"accessToken":"a1"}"""),
            unauthorised("{}"),
            ok("""{"accessToken":"a2"}"""),
            ok("""{"memos":[]}"""),
        )
        script.setCookieOnFirst = "memos.refresh-token=r1; Path=/; HttpOnly; Max-Age=86400"

        val store = FakeTokenStore(null)
        val api = MemosApiFactory(script.engine).create("https://memos.example", store).api

        val signIn = api.signIn(SignInRequestDto(PasswordCredentialsDto("rory", "pw")))
        store.token = signIn.accessToken
        assertTrue(store.cookieBlob!!.contains("memos.refresh-token=r1"), "cookie was not persisted")

        api.listMemos()

        val refresh = script.requests[2]
        assertEquals("/api/v1/auth/refresh", refresh.url.encodedPath)
        assertTrue(
            refresh.headers[HttpHeaders.Cookie].orEmpty().contains("memos.refresh-token=r1"),
            "the refresh call did not carry the cookie: ${refresh.headers[HttpHeaders.Cookie]}",
        )
        assertEquals("a2", store.token)
    }

    @Test
    fun resourceNamesWithSlashesAreNotUrlEncodedInPaths() = runTest {
        val script = Script(ok("""{"name":"memos/abc","content":"x"}"""))
        val api = MemosApiFactory(script.engine).create("https://memos.example", FakeTokenStore("t")).api

        api.getMemo("memos/abc")

        assertEquals("/api/v1/memos/abc", script.requests.single().url.encodedPath)
    }

    @Test
    fun theServersErrorBodyBecomesAnApiException() = runTest {
        val script = Script(HttpStatusCode.NotFound to """{"code":5,"message":"memo not found"}""")
        val api = MemosApiFactory(script.engine).create("https://memos.example", FakeTokenStore("t")).api

        val raw = assertFailsWith<ResponseException> { api.getMemo("memos/gone") }
        val failure = ApiException.from(raw, MemosApiFactory().json)

        assertEquals(404, failure.httpStatus)
        assertEquals(5, failure.grpcCode)
        assertEquals("memo not found", failure.message)
        assertTrue(failure.isNotFound)
    }

    @Test
    fun anErrorBodyThatIsNotJsonStillGivesAUsableException() = runTest {
        val script = Script(HttpStatusCode.BadGateway to "<html>nginx</html>")
        val api = MemosApiFactory(script.engine).create("https://memos.example", FakeTokenStore("t")).api

        val raw = assertFailsWith<ResponseException> { api.getInstanceProfile() }
        val failure = ApiException.from(raw, MemosApiFactory().json)

        assertEquals(502, failure.httpStatus)
        assertEquals(0, failure.grpcCode)
        assertTrue(failure.message!!.isNotEmpty())
    }

    @Test
    fun baseUrlsAreNormalisedTheSameWayAsBefore() {
        assertEquals("https://memos.example/", MemosApiFactory.normaliseBaseUrl("memos.example"))
        assertEquals("https://memos.example/", MemosApiFactory.normaliseBaseUrl("https://memos.example/"))
        assertEquals("http://192.168.1.2:5230/", MemosApiFactory.normaliseBaseUrl(" http://192.168.1.2:5230 "))
    }
}
