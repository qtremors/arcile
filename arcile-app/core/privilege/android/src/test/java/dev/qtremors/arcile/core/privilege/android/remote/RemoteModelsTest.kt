package dev.qtremors.arcile.core.privilege.android.remote

import android.os.Parcel
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RemoteModelsTest {
    @Test
    fun `handshake survives a parcel round trip`() {
        val source = RemoteHandshake(
            protocolVersion = 3,
            effectiveUid = 2000,
            pid = 42,
            transport = "SHIZUKU_USER_SERVICE",
            selinuxContext = "u:r:shell:s0",
            capabilities = listOf("READ", "LIST_DIRECTORY"),
            maximumDirectoryPageSize = 250
        )

        assertEquals(source, roundTrip(source, RemoteHandshake.CREATOR))
    }

    @Test
    fun `directory page preserves entries and opaque next token`() {
        val entry = RemoteFileEntry(
            path = "/data/local/tmp/file",
            canonicalIdentity = "/data/local/tmp/file",
            displayName = "file",
            type = "REGULAR_FILE",
            size = 123,
            modifiedAtMillis = 456,
            mode = 0x81A4,
            readable = true,
            writable = false
        )
        val source = RemoteDirectoryPage(listOf(entry), "opaque-page-2")

        assertEquals(source, roundTrip(source, RemoteDirectoryPage.CREATOR))
    }

    private fun <T : android.os.Parcelable> roundTrip(
        source: T,
        creator: android.os.Parcelable.Creator<T>
    ): T {
        val parcel = Parcel.obtain()
        return try {
            source.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            creator.createFromParcel(parcel)
        } finally {
            parcel.recycle()
        }
    }
}
