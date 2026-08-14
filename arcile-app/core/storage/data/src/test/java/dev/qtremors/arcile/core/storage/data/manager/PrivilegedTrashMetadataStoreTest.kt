package dev.qtremors.arcile.core.storage.data.manager

import dev.qtremors.arcile.core.storage.data.CachedFileModel
import dev.qtremors.arcile.core.storage.data.CachedStorageNodeRef
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.StorageNodeRef
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PrivilegedTrashMetadataStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `write and read preserve Root identities and cached file metadata`() {
        val store = store()
        val record = record("one", deletionTime = 100L)

        store.write(record)
        val restored = store.read("one")

        assertEquals(record, restored)
        assertEquals(StorageNodeRef.ROOT_BACKEND_ID, restored?.originalNode?.backendId)
        assertEquals("report.txt", restored?.originalFile?.name)
        assertEquals(42L, restored?.originalFile?.size)
    }

    @Test
    fun `write publishes one final json file without temporary artifacts`() {
        val directory = temporaryFolder.newFolder("metadata")
        val store = PrivilegedTrashMetadataStore(directory)

        store.write(record("one"))

        assertEquals(listOf("one.json"), directory.listFiles().orEmpty().map(File::getName))
        assertTrue(File(directory, "one.json").readText().contains("originalNode"))
    }

    @Test
    fun `rewriting a record atomically replaces its previous payload`() {
        val store = store()
        store.write(record("one", deletionTime = 1L))

        store.write(record("one", deletionTime = 2L))

        assertEquals(2L, store.read("one")?.deletionTime)
        assertEquals(1, store.list().size)
    }

    @Test
    fun `list sorts newest first and ignores unrelated files`() {
        val directory = temporaryFolder.newFolder("listed")
        val store = PrivilegedTrashMetadataStore(directory)
        store.write(record("old", deletionTime = 10L))
        store.write(record("new", deletionTime = 30L))
        store.write(record("middle", deletionTime = 20L))
        File(directory, "notes.txt").writeText("ignore")

        val listed = store.list()

        assertEquals(listOf("new", "middle", "old"), listed.map(PrivilegedTrashRecord::id))
    }

    @Test
    fun `malformed metadata is ignored without deleting recoverable files`() {
        val directory = temporaryFolder.newFolder("malformed")
        val malformed = File(directory, "broken.json").apply { writeText("{not-json") }
        val store = PrivilegedTrashMetadataStore(directory)

        assertNull(store.read("broken"))
        assertTrue(store.list().isEmpty())
        assertTrue(malformed.exists())
    }

    @Test
    fun `unsupported future schema is ignored`() {
        val directory = temporaryFolder.newFolder("future")
        val payload = Json { encodeDefaults = true }.encodeToString(record("future")).replace(
            "\"schemaVersion\":1",
            "\"schemaVersion\":99"
        )
        File(directory, "future.json").writeText(payload)
        val store = PrivilegedTrashMetadataStore(directory)

        assertNull(store.read("future"))
        assertTrue(store.list().isEmpty())
    }

    @Test
    fun `delete is idempotent`() {
        val store = store()
        store.write(record("one"))

        assertTrue(store.delete("one"))
        assertTrue(store.delete("one"))
        assertNull(store.read("one"))
    }

    @Test
    fun `clear missing retains valid ids and removes stale records`() {
        val directory = temporaryFolder.newFolder("clear")
        val store = PrivilegedTrashMetadataStore(directory)
        store.write(record("keep"))
        store.write(record("remove"))
        File(directory, "unrelated.txt").writeText("keep")

        store.clearMissing(setOf("keep"))

        assertEquals(listOf("keep"), store.list().map(PrivilegedTrashRecord::id))
        assertTrue(File(directory, "unrelated.txt").exists())
    }

    @Test
    fun `ids cannot escape the metadata directory`() {
        val store = store()
        val invalid = listOf("", "../escape", "folder/item", "folder\\item", "nul\u0000id")

        invalid.forEach { id ->
            assertTrue(runCatching { store.read(id) }.isFailure)
        }

        assertFalse(File(temporaryFolder.root.parentFile, "escape.json").exists())
    }

    @Test
    fun `Unicode and newline paths remain intact`() {
        val original = rootNode("/storage/emulated/0/文档/report\nfinal.txt")
        val payload = rootNode("/storage/emulated/0/.arcile/.trash/id/report\nfinal.txt")
        val file = FileModel(
            name = "report\nfinal.txt",
            absolutePath = original.displayPath.absolutePath,
            size = 7L,
            nodeRef = original
        )
        val record = PrivilegedTrashRecord(
            id = "unicode",
            originalNode = CachedStorageNodeRef.from(original),
            payloadNode = CachedStorageNodeRef.from(payload),
            originalFile = CachedFileModel.from(file),
            deletionTime = 1L,
            sourceVolumeId = "primary",
            sourceStorageKind = "INTERNAL"
        )
        val store = store()

        store.write(record)
        val restored = store.read("unicode")

        assertEquals(original.displayPath.absolutePath, restored?.originalNode?.displayPath)
        assertEquals(file.name, restored?.originalFile?.name)
    }

    private fun store() = PrivilegedTrashMetadataStore(temporaryFolder.newFolder())

    private fun record(id: String, deletionTime: Long = 1L): PrivilegedTrashRecord {
        val original = rootNode("/storage/emulated/0/Documents/report.txt")
        val payload = rootNode("/storage/emulated/0/.arcile/.trash/$id/report.txt")
        val file = FileModel(
            name = "report.txt",
            absolutePath = original.displayPath.absolutePath,
            size = 42L,
            lastModified = 50L,
            extension = "txt",
            mimeType = "text/plain",
            nodeRef = original
        )
        return PrivilegedTrashRecord(
            id = id,
            originalNode = CachedStorageNodeRef.from(original),
            payloadNode = CachedStorageNodeRef.from(payload),
            originalFile = CachedFileModel.from(file),
            deletionTime = deletionTime,
            sourceVolumeId = "primary",
            sourceStorageKind = "INTERNAL"
        )
    }

    private fun rootNode(path: String) = StorageNodeRef.root(path, path)
}
