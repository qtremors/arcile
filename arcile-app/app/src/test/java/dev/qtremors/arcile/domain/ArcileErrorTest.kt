package dev.qtremors.arcile.domain

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ArcileErrorTest {

    @Test
    fun `maps access denied errors`() {
        assertTrue(SecurityException("denied").toArcileError() is ArcileError.AccessDenied)
        assertTrue(FileOperationException.AccessDenied().toArcileError() is ArcileError.AccessDenied)
    }

    @Test
    fun `maps storage unavailable errors`() {
        assertTrue(IOException("missing drive").toArcileError() is ArcileError.StorageUnavailable)
        assertTrue(FileOperationException.NotFound().toArcileError() is ArcileError.StorageUnavailable)
    }

    @Test
    fun `maps partial success and unsupported provider messages`() {
        assertTrue(Exception("Moved 1 of 2 items to trash. Failed on a.txt").toArcileError() is ArcileError.PartialSuccess)
        assertTrue(Exception("Trash not supported on this storage").toArcileError() is ArcileError.UnsupportedProvider)
    }

    @Test
    fun `maps cancellation and unknown fallback`() {
        assertTrue(kotlinx.coroutines.CancellationException("cancelled").toArcileError() is ArcileError.Cancelled)
        assertTrue(Exception("something strange").toArcileError() is ArcileError.Unknown)
    }
}

