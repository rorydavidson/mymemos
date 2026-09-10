package com.keltruc.mymemos.data.auth

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveSessionTest {
    private val server = "https://memos.example.com/"

    @Test
    fun `token goes to the server itself`() {
        assertTrue(ActiveSession.sameOrigin("https://memos.example.com/file/attachments/x.png".toHttpUrl(), server))
        assertTrue(ActiveSession.sameOrigin("https://MEMOS.example.com:443/avatar".toHttpUrl(), server))
    }

    @Test
    fun `token never goes to look-alike or other hosts`() {
        assertFalse(ActiveSession.sameOrigin("https://memos.example.com.evil.net/x.png".toHttpUrl(), server))
        assertFalse(ActiveSession.sameOrigin("https://memos.example.com@evil.net/x.png".toHttpUrl(), server))
        assertFalse(ActiveSession.sameOrigin("http://memos.example.com/x.png".toHttpUrl(), server))
        assertFalse(ActiveSession.sameOrigin("https://memos.example.com:8443/x.png".toHttpUrl(), server))
        assertFalse(ActiveSession.sameOrigin("https://evil.net/x.png".toHttpUrl(), "not a url"))
    }
}
