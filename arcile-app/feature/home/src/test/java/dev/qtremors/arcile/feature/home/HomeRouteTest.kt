package dev.qtremors.arcile.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeRouteTest {
    @Test
    fun `restricted primary storage uri resolves to a local path`() {
        assertEquals(
            "/storage/emulated/0/Android/data",
            homeRestrictedLocalPath(
                "content://com.android.externalstorage.documents/tree/primary%3A/" +
                    "document/primary%3AAndroid%2Fdata"
            )
        )
    }

    @Test
    fun `restricted folder resolver rejects other providers and volumes`() {
        assertNull(
            homeRestrictedLocalPath(
                "content://downloads/document/primary%3AAndroid%2Fdata"
            )
        )
        assertNull(
            homeRestrictedLocalPath(
                "content://com.android.externalstorage.documents/document/" +
                    "1234-5678%3AAndroid%2Fdata"
            )
        )
    }
}
