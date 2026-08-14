package dev.qtremors.arcile.core.storage.data.source

import dev.qtremors.arcile.core.privilege.PrivilegeBackendId
import dev.qtremors.arcile.core.privilege.PrivilegeCapability
import dev.qtremors.arcile.core.privilege.PrivilegeServiceIdentity
import dev.qtremors.arcile.core.privilege.PrivilegeSession
import dev.qtremors.arcile.core.privilege.PrivilegeTransport
import dev.qtremors.arcile.core.privilege.PrivilegedFileEntry
import dev.qtremors.arcile.core.privilege.PrivilegedFileType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivilegedPathPolicyTest {
    private var protectedWrites = false
    private val policy = PrivilegedPathPolicy(
        packageName = "dev.qtremors.arcile",
        protectedWritesEnabled = { protectedWrites },
        storageRoots = { listOf("/storage/emulated/0", "/storage/ABCD-1234") }
    )

    @Test
    fun `classifies supported path scopes without local filesystem access`() {
        assertScope(PrivilegedPathScope.USER_STORAGE, "/storage/emulated/0/Download/file.txt")
        assertScope(PrivilegedPathScope.USER_STORAGE, "/mnt/media_rw/ABCD-1234/DCIM")
        assertScope(
            PrivilegedPathScope.ANDROID_APP_STORAGE,
            "/storage/emulated/0/Android/data/com.example/cache"
        )
        assertScope(PrivilegedPathScope.APP_PRIVATE_DATA, "/data/user/0/com.example/files")
        assertScope(PrivilegedPathScope.SYSTEM_READ_ONLY, "/system/framework")
        assertScope(PrivilegedPathScope.VIRTUAL_FILESYSTEM, "/proc/123/status")
        assertScope(
            PrivilegedPathScope.ARCILE_PRIVATE,
            "/data/user/0/dev.qtremors.arcile/files"
        )
        assertScope(
            PrivilegedPathScope.ARCILE_PRIVATE,
            "/storage/emulated/0/.onlyfiles/vault/node"
        )
        assertScope(PrivilegedPathScope.UNSUPPORTED, "/unknown/mount/file")
    }

    @Test
    fun `rejects traversal nul relative and backslash paths before policy evaluation`() {
        val invalid = listOf(
            "/storage/emulated/0/../data",
            "/storage/emulated/0/./Download",
            "/storage/emulated/0/bad\u0000name",
            "storage/emulated/0",
            "/storage/emulated/0\\Download"
        )

        invalid.forEach { path ->
            val decision = policy.evaluate(
                path,
                PrivilegedPathOperation.READ,
                rootSession,
                regular(path)
            )
            assertFalse("Expected path to be rejected: $path", decision.allowed)
            assertEquals(PrivilegedPathScope.UNSUPPORTED, decision.scope)
        }
    }

    @Test
    fun `root can read app private data while shell cannot`() {
        val path = "/data/user/0/com.example/files/report.txt"

        assertTrue(
            policy.evaluate(path, PrivilegedPathOperation.READ, rootSession, regular(path)).allowed
        )
        val shellDecision = policy.evaluate(
            path,
            PrivilegedPathOperation.READ,
            shellSession,
            regular(path)
        )
        assertFalse(shellDecision.allowed)
        assertTrue(shellDecision.reason.orEmpty().contains("Root"))
    }

    @Test
    fun `app private writes require root and advanced opt in`() {
        val path = "/data/user/0/com.example/files/report.txt"

        assertFalse(
            policy.evaluate(path, PrivilegedPathOperation.WRITE, rootSession, regular(path)).allowed
        )
        protectedWrites = true
        assertTrue(
            policy.evaluate(path, PrivilegedPathOperation.WRITE, rootSession, regular(path)).allowed
        )
        assertFalse(
            policy.evaluate(path, PrivilegedPathOperation.WRITE, shellSession, regular(path)).allowed
        )
    }

    @Test
    fun `system partitions remain read only even for root`() {
        val path = "/system/build.prop"

        assertTrue(
            policy.evaluate(path, PrivilegedPathOperation.READ, rootSession, regular(path)).allowed
        )
        assertFalse(
            policy.evaluate(path, PrivilegedPathOperation.WRITE, rootSession, regular(path)).allowed
        )
        assertFalse(
            policy.evaluate(path, PrivilegedPathOperation.DELETE, rootSession, regular(path)).allowed
        )
        assertFalse(
            policy.evaluate(path, PrivilegedPathOperation.READ, shellSession, regular(path)).allowed
        )
    }

    @Test
    fun `virtual filesystems allow limited reads and reject special files`() {
        val status = regular("/proc/123/status", writable = false)
        val device = entry("/dev/block/dm-0", PrivilegedFileType.BLOCK_DEVICE)

        assertTrue(
            policy.evaluate(status.path, PrivilegedPathOperation.READ, rootSession, status).allowed
        )
        assertFalse(
            policy.evaluate(status.path, PrivilegedPathOperation.WRITE, rootSession, status).allowed
        )
        assertFalse(
            policy.evaluate(device.path, PrivilegedPathOperation.READ, rootSession, device).allowed
        )
    }

    @Test
    fun `recursive deletion protects filesystem and configured storage roots`() {
        val protectedRoots = listOf("/", "/storage", "/storage/emulated/0")

        protectedRoots.forEach { path ->
            val decision = policy.evaluate(
                path,
                PrivilegedPathOperation.RECURSIVE_DELETE,
                rootSession,
                directory(path)
            )
            assertFalse("Expected protected root: $path", decision.allowed)
            assertTrue(decision.reason.orEmpty().contains("roots"))
        }
        assertTrue(
            policy.evaluate(
                "/storage/emulated/0/Download/folder",
                PrivilegedPathOperation.RECURSIVE_DELETE,
                rootSession,
                directory("/storage/emulated/0/Download/folder")
            ).allowed
        )
    }

    @Test
    fun `operation is rejected when service capability is absent`() {
        val readOnlySession = rootSession.copy(capabilities = setOf(PrivilegeCapability.READ))
        val path = "/storage/emulated/0/Download/file.txt"

        assertTrue(
            policy.evaluate(path, PrivilegedPathOperation.READ, readOnlySession, regular(path)).allowed
        )
        val delete = policy.evaluate(
            path,
            PrivilegedPathOperation.DELETE,
            readOnlySession,
            regular(path)
        )
        assertFalse(delete.allowed)
        assertTrue(delete.reason.orEmpty().contains("does not support"))
    }

    @Test
    fun `recursive delete treats a symbolic link as a leaf`() {
        val path = "/storage/emulated/0/Download/link"
        val link = entry(path, PrivilegedFileType.SYMBOLIC_LINK)

        assertTrue(
            policy.evaluate(
                path,
                PrivilegedPathOperation.RECURSIVE_DELETE,
                rootSession,
                link
            ).allowed
        )
    }

    @Test
    fun `source and destination are validated independently`() {
        val source = regular("/storage/emulated/0/Download/source.txt")

        assertTrue(
            policy.validateTransfer(
                source,
                "/storage/emulated/0/Documents/source.txt",
                move = false,
                session = rootSession
            ).isSuccess
        )
        assertTrue(
            policy.validateTransfer(
                source,
                "/system/source.txt",
                move = false,
                session = rootSession
            ).isFailure
        )
    }

    private fun assertScope(expected: PrivilegedPathScope, path: String) {
        assertEquals(expected, policy.classify(path).getOrThrow())
    }

    private fun regular(path: String, writable: Boolean = true) =
        entry(path, PrivilegedFileType.REGULAR_FILE, writable)

    private fun directory(path: String) = entry(path, PrivilegedFileType.DIRECTORY)

    private fun entry(
        path: String,
        type: PrivilegedFileType,
        writable: Boolean = true
    ) = PrivilegedFileEntry(
        path = path,
        canonicalIdentity = path,
        displayName = path.substringAfterLast('/').ifEmpty { "/" },
        type = type,
        size = 10,
        modifiedAtMillis = 100,
        mode = 0,
        readable = true,
        writable = writable
    )

    private val rootSession = PrivilegeSession(
        backendId = PrivilegeBackendId.ROOT,
        generation = 1,
        identity = PrivilegeServiceIdentity(
            effectiveUid = 0,
            pid = 1,
            transport = PrivilegeTransport.ROOT_SERVICE
        ),
        capabilities = PrivilegeCapability.entries.toSet()
    )

    private val shellSession = PrivilegeSession(
        backendId = PrivilegeBackendId.SHIZUKU,
        generation = 1,
        identity = PrivilegeServiceIdentity(
            effectiveUid = 2000,
            pid = 2,
            transport = PrivilegeTransport.SHIZUKU_USER_SERVICE
        ),
        capabilities = PrivilegeCapability.entries.toSet()
    )
}
