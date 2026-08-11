package dev.qtremors.arcile

import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.arcile.feature.importing.SaveToArcileActivity
import dev.qtremors.arcile.feature.audio.AudioPlayerActivity
import dev.qtremors.arcile.feature.audio.canResolveStandaloneAudio
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.StorageNodeCapabilities
import dev.qtremors.arcile.core.storage.domain.StorageNodeRef
import dev.qtremors.arcile.core.storage.data.PrivilegedContentProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppActivityManifestTest {

    @Test
    fun `protected content provider is private and grants individual read capabilities`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val provider = context.packageManager.getProviderInfo(
            ComponentName(context, PrivilegedContentProvider::class.java),
            0
        )

        assertEquals(false, provider.exported)
        assertEquals(true, provider.grantUriPermissions)
        assertEquals("${context.packageName}.privileged.content", provider.authority)
    }

    @Test
    fun `manifest exposes send and send multiple share target`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val packageManager = context.packageManager
        val single = packageManager.queryIntentActivities(
            Intent(Intent.ACTION_SEND).setType("image/png"),
            0
        )
        val multiple = packageManager.queryIntentActivities(
            Intent(Intent.ACTION_SEND_MULTIPLE).setType("*/*"),
            0
        )

        assertTrue(single.any { it.activityInfo.name == SaveToArcileActivity::class.java.name })
        assertTrue(multiple.any { it.activityInfo.name == SaveToArcileActivity::class.java.name })
    }

    @Test
    fun `standalone image viewer resolves valid image view intent`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val uri = Uri.parse("content://example/photo")
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "image/png")
        }

        val target = resolveStandaloneImageTarget(context, intent)

        assertEquals(uri.toString(), target?.reference)
        assertEquals("image/png", target?.mimeType)
    }

    @Test
    fun `standalone image viewer rejects missing uri and unsupported mime`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertEquals(null, resolveStandaloneImageTarget(context, Intent(Intent.ACTION_VIEW).setType("image/png")))
        assertEquals(
            null,
            resolveStandaloneImageTarget(
                context,
                Intent(Intent.ACTION_VIEW).setDataAndType(
                    Uri.parse("content://example/file.txt"),
                    "text/plain"
                )
            )
        )
    }

    @Test
    fun `standalone video viewer resolves valid video view intent`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val uri = Uri.parse("content://example/video")
        val target = resolveStandaloneVideoTarget(
            context,
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "video/mp4")
        )

        assertEquals(uri, target?.uri)
        assertEquals("video/mp4", target?.mimeType)
    }

    @Test
    fun `manifest exposes standalone image viewer in separate process`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val matches = context.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_VIEW).setDataAndType(
                Uri.parse("content://example/photo"),
                "image/jpeg"
            ),
            0
        )

        val activity = matches.first {
            it.activityInfo.name == ImageViewerActivity::class.java.name
        }.activityInfo
        assertEquals("${context.packageName}:imageviewer", activity.processName)
    }

    @Test
    fun `standalone audio player resolves audio in the app process`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(
            Uri.parse("content://example/song"),
            "audio/mpeg"
        )

        assertTrue(canResolveStandaloneAudio(context, intent))
        assertEquals(
            AudioPlayerActivity::class.java.name,
            resolveStandaloneViewerActivityName(context, intent)
        )

        val activity = context.packageManager.queryIntentActivities(intent, 0)
            .first { it.activityInfo.name == AudioPlayerActivity::class.java.name }
            .activityInfo
        assertEquals(context.packageName, activity.processName)
    }

    @Test
    fun `standalone pdf viewer resolves valid pdf intent`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val uri = Uri.parse("content://example/report")
        val target = resolveStandalonePdfTarget(
            context,
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/pdf")
        )

        assertEquals(uri.toString(), target?.reference)
        assertEquals("report", target?.displayName)
    }

    @Test
    fun `standalone pdf viewer rejects non pdf intent`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertEquals(
            null,
            resolveStandalonePdfTarget(
                context,
                Intent(Intent.ACTION_VIEW).setDataAndType(
                    Uri.parse("content://example/notes.txt"),
                    "text/plain"
                )
            )
        )
    }

    @Test
    fun `manifest exposes standalone pdf viewer in separate process`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val matches = context.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_VIEW).setDataAndType(
                Uri.parse("content://example/report"),
                "application/pdf"
            ),
            0
        )

        val activity = matches.first {
            it.activityInfo.name == PdfViewerActivity::class.java.name
        }.activityInfo
        assertEquals("${context.packageName}:pdfviewer", activity.processName)
    }

    @Test
    fun `manifest exposes generic file opener for binary fallback`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val matches = context.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_VIEW).setDataAndType(
                Uri.parse("content://example/file.bin"),
                "application/octet-stream"
            ),
            0
        )

        assertTrue(matches.any { it.activityInfo.name == FileOpenActivity::class.java.name })
    }

    @Test
    fun `standalone text editor resolves valid text and markdown view and edit intent`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val uri = Uri.parse("content://example/notes.md")
        val viewIntent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "text/markdown")
        val editIntent = Intent(Intent.ACTION_EDIT).setDataAndType(uri, "text/plain")

        val targetView = resolveStandaloneTextTarget(context, viewIntent)
        val targetEdit = resolveStandaloneTextTarget(context, editIntent)

        assertEquals(uri.toString(), targetView?.reference)
        assertEquals(uri.toString(), targetEdit?.reference)
        assertEquals(false, targetView?.writable)
        assertEquals(false, targetEdit?.writable)
        assertEquals(true, targetView?.isMarkdown)
    }

    @Test
    fun `standalone text editor requires edit action and write grant for content uri`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val uri = Uri.parse("content://example/notes.md")
        val target = resolveStandaloneTextTarget(
            context,
            Intent(Intent.ACTION_EDIT)
                .setDataAndType(uri, "text/markdown")
                .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        )

        assertEquals(true, target?.writable)
    }

    @Test
    fun `generic markdown intent is forwarded to the standalone text editor`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val target = resolveStandaloneViewerActivityName(
            context,
            Intent(Intent.ACTION_VIEW).setDataAndType(
                Uri.parse("content://example/readme.md"),
                "application/octet-stream"
            )
        )

        assertEquals(TextEditorActivity::class.java.name, target)
    }

    @Test
    fun `manifest exposes standalone text editor in separate process`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val matches = context.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_VIEW).setDataAndType(
                Uri.parse("content://example/readme.txt"),
                "text/plain"
            ),
            0
        )

        val activity = matches.first {
            it.activityInfo.name == TextEditorActivity::class.java.name
        }.activityInfo
        assertEquals("${context.packageName}:texteditor", activity.processName)

        val editMatches = context.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_EDIT).setDataAndType(
                Uri.parse("content://example/readme.md"),
                "text/markdown"
            ),
            0
        )
        assertTrue(editMatches.any { it.activityInfo.name == TextEditorActivity::class.java.name })
    }

    @Test
    fun `protected text editor stays in app process and restores backend identity`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = protectedFile(
            path = "/data/user/0/example/files/notes.md",
            name = "notes.md",
            mimeType = "text/markdown",
            contentUri = "content://${context.packageName}.privileged.content/${"1".repeat(64)}",
            canWrite = true
        )

        val intent = createProtectedTextEditorIntent(context, file)
        val target = resolveStandaloneTextTarget(context, intent)
        val activity = context.packageManager.getActivityInfo(intent.component!!, 0)

        assertEquals(ProtectedTextEditorActivity::class.java.name, intent.component?.className)
        assertEquals(context.packageName, activity.processName)
        assertEquals(file.nodeRef.canonicalIdentity, target?.nodeRef?.canonicalIdentity)
        assertEquals(file.nodeRef.backendId, target?.nodeRef?.backendId)
        assertEquals(file.nodeRef.contentUri, target?.nodeRef?.contentUri)
        assertEquals(true, target?.writable)
        assertEquals(0, intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    @Test
    fun `protected read-only text target never becomes writable`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = protectedFile(
            path = "/system/etc/readme.txt",
            name = "readme.txt",
            mimeType = "text/plain",
            contentUri = "content://${context.packageName}.privileged.content/${"2".repeat(64)}",
            canWrite = false
        )

        val target = resolveStandaloneTextTarget(
            context,
            createProtectedTextEditorIntent(context, file)
        )

        assertEquals(false, target?.writable)
        assertEquals(StorageNodeRef.ROOT_BACKEND_ID, target?.nodeRef?.backendId)
    }

    @Test
    fun `protected PDF viewer stays in app process and retains capability`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = protectedFile(
            path = "/data/user/0/example/files/report.pdf",
            name = "report.pdf",
            mimeType = "application/pdf",
            contentUri = "content://${context.packageName}.privileged.content/${"3".repeat(64)}",
            canWrite = false
        )

        val intent = createProtectedPdfViewerIntent(context, file)
        val activity = context.packageManager.getActivityInfo(intent.component!!, 0)
        val target = resolveStandalonePdfTarget(context, intent)

        assertEquals(ProtectedPdfViewerActivity::class.java.name, intent.component?.className)
        assertEquals(context.packageName, activity.processName)
        assertEquals(file.nodeRef.canonicalIdentity, target?.nodeRef?.canonicalIdentity)
        assertEquals(file.nodeRef.contentUri, target?.reference)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    private fun protectedFile(
        path: String,
        name: String,
        mimeType: String,
        contentUri: String,
        canWrite: Boolean
    ): FileModel {
        val node = StorageNodeRef.root(
            displayPath = path,
            remoteCanonicalIdentity = path,
            capabilities = StorageNodeCapabilities(canRead = true, canWrite = canWrite)
        ).copy(contentUri = contentUri)
        return FileModel(
            name = name,
            absolutePath = path,
            size = 100,
            lastModified = 500,
            extension = name.substringAfterLast('.', ""),
            mimeType = mimeType,
            nodeRef = node
        )
    }
}
