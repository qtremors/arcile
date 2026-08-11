package dev.qtremors.arcile.core.storage.data

import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.StorageNodeCapabilities
import dev.qtremors.arcile.core.storage.domain.StorageNodeRef
import dev.qtremors.arcile.core.storage.domain.StorageUsageNode
import dev.qtremors.arcile.core.storage.domain.StorageUsageNodeKind
import dev.qtremors.arcile.core.storage.domain.StorageUsageScanStatus
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotPayloadsNodeIdentityTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `Root file snapshot round trip preserves canonical and backend identity`() {
        val ref = StorageNodeRef.root(
            displayPath = "/data/user/0/example/files/report.txt",
            remoteCanonicalIdentity = "/data/user/0/example/files/report.txt",
            volumeId = "root-data"
        )
        val file = FileModel(
            name = "report.txt",
            absolutePath = ref.displayPath.absolutePath,
            size = 25L,
            lastModified = 100L,
            extension = "txt",
            mimeType = "text/plain",
            nodeRef = ref
        )

        val restored = json.decodeFromString<CachedFileModel>(
            json.encodeToString(CachedFileModel.from(file))
        ).toDomain()

        assertEquals(file, restored)
        assertEquals(StorageNodeRef.ROOT_BACKEND_ID, restored.nodeRef.backendId)
        assertEquals(ref.canonicalIdentity, restored.nodeRef.canonicalIdentity)
        assertEquals(ref.backendIdentity, restored.nodeRef.backendIdentity)
    }

    @Test
    fun `Shizuku capability policy survives a file snapshot`() {
        val capabilities = StorageNodeCapabilities(
            canRead = true,
            canWrite = false,
            canDelete = false,
            canTrash = false,
            canArchive = true,
            canRename = false,
            canCopy = true,
            canMove = false,
            canExport = true,
            canShare = false,
            canOpenWith = true
        )
        val ref = StorageNodeRef.shizuku(
            displayPath = "/data/local/tmp/log.txt",
            remoteCanonicalIdentity = "/data/local/tmp/log.txt",
            capabilities = capabilities
        )

        val restored = CachedFileModel.from(
            FileModel("log.txt", ref.displayPath.absolutePath, nodeRef = ref)
        ).toDomain().nodeRef

        assertEquals(capabilities, restored.capabilities)
        assertFalse(restored.capabilities.canWrite)
        assertFalse(restored.capabilities.canShare)
        assertTrue(restored.capabilities.canOpenWith)
    }

    @Test
    fun `generic cached node retains content URI and opaque backend fields`() {
        val original = StorageNodeRef(
            backendId = StorageNodeRef.ONLYFILES_BACKEND_ID,
            displayPath = dev.qtremors.arcile.core.storage.domain.StorageNodePath.of("/.onlyfiles/vault/node"),
            canonicalIdentity = dev.qtremors.arcile.core.storage.domain.CanonicalStorageIdentity.of("onlyfiles:vault:node"),
            capabilities = StorageNodeCapabilities(
                canRead = true,
                canWrite = true,
                canDelete = true,
                canTrash = false,
                canArchive = false
            ),
            contentUri = "content://arcile/token",
            backendIdentity = "vault:node"
        )

        val restored = CachedStorageNodeRef.from(original).toDomain()

        assertEquals(original, restored)
        assertEquals("content://arcile/token", restored.contentUri)
        assertEquals("vault:node", restored.backendIdentity)
    }

    @Test
    fun `MediaStore snapshot retains its content identity`() {
        val ref = StorageNodeRef.mediaStore(
            id = 42L,
            volumeName = "external_primary",
            contentUri = "content://media/external/images/media/42",
            displayPath = "/storage/emulated/0/Pictures/photo.jpg",
            volumeId = "primary",
            localPath = "/storage/emulated/0/Pictures/photo.jpg"
        )
        val file = FileModel("photo.jpg", ref.displayPath.absolutePath, extension = "jpg", nodeRef = ref)

        val restored = CachedFileModel.from(file).toDomain()

        assertEquals(StorageNodeRef.MEDIA_STORE_BACKEND_ID, restored.nodeRef.backendId)
        assertEquals(ref.backendIdentity, restored.nodeRef.backendIdentity)
        assertEquals(ref.contentUri, restored.nodeRef.contentUri)
        assertEquals(ref.volumeId, restored.nodeRef.volumeId)
    }

    @Test
    fun `legacy Root payload reconstructs privileged identity without the new node object`() {
        val payload = """
            {
              "name":"config.xml",
              "absolutePath":"/data/system/config.xml",
              "size":10,
              "lastModified":20,
              "isDirectory":false,
              "extension":"xml",
              "isHidden":false,
              "mimeType":"text/xml",
              "backendId":"root",
              "volumeId":null,
              "contentUri":null,
              "backendIdentity":"/data/system/config.xml"
            }
        """.trimIndent()

        val restored = json.decodeFromString<CachedFileModel>(payload).toDomain()

        assertEquals(StorageNodeRef.ROOT_BACKEND_ID, restored.nodeRef.backendId)
        assertEquals("/data/system/config.xml", restored.nodeRef.backendIdentity)
        assertEquals("root:/data/system/config.xml", restored.nodeRef.canonicalIdentity.value)
    }

    @Test
    fun `legacy Shizuku payload reconstructs shell identity`() {
        val payload = """
            {
              "name":"package.list",
              "absolutePath":"/data/system/package.list",
              "size":1,
              "lastModified":2,
              "isDirectory":false,
              "extension":"list",
              "isHidden":false,
              "mimeType":null,
              "backendId":"shizuku",
              "volumeId":"shell",
              "contentUri":null,
              "backendIdentity":"/data/system/package.list"
            }
        """.trimIndent()

        val restored = json.decodeFromString<CachedFileModel>(payload).toDomain()

        assertEquals(StorageNodeRef.SHIZUKU_BACKEND_ID, restored.nodeRef.backendId)
        assertEquals("shell", restored.nodeRef.volumeId?.value)
        assertEquals("/data/system/package.list", restored.nodeRef.backendIdentity)
    }

    @Test
    fun `legacy local payload remains local`() {
        val payload = """
            {
              "name":"notes.txt",
              "absolutePath":"/storage/emulated/0/notes.txt",
              "size":1,
              "lastModified":2,
              "isDirectory":false,
              "extension":"txt",
              "isHidden":false,
              "mimeType":null,
              "backendId":"local",
              "volumeId":"primary",
              "contentUri":null,
              "backendIdentity":null
            }
        """.trimIndent()

        val restored = json.decodeFromString<CachedFileModel>(payload).toDomain()

        assertEquals(StorageNodeRef.LOCAL_BACKEND_ID, restored.nodeRef.backendId)
        assertEquals("primary", restored.nodeRef.volumeId?.value)
        assertNull(restored.nodeRef.backendIdentity)
    }

    @Test
    fun `usage tree snapshot preserves every concrete node reference`() {
        val rootRef = StorageNodeRef.root("/data", "/data")
        val folderRef = StorageNodeRef.root("/data/app", "/data/app")
        val fileRef = StorageNodeRef.root("/data/app/base.apk", "/data/app/base.apk")
        val usage = StorageUsageNode(
            name = "data",
            path = "/data",
            sizeBytes = 123L,
            kind = StorageUsageNodeKind.Folder,
            childCount = 1,
            status = StorageUsageScanStatus.Partial,
            children = listOf(
                StorageUsageNode(
                    name = "app",
                    path = "/data/app",
                    sizeBytes = 123L,
                    kind = StorageUsageNodeKind.Folder,
                    childCount = 1,
                    children = listOf(
                        StorageUsageNode(
                            name = "base.apk",
                            path = "/data/app/base.apk",
                            sizeBytes = 123L,
                            kind = StorageUsageNodeKind.File,
                            childCount = 0,
                            nodeRef = fileRef
                        )
                    ),
                    nodeRef = folderRef
                )
            ),
            nodeRef = rootRef
        )

        val restored = json.decodeFromString<CachedStorageUsageNode>(
            json.encodeToString(CachedStorageUsageNode.from(usage))
        ).toDomain()

        assertEquals(usage, restored)
        assertEquals(rootRef, restored.nodeRef)
        assertEquals(folderRef, restored.children.single().nodeRef)
        assertEquals(fileRef, restored.children.single().children.single().nodeRef)
    }

    @Test
    fun `grouped usage snapshot remains path-only`() {
        val grouped = StorageUsageNode(
            name = "Other small items",
            path = "/data/Other small items",
            sizeBytes = 30L,
            kind = StorageUsageNodeKind.Grouped,
            childCount = 4
        )

        val restored = CachedStorageUsageNode.from(grouped).toDomain()

        assertEquals(grouped, restored)
        assertNull(restored.nodeRef)
    }

    @Test
    fun `old usage payload without node reference remains readable`() {
        val payload = """
            {
              "name":"data",
              "path":"/data",
              "sizeBytes":100,
              "kind":"Folder",
              "childCount":0,
              "status":"Ready",
              "children":[]
            }
        """.trimIndent()

        val restored = json.decodeFromString<CachedStorageUsageNode>(payload).toDomain()

        assertEquals("/data", restored.path)
        assertEquals(100L, restored.sizeBytes)
        assertNull(restored.nodeRef)
    }
}
