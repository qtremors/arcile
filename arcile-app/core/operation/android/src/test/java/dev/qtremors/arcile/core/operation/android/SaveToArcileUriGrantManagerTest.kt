package dev.qtremors.arcile.core.operation.android

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.arcile.core.operation.SaveToArcileImportItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SaveToArcileUriGrantManagerTest {
    private lateinit var context: Context
    private lateinit var manager: SaveToArcileUriGrantManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        ShadowContentResolver.registerProviderInternal(AUTHORITY, TestDocumentsProvider())
        manager = SaveToArcileUriGrantManager(context.contentResolver)
    }

    @Test
    fun `acquired grants are released once after terminal import`() {
        val first = uri("first")
        val second = uri("second")

        val acquired = manager.acquirePersistableReadGrants(listOf(first, second, first))

        assertEquals(setOf(first.toString(), second.toString()), acquired)
        manager.releaseOwnedPersistableReadGrants(
            listOf(
                item(first, ownsGrant = true),
                item(second, ownsGrant = true),
                item(first, ownsGrant = true)
            )
        )
        assertTrue(context.contentResolver.persistedUriPermissions.isEmpty())
    }

    @Test
    fun `preexisting app grant is never claimed or released`() {
        val quickAccessRoot = uri("quick-access-root")
        context.contentResolver.takePersistableUriPermission(quickAccessRoot, READ_GRANT)

        val acquired = manager.acquirePersistableReadGrants(listOf(quickAccessRoot))
        manager.releaseOwnedPersistableReadGrants(listOf(item(quickAccessRoot, ownsGrant = false)))

        assertTrue(acquired.isEmpty())
        assertEquals(
            listOf(quickAccessRoot),
            context.contentResolver.persistedUriPermissions.map { it.uri }
        )
    }

    private fun item(uri: Uri, ownsGrant: Boolean) = SaveToArcileImportItem(
        uri = uri.toString(),
        displayName = uri.lastPathSegment.orEmpty(),
        ownsPersistedReadGrant = ownsGrant
    )

    private fun uri(path: String): Uri = Uri.parse("content://$AUTHORITY/$path")

    private class TestDocumentsProvider : ContentProvider() {
        override fun onCreate(): Boolean = true
        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?
        ): Cursor? = null
        override fun getType(uri: Uri): String = "application/octet-stream"
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?
        ): Int = 0
    }

    private companion object {
        const val AUTHORITY = "dev.qtremors.arcile.test.import-grants"
        const val READ_GRANT = Intent.FLAG_GRANT_READ_URI_PERMISSION
    }
}
