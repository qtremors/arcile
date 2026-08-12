package dev.qtremors.arcile.core.storage.data.source

import dev.qtremors.arcile.core.storage.data.runCatchingPreservingCancellation
import dev.qtremors.arcile.core.privilege.PrivilegeCapability
import dev.qtremors.arcile.core.privilege.PrivilegeSession
import dev.qtremors.arcile.core.privilege.PrivilegedFileEntry
import dev.qtremors.arcile.core.privilege.PrivilegedFileType

enum class PrivilegedPathScope {
    USER_STORAGE,
    ANDROID_APP_STORAGE,
    APP_PRIVATE_DATA,
    SYSTEM_READ_ONLY,
    VIRTUAL_FILESYSTEM,
    ARCILE_PRIVATE,
    UNSUPPORTED
}

enum class PrivilegedPathOperation {
    LIST,
    READ,
    CREATE_FILE,
    CREATE_DIRECTORY,
    WRITE,
    RENAME_SOURCE,
    RENAME_DESTINATION,
    COPY_SOURCE,
    COPY_DESTINATION,
    MOVE_SOURCE,
    MOVE_DESTINATION,
    DELETE,
    RECURSIVE_DELETE,
    SECURE_OVERWRITE,
    UPDATE_TIMESTAMPS;

    val isReadOnly: Boolean
        get() = this == LIST || this == READ || this == COPY_SOURCE

    val isDestructive: Boolean
        get() = this == MOVE_SOURCE || this == DELETE ||
            this == RECURSIVE_DELETE || this == SECURE_OVERWRITE
}

data class PrivilegedPathDecision(
    val normalizedPath: String,
    val scope: PrivilegedPathScope,
    val operation: PrivilegedPathOperation,
    val allowed: Boolean,
    val reason: String? = null
) {
    fun asResult(): Result<Unit> = if (allowed) {
        Result.success(Unit)
    } else {
        Result.failure(SecurityException(reason ?: "This location is protected by Arcile"))
    }
}

/**
 * Product policy applied in Arcile's process before a privileged request reaches Binder.
 * The remote service still enforces operating-system permissions and its own path checks.
 */
class PrivilegedPathPolicy(
    private val packageName: String,
    private val protectedWritesEnabled: () -> Boolean,
    private val storageRoots: () -> List<String>
) {
    private val systemPrefixes = listOf(
        "/apex",
        "/boot",
        "/odm",
        "/product",
        "/system",
        "/system_ext",
        "/vendor"
    )
    private val virtualPrefixes = listOf("/dev", "/proc", "/sys")
    private val appPrivatePrefixes = listOf(
        "/data/data",
        "/data/user",
        "/data/user_de",
        "/data/misc",
        "/data/system"
    )

    fun classify(path: String): Result<PrivilegedPathScope> =
        normalize(path).map(::classifyNormalized)

    fun evaluate(
        path: String,
        operation: PrivilegedPathOperation,
        session: PrivilegeSession,
        entry: PrivilegedFileEntry? = null
    ): PrivilegedPathDecision {
        val normalized = normalize(path).getOrElse { error ->
            return PrivilegedPathDecision(
                normalizedPath = path,
                scope = PrivilegedPathScope.UNSUPPORTED,
                operation = operation,
                allowed = false,
                reason = error.message ?: "Invalid path"
            )
        }
        val scope = classifyNormalized(normalized)
        val capability = operation.requiredCapability()
        if (capability != null && capability !in session.capabilities) {
            return denied(normalized, scope, operation, "The active backend does not support this operation")
        }
        if (entry != null) {
            validateEntry(entry, operation)?.let { reason ->
                return denied(normalized, scope, operation, reason)
            }
        }
        if (operation.isDestructive && isProtectedRoot(normalized)) {
            return denied(normalized, scope, operation, "Filesystem and storage roots cannot be deleted")
        }

        val allowed = when (scope) {
            PrivilegedPathScope.USER_STORAGE -> true
            PrivilegedPathScope.ANDROID_APP_STORAGE ->
                operation.isReadOnly || session.identity.isRoot || session.identity.isShell
            PrivilegedPathScope.APP_PRIVATE_DATA -> when {
                !session.identity.isRoot -> false
                operation.isReadOnly -> true
                else -> protectedWritesEnabled()
            }
            PrivilegedPathScope.SYSTEM_READ_ONLY ->
                session.identity.isRoot && operation.isReadOnly
            PrivilegedPathScope.VIRTUAL_FILESYSTEM ->
                operation.isReadOnly && entry?.type in READABLE_VIRTUAL_TYPES
            PrivilegedPathScope.ARCILE_PRIVATE -> false
            PrivilegedPathScope.UNSUPPORTED -> false
        }
        val reason = if (allowed) null else deniedReason(scope, operation, session)
        return PrivilegedPathDecision(normalized, scope, operation, allowed, reason)
    }

    fun validate(
        path: String,
        operation: PrivilegedPathOperation,
        session: PrivilegeSession,
        entry: PrivilegedFileEntry? = null
    ): Result<Unit> = evaluate(path, operation, session, entry).asResult()

    fun validateTransfer(
        source: PrivilegedFileEntry,
        destinationPath: String,
        move: Boolean,
        session: PrivilegeSession
    ): Result<Unit> {
        val sourceOperation = if (move) {
            PrivilegedPathOperation.MOVE_SOURCE
        } else {
            PrivilegedPathOperation.COPY_SOURCE
        }
        val destinationOperation = if (move) {
            PrivilegedPathOperation.MOVE_DESTINATION
        } else {
            PrivilegedPathOperation.COPY_DESTINATION
        }
        return validate(source.path, sourceOperation, session, source).fold(
            onSuccess = { validate(destinationPath, destinationOperation, session) },
            onFailure = { Result.failure(it) }
        )
    }

    fun normalize(path: String): Result<String> = runCatchingPreservingCancellation {
        require(path.isNotBlank()) { "Path must not be blank" }
        require(path.indexOf('\u0000') < 0) { "Path must not contain NUL" }
        require(path.startsWith('/')) { "Privileged paths must be absolute" }
        require('\\' !in path) { "Backslash path separators are not supported" }
        val segments = path.split('/').filter(String::isNotEmpty)
        require(segments.none { it == "." || it == ".." }) {
            "Traversal path segments are not allowed"
        }
        if (segments.isEmpty()) "/" else "/" + segments.joinToString("/")
    }

    private fun classifyNormalized(path: String): PrivilegedPathScope {
        if (path.isArcilePrivatePath()) return PrivilegedPathScope.ARCILE_PRIVATE
        if (virtualPrefixes.any { root -> path.isInside(root) }) return PrivilegedPathScope.VIRTUAL_FILESYSTEM
        if (systemPrefixes.any { root -> path.isInside(root) } || path == "/") {
            return PrivilegedPathScope.SYSTEM_READ_ONLY
        }
        if (appPrivatePrefixes.any { root -> path.isInside(root) } || path == "/data") {
            return PrivilegedPathScope.APP_PRIVATE_DATA
        }
        if (path.isAndroidApplicationStorage()) {
            return PrivilegedPathScope.ANDROID_APP_STORAGE
        }
        if (path.isUserStoragePath()) return PrivilegedPathScope.USER_STORAGE
        return PrivilegedPathScope.UNSUPPORTED
    }

    private fun String.isArcilePrivatePath(): Boolean {
        val escapedPackage = Regex.escape(packageName)
        val privatePatterns = listOf(
            Regex("^/data/(?:data|user/\\d+|user_de/\\d+)/$escapedPackage(?:/|$)"),
            Regex("^/storage/(?:emulated/\\d+|[^/]+)/Android/(?:data|obb)/$escapedPackage(?:/|$)")
        )
        return privatePatterns.any { it.containsMatchIn(this) } ||
            contains("/.onlyfiles/") || endsWith("/.onlyfiles")
    }

    private fun String.isAndroidApplicationStorage(): Boolean =
        Regex("^/storage/(?:emulated/\\d+|[^/]+)/Android/(?:data|obb)(?:/|$)")
            .containsMatchIn(this) ||
            Regex("^/mnt/(?:media_rw|runtime/[^/]+)/[^/]+/Android/(?:data|obb)(?:/|$)")
                .containsMatchIn(this)

    private fun String.isUserStoragePath(): Boolean {
        if (this == "/sdcard" || startsWith("/sdcard/")) return true
        if (this == "/storage" || startsWith("/storage/")) return true
        if (startsWith("/mnt/media_rw/") || startsWith("/mnt/runtime/")) return true
        return storageRoots().mapNotNull { normalize(it).getOrNull() }
            .any { root -> isInside(root) }
    }

    private fun isProtectedRoot(path: String): Boolean {
        if (path == "/") return true
        val protected = buildList {
            addAll(systemPrefixes)
            addAll(virtualPrefixes)
            add("/data")
            add("/storage")
            storageRoots().mapNotNullTo(this) { normalize(it).getOrNull() }
        }
        return path in protected
    }

    private fun validateEntry(
        entry: PrivilegedFileEntry,
        operation: PrivilegedPathOperation
    ): String? {
        if (operation.isReadOnly && !entry.readable) return "This path is not readable by the active backend"
        if (!operation.isReadOnly && operation != PrivilegedPathOperation.DELETE &&
            operation != PrivilegedPathOperation.RECURSIVE_DELETE && !entry.writable
        ) {
            return "This path is readable but cannot be changed by the active backend"
        }
        if (entry.type in SPECIAL_FILE_TYPES) {
            return "Devices, sockets, and FIFOs are not supported for this operation"
        }
        if (operation == PrivilegedPathOperation.RECURSIVE_DELETE &&
            entry.type != PrivilegedFileType.DIRECTORY &&
            entry.type != PrivilegedFileType.SYMBOLIC_LINK
        ) {
            return "Recursive deletion requires a directory or symbolic link"
        }
        if (operation == PrivilegedPathOperation.SECURE_OVERWRITE &&
            entry.type != PrivilegedFileType.REGULAR_FILE
        ) {
            return "Secure overwrite supports regular files only"
        }
        return null
    }

    private fun deniedReason(
        scope: PrivilegedPathScope,
        operation: PrivilegedPathOperation,
        session: PrivilegeSession
    ): String = when (scope) {
        PrivilegedPathScope.SYSTEM_READ_ONLY ->
            if (operation.isReadOnly) "Root is required to read this system location"
            else "System partitions are read-only in Arcile"
        PrivilegedPathScope.VIRTUAL_FILESYSTEM ->
            "Virtual filesystem locations are limited to safe read-only access"
        PrivilegedPathScope.APP_PRIVATE_DATA -> when {
            !session.identity.isRoot -> "Root is required for application-private data"
            operation.isReadOnly -> "This application-private location cannot be read"
            else -> "Protected filesystem writes are disabled"
        }
        PrivilegedPathScope.ARCILE_PRIVATE ->
            "Arcile internal and vault locations use dedicated storage workflows"
        PrivilegedPathScope.UNSUPPORTED -> "This location is outside Arcile's supported scopes"
        else -> "This location is protected by Arcile"
    }

    private fun denied(
        path: String,
        scope: PrivilegedPathScope,
        operation: PrivilegedPathOperation,
        reason: String
    ) = PrivilegedPathDecision(path, scope, operation, allowed = false, reason = reason)

    private fun String.isInside(root: String): Boolean =
        this == root || startsWith(if (root.endsWith('/')) root else "$root/")

    private fun PrivilegedPathOperation.requiredCapability(): PrivilegeCapability? = when (this) {
        PrivilegedPathOperation.LIST -> PrivilegeCapability.LIST_DIRECTORY
        PrivilegedPathOperation.READ, PrivilegedPathOperation.COPY_SOURCE -> PrivilegeCapability.READ
        PrivilegedPathOperation.CREATE_FILE -> PrivilegeCapability.CREATE_FILE
        PrivilegedPathOperation.CREATE_DIRECTORY -> PrivilegeCapability.CREATE_DIRECTORY
        PrivilegedPathOperation.WRITE -> PrivilegeCapability.WRITE
        PrivilegedPathOperation.RENAME_SOURCE,
        PrivilegedPathOperation.RENAME_DESTINATION -> PrivilegeCapability.RENAME
        PrivilegedPathOperation.COPY_DESTINATION -> PrivilegeCapability.COPY
        PrivilegedPathOperation.MOVE_SOURCE,
        PrivilegedPathOperation.MOVE_DESTINATION -> PrivilegeCapability.MOVE
        PrivilegedPathOperation.DELETE -> PrivilegeCapability.DELETE
        PrivilegedPathOperation.RECURSIVE_DELETE -> PrivilegeCapability.RECURSIVE_DELETE
        PrivilegedPathOperation.SECURE_OVERWRITE -> PrivilegeCapability.SECURE_OVERWRITE
        PrivilegedPathOperation.UPDATE_TIMESTAMPS -> PrivilegeCapability.UPDATE_TIMESTAMPS
    }

    private companion object {
        val SPECIAL_FILE_TYPES = setOf(
            PrivilegedFileType.BLOCK_DEVICE,
            PrivilegedFileType.CHARACTER_DEVICE,
            PrivilegedFileType.FIFO,
            PrivilegedFileType.SOCKET,
            PrivilegedFileType.UNKNOWN
        )
        val READABLE_VIRTUAL_TYPES = setOf(
            PrivilegedFileType.REGULAR_FILE,
            PrivilegedFileType.DIRECTORY,
            PrivilegedFileType.SYMBOLIC_LINK
        )
    }
}
