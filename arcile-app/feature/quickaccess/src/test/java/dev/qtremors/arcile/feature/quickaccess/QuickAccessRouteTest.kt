package dev.qtremors.arcile.feature.quickaccess

import android.net.Uri
import android.provider.DocumentsContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QuickAccessRouteTest {

    @Test
    fun `restricted folder uri targets primary external storage`() {
        val uri = restrictedExternalStorageUri("Android/data")

        assertEquals(
            "com.android.externalstorage.documents",
            uri.authority
        )
        assertEquals(
            "primary:Android/data",
            DocumentsContract.getDocumentId(uri)
        )
    }

    @Test
    fun `restricted folder uri normalizes separators and whitespace`() {
        val uri = restrictedExternalStorageUri("  /Android\\obb/ ")

        assertEquals("primary:Android/obb", DocumentsContract.getDocumentId(uri))
    }

    @Test
    fun `restricted folder uri supports files app root`() {
        val uri = restrictedExternalStorageUri("")

        assertEquals("primary:", DocumentsContract.getDocumentId(uri))
    }

    @Test
    fun `restricted folder uri rejects traversal`() {
        assertThrows(IllegalArgumentException::class.java) {
            restrictedExternalStorageUri("Android/../Download")
        }
        assertThrows(IllegalArgumentException::class.java) {
            restrictedExternalStorageUri("./Android/data")
        }
    }

    @Test
    fun `folder label uses final document path segment`() {
        val tree = DocumentsContract.buildTreeDocumentUri(
            "com.android.externalstorage.documents",
            "primary:Projects/Arcile"
        )

        assertEquals("Arcile", folderLabel(tree))
    }

    @Test
    fun `folder label falls back for root and malformed uri`() {
        val root = DocumentsContract.buildTreeDocumentUri(
            "com.android.externalstorage.documents",
            "primary:"
        )

        assertEquals("New Folder", folderLabel(root))
        assertEquals("New Folder", folderLabel(Uri.parse("content://example/not-a-tree")))
    }
}
