package dev.qtremors.arcile.core.ui

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.ActivityInfo
import android.content.pm.PackageInfo
import android.content.pm.ResolveInfo
import android.content.Intent
import android.os.Looper
import android.provider.MediaStore
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FolderAppIconResolverTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        FolderAppIconResolver.clearCache()
    }

    @Test
    fun `normalize collapses whitespace and lowercase`() {
        assertEquals("whatsapp business", FolderAppIconResolver.normalize("  WhatsApp_Business  "))
        assertEquals("telegram", FolderAppIconResolver.normalize("Telegram-"))
    }

    @Test
    fun `resolves known app folder name when app is installed`() {
        val shadowPm = shadowOf(context.packageManager)
        val packageInfo = PackageInfo().apply {
            packageName = "com.whatsapp"
            applicationInfo = ApplicationInfo().apply {
                packageName = "com.whatsapp"
                name = "WhatsApp"
            }
        }
        shadowPm.installPackage(packageInfo)

        val resolved = FolderAppIconResolver.resolvePackageName(context, "WhatsApp")
        assertEquals("com.whatsapp", resolved)
    }

    @Test
    fun `resolves exact package name folder`() {
        val shadowPm = shadowOf(context.packageManager)
        val packageInfo = PackageInfo().apply {
            packageName = "org.telegram.messenger"
            applicationInfo = ApplicationInfo().apply {
                packageName = "org.telegram.messenger"
                name = "Telegram"
            }
        }
        shadowPm.installPackage(packageInfo)

        val resolved = FolderAppIconResolver.resolvePackageName(context, "org.telegram.messenger")
        assertEquals("org.telegram.messenger", resolved)
    }

    @Test
    fun `returns null for uninstalled app folder`() {
        val resolved = FolderAppIconResolver.resolvePackageName(context, "NonExistentApp")
        assertNull(resolved)
    }

    @Test
    fun `resolves installed app labels case insensitively`() {
        installPackage("com.example.notes", "My Notes")

        assertEquals(
            "com.example.notes",
            FolderAppIconResolver.resolvePackageName(context, "MY_notes")
        )
    }

    @Test
    fun `ambiguous app labels fall back to folder icon`() {
        installPackage("com.example.notes.one", "Notes")
        installPackage("com.example.notes.two", "Notes")

        assertNull(FolderAppIconResolver.resolvePackageName(context, "Notes"))
    }

    @Test
    fun `package change invalidates an earlier missing result`() {
        val packageName = "com.example.newapp"
        assertNull(FolderAppIconResolver.resolvePackageName(context, packageName))

        installPackage(packageName, "New App")
        context.sendBroadcast(
            Intent(Intent.ACTION_PACKAGE_ADDED, "package:$packageName".toUri())
                .setPackage(context.packageName)
        )
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(
            packageName,
            FolderAppIconResolver.resolvePackageName(context, packageName)
        )
    }

    @Test
    fun `same folder name at different paths does not reuse path-specific match`() {
        installPackage("com.example.media", "Media App")

        assertEquals(
            "com.example.media",
            FolderAppIconResolver.resolvePackageName(
                context,
                "Shared",
                "/storage/emulated/0/Android/media/com.example.media/Shared"
            )
        )
        assertNull(
            FolderAppIconResolver.resolvePackageName(
                context,
                "Shared",
                "/storage/emulated/0/Documents/Shared"
            )
        )
    }

    @Test
    fun `dcim prefers the installed camera app`() {
        addIntentHandler(MediaStore.ACTION_IMAGE_CAPTURE, "com.example.camera")

        assertEquals(
            "com.example.camera",
            FolderAppIconResolver.resolvePackageName(
                context,
                "DCIM",
                "/storage/emulated/0/DCIM"
            )
        )
    }

    @Test
    fun `recordings prefers the installed recorder app`() {
        addIntentHandler(MediaStore.Audio.Media.RECORD_SOUND_ACTION, "com.example.recorder")

        assertEquals(
            "com.example.recorder",
            FolderAppIconResolver.resolvePackageName(
                context,
                "Recordings",
                "/storage/emulated/0/Recordings"
            )
        )
    }

    @Test
    fun `top-level arcile folder uses the Arcile app icon`() {
        assertEquals(
            context.packageName,
            FolderAppIconResolver.resolvePackageName(
                context,
                ".arcile",
                "/storage/emulated/0/.arcile"
            )
        )
        assertNull(
            FolderAppIconResolver.resolvePackageName(
                context,
                ".arcile",
                "/storage/emulated/0/Documents/.arcile"
            )
        )
    }

    @Suppress("DEPRECATION")
    private fun addIntentHandler(action: String, packageName: String) {
        shadowOf(context.packageManager).addResolveInfoForIntent(
            Intent(action),
            ResolveInfo().apply {
                activityInfo = ActivityInfo().apply {
                    this.packageName = packageName
                    applicationInfo = ApplicationInfo().apply {
                        this.packageName = packageName
                    }
                }
            }
        )
    }

    private fun installPackage(packageName: String, label: String) {
        shadowOf(context.packageManager).installPackage(
            PackageInfo().apply {
                this.packageName = packageName
                applicationInfo = ApplicationInfo().apply {
                    this.packageName = packageName
                    name = label
                }
            }
        )
    }
}
