package dev.qtremors.arcile.presentation.ui

import androidx.compose.runtime.saveable.SaverScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ApkInstallTargetSaverTest {
    private val scope = SaverScope { true }

    @Test
    fun `installer target restores all source fields after recreation`() {
        val target = AppFileOpenResolution.InstallApk(
            path = "/downloads/package.apks",
            splitPaths = listOf("/downloads/base.apk", "/downloads/split.apk"),
            contentUri = "content://downloads/one",
            displayName = "Package"
        )
        val saved = with(ApkInstallTargetSaver) { scope.save(target) }!!
        assertEquals(target, ApkInstallTargetSaver.restore(saved))
    }

    @Test
    fun `dismissed target remains absent after recreation`() {
        val saved = with(ApkInstallTargetSaver) { scope.save(null) }!!
        assertNull(ApkInstallTargetSaver.restore(saved))
        assertEquals(
            AppFileOpenResolution.InstallApk("/downloads/base.apk"),
            ApkInstallTargetSaver.restore(listOf("/downloads/base.apk", "", ""))
        )
    }
}
