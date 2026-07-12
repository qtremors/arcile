package dev.qtremors.arcile.core.ui.image

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import coil.fetch.DrawableResult
import coil.request.Options
import coil.size.Size
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

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
        assertNotNull(ApkIconFetcher.KeyFactory().create(ThumbnailKey.from(java.io.File("sample.apk")), options, mockk(relaxed = true)))
    }

    @Test
    fun `fetch returns drawable result when package archive icon is available`() = runTest {
        val expectedDrawable = ColorDrawable(Color.RED)
        val apk = File.createTempFile("sample", ".apk").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val packageManager = mockk<PackageManager>()
        val appInfo = spyk(ApplicationInfo())
        every { appInfo.loadIcon(packageManager) } returns expectedDrawable
        val packageInfo = PackageInfo().apply {
            applicationInfo = appInfo
        }
        every { packageManager.getPackageArchiveInfo(apk.absolutePath, 0) } returns packageInfo

        val context = mockk<Context> {
            every { this@mockk.packageManager } returns packageManager
        }
        val options = mockk<Options> {
            every { this@mockk.context } returns context
            every { size } returns Size(96, 96)
        }

        val result = ApkIconFetcher(apk, options).fetch() as DrawableResult

        assertNotNull(result.drawable)
        assertEquals(apk.absolutePath, appInfo.sourceDir)
        assertEquals(apk.absolutePath, appInfo.publicSourceDir)
    }
}
