package com.keltruc.mymemos.data.account

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AvatarSourceTest {

    private val server = "https://memos.example"

    @Test
    fun `no avatar means nothing to show`() {
        assertEquals(AvatarSource.None, AvatarSource.of("", server))
        assertEquals(AvatarSource.None, AvatarSource.of("   ", server))
    }

    @Test
    fun `a data uri is decoded in place`() {
        val source = AvatarSource.of("data:image/png;base64,aGVsbG8=", server)
        assertEquals("hello", (source as AvatarSource.Bytes).bytes.decodeToString())
    }

    @Test
    fun `a broken data uri falls back rather than showing nothing at all`() {
        assertEquals(AvatarSource.None, AvatarSource.of("data:image/png;base64,!!!not base64!!!", server))
        assertEquals(AvatarSource.None, AvatarSource.of("data:image/png;base64,", server))
    }

    @Test
    fun `a path is resolved against the server and counts as its own`() {
        val source = AvatarSource.of("/file/users/rory/avatar", server) as AvatarSource.Url
        assertEquals("https://memos.example/file/users/rory/avatar", source.url)
        assertTrue(source.sameOrigin)
    }

    @Test
    fun `an absolute url on the same server still counts as its own`() {
        assertTrue((AvatarSource.of("https://memos.example/file/a", server) as AvatarSource.Url).sameOrigin)
    }

    @Test
    fun `an absolute url somewhere else does not get the credential`() {
        assertTrue(!(AvatarSource.of("https://elsewhere.example/a.png", server) as AvatarSource.Url).sameOrigin)
    }

    @Test
    fun `a look-alike host does not get the credential either`() {
        // The reason origins are compared whole: this starts with the server's own URL.
        assertTrue(!(AvatarSource.of("https://memos.example.evil.net/a.png", server) as AvatarSource.Url).sameOrigin)
    }

    @Test
    fun `a different scheme or port is a different origin`() {
        assertTrue(!(AvatarSource.of("http://memos.example/a", server) as AvatarSource.Url).sameOrigin)
        assertTrue(!(AvatarSource.of("https://memos.example:8443/a", server) as AvatarSource.Url).sameOrigin)
    }

    @Test
    fun `the default port is implied so naming it changes nothing`() {
        assertTrue((AvatarSource.of("https://memos.example:443/a", server) as AvatarSource.Url).sameOrigin)
    }
}
