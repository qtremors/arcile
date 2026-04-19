package dev.qtremors.arcile.image

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import coil.fetch.DrawableResult
import coil.request.Options
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ApkIconFetcherTest {

    @Test
    fun `factory only creates fetcher for apk-like extensions`() {
        val factory = ApkIconFetcher.Factory()
        val options = mockk<Options> {
            every { context } returns mockk<Context>(relaxed = true)
        }

        assertNotNull(factory.create(java.io.File("sample.apk"), options, mockk(relaxed = true)))
        assertNotNull(factory.create(java.io.File("bundle.xapk"), options, mockk(relaxed = true)))
        assertNull(factory.create(java.io.File("notes.txt"), options, mockk(relaxed = true)))
    }

    @Test
    fun `fetch returns drawable result when package archive icon is available`() = runTest {
        val expectedDrawable = ColorDrawable(Color.RED)
        val packageManager = mockk<PackageManager>()
        val appInfo = spyk(ApplicationInfo())
        every { appInfo.loadIcon(packageManager) } returns expectedDrawable
        val packageInfo = PackageInfo().apply {
            applicationInfo = appInfo
        }
        every { packageManager.getPackageArchiveInfo("C:\\apps\\sample.apk", 0) } returns packageInfo

        val context = mockk<Context> {
            every { this@mockk.packageManager } returns packageManager
        }
        val options = mockk<Options> {
            every { this@mockk.context } returns context
        }

        val result = ApkIconFetcher(java.io.File("C:\\apps\\sample.apk"), options).fetch() as DrawableResult

        assertEquals(expectedDrawable, result.drawable)
        assertEquals("C:\\apps\\sample.apk", appInfo.sourceDir)
        assertEquals("C:\\apps\\sample.apk", appInfo.publicSourceDir)
    }
}
