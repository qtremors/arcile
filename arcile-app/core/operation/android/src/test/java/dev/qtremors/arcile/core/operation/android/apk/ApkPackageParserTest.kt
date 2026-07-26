package dev.qtremors.arcile.core.operation.android.apk

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class ApkPackageParserTest {

    @Test
    fun `generic bundle selects only device compatible splits`() {
        val entries = entries(
            "splits/base-master.apk",
            "splits/base-arm64_v8a.apk",
            "splits/base-armeabi_v7a.apk",
            "splits/base-xhdpi.apk",
            "splits/base-xxhdpi.apk",
            "splits/base-en.apk",
            "splits/base-fr.apk",
            "standalones/standalone-arm64_v8a.apk"
        )

        val selected = ApkPackageParser.selectCompatibleArchiveApks(
            extractedApks = entries,
            target = ApkPackageParser.ApkArchiveDeviceTarget(
                supportedAbis = listOf("arm64-v8a", "armeabi-v7a"),
                densityDpi = 420,
                language = "en",
                region = "US"
            )
        )

        assertEquals(
            listOf(
                "splits/base-master.apk",
                "splits/base-arm64_v8a.apk",
                "splits/base-xxhdpi.apk",
                "splits/base-en.apk"
            ),
            selected.map(ApkPackageParser.ExtractedApk::archivePath)
        )
    }

    @Test
    fun `standalone bundle chooses preferred supported ABI`() {
        val entries = entries(
            "standalones/standalone-x86.apk",
            "standalones/standalone-armeabi_v7a.apk",
            "standalones/standalone-arm64_v8a.apk"
        )

        val selected = ApkPackageParser.selectCompatibleArchiveApks(
            extractedApks = entries,
            target = ApkPackageParser.ApkArchiveDeviceTarget(
                supportedAbis = listOf("arm64-v8a", "armeabi-v7a"),
                densityDpi = 420,
                language = "en",
                region = "US"
            )
        )

        assertEquals(
            listOf("standalones/standalone-arm64_v8a.apk"),
            selected.map(ApkPackageParser.ExtractedApk::archivePath)
        )
    }

    private fun entries(vararg archivePaths: String): List<ApkPackageParser.ExtractedApk> =
        archivePaths.mapIndexed { index, archivePath ->
            ApkPackageParser.ExtractedApk(
                file = File("${index.toString().padStart(4, '0')}_${archivePath.substringAfterLast('/')}"),
                archivePath = archivePath
            )
        }
}
