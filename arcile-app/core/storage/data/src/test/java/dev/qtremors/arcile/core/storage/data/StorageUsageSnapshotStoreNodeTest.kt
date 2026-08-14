package dev.qtremors.arcile.core.storage.data

import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import dev.qtremors.arcile.core.storage.data.db.StorageUsageSnapshotDao
import dev.qtremors.arcile.core.storage.data.db.StorageUsageSnapshotEntity
import dev.qtremors.arcile.core.storage.domain.StorageNodeRef
import dev.qtremors.arcile.core.storage.domain.StorageUsageNode
import dev.qtremors.arcile.core.storage.domain.StorageUsageNodeKind
import dev.qtremors.arcile.core.storage.domain.StorageUsageScanLimits
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StorageUsageSnapshotStoreNodeTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val dispatchers = ArcileDispatchers(dispatcher, dispatcher, dispatcher, dispatcher)
    private val dao = mockk<StorageUsageSnapshotDao>()
    private val store = StorageUsageSnapshotStore(dao, dispatchers)
    private val limits = StorageUsageScanLimits(maxDepth = 3, maxVisitedNodes = 100)

    @Test
    fun `put uses backend and canonical identity in the persistent key`() = runTest {
        val root = rootNode("/data", "remote-data")
        val entity = slot<StorageUsageSnapshotEntity>()
        coEvery { dao.upsert(capture(entity)) } just Runs

        store.put(root, limits, usage(root, 10L))

        val decoded = StorageNodePersistenceIdentity.decode(entity.captured.rootPath)
        assertTrue(entity.captured.key.startsWith("usage:node-v1:"))
        assertEquals(StorageNodeRef.ROOT_BACKEND_ID, decoded?.backendId)
        assertEquals("root:remote-data", decoded?.canonicalIdentity)
        assertEquals("/data", decoded?.displayPath)
        assertTrue(entity.captured.payloadJson.contains("remote-data"))
    }

    @Test
    fun `same path on Root and Shizuku produces separate records`() = runTest {
        val entities = mutableListOf<StorageUsageSnapshotEntity>()
        coEvery { dao.upsert(capture(entities)) } just Runs
        val root = rootNode("/data", "data")
        val shizuku = shizukuNode("/data", "data")

        store.put(root, limits, usage(root, 10L))
        store.put(shizuku, limits, usage(shizuku, 20L))

        assertEquals(2, entities.size)
        assertNotEquals(entities[0].key, entities[1].key)
        assertEquals("root", StorageNodePersistenceIdentity.decode(entities[0].rootPath)?.backendId)
        assertEquals("shizuku", StorageNodePersistenceIdentity.decode(entities[1].rootPath)?.backendId)
    }

    @Test
    fun `get restores backend-aware tree from its exact identity key`() = runTest {
        val root = shizukuNode("/data/local/tmp", "tmp")
        val expected = usage(root, 55L)
        val payload = Json.encodeToString(CachedStorageUsageNode.from(expected))
        val identity = StorageNodePersistenceIdentity.from(root).encode()
        val key = "usage:$identity:3:48:0.0:100:30000"
        coEvery { dao.get(key) } returns StorageUsageSnapshotEntity(
            key = key,
            rootPath = identity,
            payloadJson = payload,
            cachedAt = 1L
        )

        val restored = store.get(root, limits)

        assertEquals(expected, restored)
        assertEquals(StorageNodeRef.SHIZUKU_BACKEND_ID, restored?.nodeRef?.backendId)
    }

    @Test
    fun `malformed payload is ignored`() = runTest {
        val root = rootNode("/data", "data")
        coEvery { dao.get(any()) } returns StorageUsageSnapshotEntity(
            key = "broken",
            rootPath = "node:root:root:data|/data",
            payloadJson = "{not-json",
            cachedAt = 1L
        )

        val restored = store.get(root, limits)

        assertNull(restored)
    }

    @Test
    fun `invalidating a descendant deletes its cached ancestor on the same backend`() = runTest {
        val cached = entity("root", "/data", "data", "ancestor")
        coEvery { dao.getAll() } returns listOf(cached)
        coEvery { dao.deleteKeys(any()) } just Runs

        store.invalidateNodes(listOf(rootNode("/data/user/0/app", "app")))

        coVerify(exactly = 1) { dao.deleteKeys(listOf("ancestor")) }
    }

    @Test
    fun `invalidating an ancestor deletes cached descendant scans`() = runTest {
        val cached = entity("root", "/data/user/0/app", "app", "descendant")
        coEvery { dao.getAll() } returns listOf(cached)
        coEvery { dao.deleteKeys(any()) } just Runs

        store.invalidateNodes(listOf(rootNode("/data", "data")))

        coVerify(exactly = 1) { dao.deleteKeys(listOf("descendant")) }
    }

    @Test
    fun `invalidation never crosses backend identity`() = runTest {
        val rootCached = entity("root", "/data", "data", "root-key")
        val shizukuCached = entity("shizuku", "/data", "data", "shizuku-key")
        coEvery { dao.getAll() } returns listOf(rootCached, shizukuCached)
        coEvery { dao.deleteKeys(any()) } just Runs

        store.invalidateNodes(listOf(rootNode("/data", "data")))

        coVerify(exactly = 1) { dao.deleteKeys(listOf("root-key")) }
    }

    @Test
    fun `unrelated paths do not issue an empty delete query`() = runTest {
        coEvery { dao.getAll() } returns listOf(entity("root", "/system", "system", "system-key"))

        store.invalidateNodes(listOf(rootNode("/data", "data")))

        coVerify(exactly = 0) { dao.deleteKeys(any()) }
    }

    @Test
    fun `empty node invalidation clears every stored usage tree`() = runTest {
        coEvery { dao.clear() } just Runs

        store.invalidateNodes(emptyList())

        coVerify(exactly = 1) { dao.clear() }
        coVerify(exactly = 0) { dao.getAll() }
    }

    @Test
    fun `legacy local records are left to path invalidation`() = runTest {
        val legacy = StorageUsageSnapshotEntity(
            key = "legacy",
            rootPath = "/storage/emulated/0",
            payloadJson = "{}",
            cachedAt = 1L
        )
        coEvery { dao.getAll() } returns listOf(legacy)

        store.invalidateNodes(listOf(rootNode("/storage/emulated/0", "storage")))

        coVerify(exactly = 0) { dao.deleteKeys(any()) }
    }

    private fun entity(
        backend: String,
        path: String,
        identity: String,
        key: String
    ) = StorageUsageSnapshotEntity(
        key = key,
        rootPath = StorageNodePersistenceIdentity(
            backendId = backend,
            canonicalIdentity = "$backend:$identity",
            displayPath = path
        ).encode(),
        payloadJson = "{}",
        cachedAt = 1L
    )

    private fun usage(root: StorageNodeRef, size: Long) = StorageUsageNode(
        name = root.displayPath.absolutePath.substringAfterLast('/'),
        path = root.displayPath.absolutePath,
        sizeBytes = size,
        kind = StorageUsageNodeKind.Folder,
        childCount = 0,
        nodeRef = root
    )

    private fun rootNode(path: String, identity: String) = StorageNodeRef.root(path, identity)

    private fun shizukuNode(path: String, identity: String) = StorageNodeRef.shizuku(path, identity)
}
