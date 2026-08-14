package dev.qtremors.arcile.core.storage.data

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import dev.qtremors.arcile.core.storage.data.source.PrivilegedFileSystemDataSource
import dev.qtremors.arcile.core.storage.data.source.StorageNodeInput
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.PrivilegedContentAccessManager
import dev.qtremors.arcile.core.storage.domain.PrivilegedContentFailure
import dev.qtremors.arcile.core.storage.domain.PrivilegedContentGrantPurpose
import dev.qtremors.arcile.core.storage.domain.StorageNodeCapabilities
import dev.qtremors.arcile.core.storage.domain.StorageNodeRef
import io.mockk.coEvery
import io.mockk.mockk
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DefaultPrivilegedContentAccessManagerTest {
    @Test
    fun `issued URI contains only an opaque token and never exposes the protected path`() = runTest {
        val fixture = fixture(this)

        val grant = fixture.manager.issue(
            node = fixture.node,
            displayName = fixture.model.name,
            mimeType = fixture.model.mimeType,
            sizeBytes = fixture.model.size,
            modifiedAtMillis = fixture.model.lastModified,
            expectedConsumerUid = 10123
        ).getOrThrow()

        val uri = Uri.parse(grant.contentUri)
        assertEquals("content", uri.scheme)
        assertEquals(listOf(grant.token), uri.pathSegments)
        assertTrue(DefaultPrivilegedContentAccessManager.isValidToken(grant.token))
        assertFalse(uri.encodedPath.orEmpty().contains("storage"))
        assertFalse(uri.encodedPath.orEmpty().contains("private.txt"))
        assertEquals(StorageNodeRef.ROOT_BACKEND_ID, grant.backendId)
        assertEquals(fixture.node.canonicalIdentity.value, grant.canonicalStorageIdentity)
        assertEquals(10123, grant.expectedConsumerUid)
    }

    @Test
    fun `internal grant is reused for the same stable source and consumer`() = runTest {
        val fixture = fixture(this)

        val first = fixture.issueInternal(expectedUid = 10001)
        val second = fixture.issueInternal(expectedUid = 10001)

        assertEquals(first.token, second.token)
        assertEquals(1, fixture.manager.activeGrants().size)
    }

    @Test
    fun `internal grants are separated by expected consumer UID`() = runTest {
        val fixture = fixture(this)

        val appGrant = fixture.issueInternal(expectedUid = 10001)
        val isolatedGrant = fixture.issueInternal(expectedUid = 10002)

        assertNotEquals(appGrant.token, isolatedGrant.token)
        assertEquals(2, fixture.manager.activeGrants().size)
    }

    @Test
    fun `external grants are never reused even for the same file`() = runTest {
        val fixture = fixture(this)

        val first = fixture.issueExternal()
        val second = fixture.issueExternal()

        assertNotEquals(first.token, second.token)
        assertEquals(2, fixture.manager.activeGrants().size)
    }

    @Test
    fun `known consumer UID is enforced before the backend is opened`() = runTest {
        val fixture = fixture(this)
        val grant = fixture.issueInternal(expectedUid = 10001)

        val result = fixture.manager.openGrantedContent(grant.token, consumerUid = 10002)

        assertTrue(result.exceptionOrNull() is PrivilegedContentFailure.ConsumerMismatch)
        assertEquals(0, fixture.openCount.get())
    }

    @Test
    fun `external handoff accepts any platform-authorized consumer when UID is unknown`() = runTest {
        val fixture = fixture(this)
        val grant = fixture.issueExternal()

        val first = fixture.manager.openGrantedContent(grant.token, consumerUid = 11001).getOrThrow()
        val second = fixture.manager.openGrantedContent(grant.token, consumerUid = 11002).getOrThrow()

        first.reader.close()
        second.reader.close()
        assertEquals(2, fixture.openCount.get())
    }

    @Test
    fun `random reads return the requested protected bytes`() = runTest {
        val fixture = fixture(this, bytes = "0123456789abcdef".toByteArray())
        val grant = fixture.issueInternal(expectedUid = 10001)
        val content = fixture.manager.openGrantedContent(grant.token, 10001).getOrThrow()
        val first = ByteArray(4)
        val second = ByteArray(5)

        assertEquals(4, content.reader.readAt(8, first))
        assertEquals(5, content.reader.readAt(1, second))

        assertArrayEquals("89ab".toByteArray(), first)
        assertArrayEquals("12345".toByteArray(), second)
        assertTrue(fixture.openCount.get() >= 2)
        content.reader.close()
    }

    @Test
    fun `reader validates buffer ranges without touching the backend again`() = runTest {
        val fixture = fixture(this)
        val grant = fixture.issueInternal(expectedUid = 10001)
        val content = fixture.manager.openGrantedContent(grant.token, 10001).getOrThrow()

        val result = runCatching { content.reader.readAt(0, ByteArray(4), offset = 3, length = 2) }

        assertTrue(result.isFailure)
        assertEquals(1, fixture.openCount.get())
        content.reader.close()
    }

    @Test
    fun `read at end of file returns zero`() = runTest {
        val fixture = fixture(this)
        val grant = fixture.issueInternal(expectedUid = 10001)
        val content = fixture.manager.openGrantedContent(grant.token, 10001).getOrThrow()

        assertEquals(0, content.reader.readAt(content.sizeBytes, ByteArray(8)))

        content.reader.close()
    }

    @Test
    fun `closing a reader is idempotent`() = runTest {
        val fixture = fixture(this)
        val grant = fixture.issueInternal(expectedUid = 10001)
        val content = fixture.manager.openGrantedContent(grant.token, 10001).getOrThrow()

        content.reader.close()
        content.reader.close()

        assertEquals(1, fixture.closeCount.get())
        assertTrue(fixture.manager.describe(grant.token).isSuccess)
    }

    @Test
    fun `external grant revokes after its last reader closes`() = runTest {
        val fixture = fixture(this, revokeDelayMillis = 500)
        val grant = fixture.issueExternal()
        val first = fixture.manager.openGrantedContent(grant.token, 10001).getOrThrow()
        val second = fixture.manager.openGrantedContent(grant.token, 10002).getOrThrow()

        first.reader.close()
        advanceTimeBy(1_000)
        runCurrent()
        assertTrue(fixture.manager.describe(grant.token).isSuccess)

        second.reader.close()
        advanceTimeBy(499)
        runCurrent()
        assertTrue(fixture.manager.describe(grant.token).isSuccess)
        advanceTimeBy(1)
        runCurrent()
        assertTrue(fixture.manager.describe(grant.token).isFailure)
    }

    @Test
    fun `reopening an external grant cancels pending automatic revocation`() = runTest {
        val fixture = fixture(this, revokeDelayMillis = 500)
        val grant = fixture.issueExternal()
        fixture.manager.openGrantedContent(grant.token, 10001).getOrThrow().reader.close()
        advanceTimeBy(250)

        val reopened = fixture.manager.openGrantedContent(grant.token, 10001).getOrThrow()
        advanceTimeBy(500)
        runCurrent()

        assertTrue(fixture.manager.describe(grant.token).isSuccess)
        reopened.reader.close()
    }

    @Test
    fun `internal preview remains valid after its reader closes`() = runTest {
        val fixture = fixture(this, revokeDelayMillis = 10)
        val grant = fixture.issueInternal(expectedUid = 10001)
        fixture.manager.openGrantedContent(grant.token, 10001).getOrThrow().reader.close()

        advanceTimeBy(1_000)
        runCurrent()

        assertTrue(fixture.manager.describe(grant.token).isSuccess)
    }

    @Test
    fun `expired grant is removed from describe and active lists`() = runTest {
        var now = 1_000L
        val fixture = fixture(this, clock = { now })
        val grant = fixture.manager.issue(
            node = fixture.node,
            displayName = fixture.model.name,
            lifetimeMillis = 1_000,
            expectedConsumerUid = 10001
        ).getOrThrow()

        now = 2_001L

        assertTrue(fixture.manager.describe(grant.token).isFailure)
        assertTrue(fixture.manager.activeGrants().isEmpty())
    }

    @Test
    fun `malformed token is rejected without registry lookup`() = runTest {
        val fixture = fixture(this)

        assertTrue(fixture.manager.describe("../../private.txt").isFailure)
        assertTrue(fixture.manager.describe("f".repeat(63)).isFailure)
        assertFalse(DefaultPrivilegedContentAccessManager.isValidToken("G".repeat(64)))
    }

    @Test
    fun `changed size invalidates and revokes grant before content opens`() = runTest {
        val fixture = fixture(this)
        val grant = fixture.issueInternal(expectedUid = 10001)
        coEvery { fixture.source.inspectNode(any()) } returns Result.success(
            fixture.model.copy(size = fixture.model.size + 1)
        )

        val result = fixture.manager.openGrantedContent(grant.token, 10001)

        assertTrue(result.exceptionOrNull() is PrivilegedContentFailure.SourceChanged)
        assertTrue(fixture.manager.describe(grant.token).isFailure)
        assertEquals(0, fixture.openCount.get())
    }

    @Test
    fun `changed modified time invalidates source snapshot`() = runTest {
        val fixture = fixture(this)
        val grant = fixture.issueInternal(expectedUid = 10001)
        coEvery { fixture.source.inspectNode(any()) } returns Result.success(
            fixture.model.copy(lastModified = fixture.model.lastModified + 1)
        )

        val result = fixture.manager.openGrantedContent(grant.token, 10001)

        assertTrue(result.exceptionOrNull() is PrivilegedContentFailure.SourceChanged)
        assertTrue(fixture.manager.describe(grant.token).isFailure)
    }

    @Test
    fun `changed canonical identity is rejected even when display path is unchanged`() = runTest {
        val fixture = fixture(this)
        val grant = fixture.issueInternal(expectedUid = 10001)
        val replacement = StorageNodeRef.root(
            displayPath = fixture.node.displayPath.absolutePath,
            remoteCanonicalIdentity = "/replacement/inode",
            capabilities = fixture.node.capabilities
        )
        coEvery { fixture.source.inspectNode(any()) } returns Result.success(
            fixture.model.copy(nodeRef = replacement)
        )

        val result = fixture.manager.openGrantedContent(grant.token, 10001)

        assertTrue(result.exceptionOrNull() is PrivilegedContentFailure.SourceChanged)
        assertEquals(0, fixture.openCount.get())
    }

    @Test
    fun `stale caller metadata prevents grant issuance`() = runTest {
        val fixture = fixture(this)

        val result = fixture.manager.issue(
            node = fixture.node,
            displayName = fixture.model.name,
            sizeBytes = fixture.model.size + 10,
            modifiedAtMillis = fixture.model.lastModified
        )

        assertTrue(result.exceptionOrNull() is PrivilegedContentFailure.SourceChanged)
        assertTrue(fixture.manager.activeGrants().isEmpty())
    }

    @Test
    fun `directory and non-readable node cannot receive grants`() = runTest {
        val fixture = fixture(this)
        coEvery { fixture.source.inspectNode(any()) } returns Result.success(
            fixture.model.copy(isDirectory = true)
        )

        val directory = fixture.manager.issue(fixture.node, "Folder")
        val unreadable = fixture.manager.issue(
            fixture.node.copy(capabilities = StorageNodeCapabilities(canRead = false)),
            "private.txt"
        )

        assertTrue(directory.exceptionOrNull() is PrivilegedContentFailure.UnsupportedNode)
        assertTrue(unreadable.exceptionOrNull() is PrivilegedContentFailure.UnsupportedNode)
    }

    @Test
    fun `ordinary local node cannot receive protected capability`() = runTest {
        val fixture = fixture(this)

        val result = fixture.manager.issue(
            StorageNodeRef.local("/storage/emulated/0/private.txt"),
            "private.txt"
        )

        assertTrue(result.exceptionOrNull() is PrivilegedContentFailure.UnsupportedNode)
    }

    @Test
    fun `backend disconnect does not redirect or destroy reusable grant`() = runTest {
        val fixture = fixture(this)
        val grant = fixture.issueInternal(expectedUid = 10001)
        coEvery { fixture.source.inspectNode(any()) } returns
            Result.failure(IllegalStateException("Root disconnected")) andThen
            Result.success(fixture.model)

        val disconnected = fixture.manager.openGrantedContent(grant.token, 10001)
        val reconnected = fixture.manager.openGrantedContent(grant.token, 10001)

        assertTrue(disconnected.exceptionOrNull() is PrivilegedContentFailure.BackendUnavailable)
        assertTrue(reconnected.isSuccess)
        assertEquals(StorageNodeRef.ROOT_BACKEND_ID, grant.backendId)
        reconnected.getOrThrow().reader.close()
    }

    @Test
    fun `failed descriptor open keeps internal grant available for retry`() = runTest {
        val fixture = fixture(this)
        val grant = fixture.issueInternal(expectedUid = 10001)
        coEvery { fixture.source.openNodeInput(any()) } returns
            Result.failure(IllegalStateException("service restarted")) andThen
            Result.success(StorageNodeInput(ByteArrayInputStream(fixture.bytes)))

        val first = fixture.manager.openGrantedContent(grant.token, 10001)
        val second = fixture.manager.openGrantedContent(grant.token, 10001)

        assertTrue(first.exceptionOrNull() is PrivilegedContentFailure.BackendUnavailable)
        assertTrue(second.isSuccess)
        second.getOrThrow().reader.close()
    }

    @Test
    fun `explicit revoke closes capability to future readers`() = runTest {
        val fixture = fixture(this)
        val grant = fixture.issueInternal(expectedUid = 10001)

        assertTrue(fixture.manager.revoke(grant.token))
        assertFalse(fixture.manager.revoke(grant.token))
        assertTrue(fixture.manager.openGrantedContent(grant.token, 10001).isFailure)
    }

    @Test
    fun `revoke all removes every purpose and reuse index`() = runTest {
        val fixture = fixture(this)
        fixture.issueInternal(expectedUid = 10001)
        fixture.issueInternal(expectedUid = 10002)
        fixture.issueExternal()

        fixture.manager.revokeAll()

        assertTrue(fixture.manager.activeGrants().isEmpty())
        val replacement = fixture.issueInternal(expectedUid = 10001)
        assertEquals(1, fixture.manager.activeGrants().size)
        assertTrue(fixture.manager.describe(replacement.token).isSuccess)
    }

    @Test
    fun `capacity evicts oldest closed external capability first`() = runTest {
        var now = 1_000L
        val fixture = fixture(this, clock = { now }, maximumGrants = 2)
        val oldestExternal = fixture.issueExternal()
        now += 1
        val internal = fixture.issueInternal(expectedUid = 10001)
        now += 1

        val newestExternal = fixture.issueExternal()

        assertTrue(fixture.manager.describe(oldestExternal.token).isFailure)
        assertTrue(fixture.manager.describe(internal.token).isSuccess)
        assertTrue(fixture.manager.describe(newestExternal.token).isSuccess)
    }

    @Test
    fun `active grants are ordered by expiration`() = runTest {
        var now = 1_000L
        val fixture = fixture(this, clock = { now })
        val long = fixture.manager.issue(
            fixture.node,
            fixture.model.name,
            lifetimeMillis = 10_000,
            purpose = PrivilegedContentGrantPurpose.EXTERNAL_HANDOFF
        ).getOrThrow()
        val short = fixture.manager.issue(
            fixture.node,
            fixture.model.name,
            lifetimeMillis = 2_000,
            purpose = PrivilegedContentGrantPurpose.EXTERNAL_HANDOFF
        ).getOrThrow()

        assertEquals(listOf(short.token, long.token), fixture.manager.activeGrants().map { it.token })
    }

    private fun fixture(
        scope: kotlinx.coroutines.CoroutineScope,
        bytes: ByteArray = "protected bytes".toByteArray(),
        clock: () -> Long = { 1_000L },
        revokeDelayMillis: Long = 500L,
        maximumGrants: Int = 32
    ): Fixture {
        val source = mockk<PrivilegedFileSystemDataSource>()
        val node = StorageNodeRef.root(
            displayPath = "/storage/emulated/0/Android/data/example/private.txt",
            remoteCanonicalIdentity = "/storage/emulated/0/Android/data/example/private.txt",
            capabilities = StorageNodeCapabilities(canRead = true, canWrite = true)
        )
        val model = FileModel(
            name = "private.txt",
            absolutePath = node.displayPath.absolutePath,
            size = bytes.size.toLong(),
            lastModified = 700L,
            isDirectory = false,
            extension = "txt",
            mimeType = "text/plain",
            nodeRef = node
        )
        val openCount = AtomicInteger()
        val closeCount = AtomicInteger()
        coEvery { source.inspectNode(any()) } returns Result.success(model)
        coEvery { source.openNodeInput(any()) } answers {
            openCount.incrementAndGet()
            Result.success(
                StorageNodeInput(ByteArrayInputStream(bytes)) {
                    closeCount.incrementAndGet()
                }
            )
        }
        val tokenCounter = AtomicInteger()
        val dispatchers = ArcileDispatchers(
            io = Dispatchers.IO,
            default = Dispatchers.Default,
            main = Dispatchers.Unconfined,
            storage = Dispatchers.IO
        )
        val manager = DefaultPrivilegedContentAccessManager(
            context = ApplicationProvider.getApplicationContext(),
            dataSource = source,
            applicationScope = scope,
            dispatchers = dispatchers,
            clock = clock,
            tokenGenerator = {
                tokenCounter.incrementAndGet().toString(16).padStart(64, '0')
            },
            revokeAfterCloseMillis = revokeDelayMillis,
            maximumActiveGrants = maximumGrants
        )
        return Fixture(manager, source, node, model, bytes, openCount, closeCount)
    }

    private data class Fixture(
        val manager: DefaultPrivilegedContentAccessManager,
        val source: PrivilegedFileSystemDataSource,
        val node: StorageNodeRef,
        val model: FileModel,
        val bytes: ByteArray,
        val openCount: AtomicInteger,
        val closeCount: AtomicInteger
    ) {
        suspend fun issueInternal(expectedUid: Int) = manager.issue(
            node = node,
            displayName = model.name,
            mimeType = model.mimeType,
            sizeBytes = model.size,
            modifiedAtMillis = model.lastModified,
            purpose = PrivilegedContentGrantPurpose.INTERNAL_PREVIEW,
            lifetimeMillis = PrivilegedContentAccessManager.DEFAULT_INTERNAL_GRANT_LIFETIME_MILLIS,
            expectedConsumerUid = expectedUid
        ).getOrThrow()

        suspend fun issueExternal() = manager.issue(
            node = node,
            displayName = model.name,
            mimeType = model.mimeType,
            sizeBytes = model.size,
            modifiedAtMillis = model.lastModified,
            purpose = PrivilegedContentGrantPurpose.EXTERNAL_HANDOFF,
            lifetimeMillis = PrivilegedContentAccessManager.DEFAULT_EXTERNAL_GRANT_LIFETIME_MILLIS,
            expectedConsumerUid = null
        ).getOrThrow()
    }
}
