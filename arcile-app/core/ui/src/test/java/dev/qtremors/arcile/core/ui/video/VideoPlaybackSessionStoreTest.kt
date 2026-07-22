package dev.qtremors.arcile.core.ui.video

import androidx.media3.common.MediaItem
import dev.qtremors.arcile.core.storage.domain.FileModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class VideoPlaybackSessionStoreTest {
    @Test
    fun `registered session is resolved only by opaque token and can be removed`() {
        val store = VideoPlaybackSessionStore(tokenFactory = { "opaque" })
        val session = session("private-file-name.mp4")

        val token = store.register(session)

        assertEquals("opaque", token)
        assertSame(session, store.resolve(token))
        assertNull(store.resolve("private-file-name.mp4"))
        store.remove(token)
        assertNull(store.resolve(token))
    }

    @Test
    fun `expired sessions are rejected`() {
        var now = 10L
        val store = VideoPlaybackSessionStore(
            clockMillis = { now },
            tokenFactory = { "token" },
            lifetimeMillis = 5L
        )
        store.register(session("video"))

        now = 14L
        assertEquals("video", store.resolve("token")?.items?.single()?.title)
        now = 15L
        assertNull(store.resolve("token"))
    }

    @Test
    fun `oldest session is evicted when capacity is reached`() {
        var token = 0
        val store = VideoPlaybackSessionStore(tokenFactory = { (++token).toString() }, maxSessions = 2)

        store.register(session("one"))
        store.register(session("two"))
        store.register(session("three"))

        assertNull(store.resolve("1"))
        assertEquals("two", store.resolve("2")?.items?.single()?.title)
        assertEquals("three", store.resolve("3")?.items?.single()?.title)
    }

    @Test
    fun `session rejects empty queue and invalid index`() {
        assertThrows(IllegalArgumentException::class.java) { VideoPlaybackSession(emptyList()) }
        assertThrows(IllegalArgumentException::class.java) {
            VideoPlaybackSession(listOf(VideoPlaybackItem(MediaItem.EMPTY, "video")), startIndex = 1)
        }
    }

    @Test
    fun `session allows a larger pager context than its eager playback queue`() {
        val item = VideoPlaybackItem(MediaItem.EMPTY, "selected")
        val context = listOf(
            FileModel(name = "selected.mp4", absolutePath = "/videos/selected.mp4"),
            FileModel(name = "sibling.mp4", absolutePath = "/videos/sibling.mp4")
        )

        val session = VideoPlaybackSession(items = listOf(item), files = context)

        assertEquals(1, session.items.size)
        assertEquals(2, session.files?.size)
    }

    @Test
    fun `session retains gallery selection and managed trash context`() {
        val session = VideoPlaybackSession(
            items = listOf(VideoPlaybackItem(MediaItem.EMPTY, "selected")),
            initialSelectedPaths = setOf("/videos/selected.mp4"),
            managedTrash = true
        )

        assertEquals(setOf("/videos/selected.mp4"), session.initialSelectedPaths)
        assertEquals(true, session.managedTrash)
    }

    @Test
    fun `security scope removal closes only matching vault routes`() {
        var token = 0
        val store = VideoPlaybackSessionStore(tokenFactory = { (++token).toString() })
        val vaultA = session("a").copy(securityScopeId = "vault:a")
        val vaultB = session("b").copy(securityScopeId = "vault:b")
        val local = session("local")
        store.register(vaultA)
        store.register(vaultB)
        store.register(local)

        store.removeSecurityScope("vault:a")

        assertNull(store.resolve("1"))
        assertSame(vaultB, store.resolve("2"))
        assertSame(local, store.resolve("3"))
    }

    private fun session(title: String) = VideoPlaybackSession(
        listOf(VideoPlaybackItem(MediaItem.EMPTY, title))
    )
}
