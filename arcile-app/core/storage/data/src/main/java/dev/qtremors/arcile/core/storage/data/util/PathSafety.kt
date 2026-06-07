package dev.qtremors.arcile.core.storage.data.util

import java.io.File
import java.nio.file.Files

object PathSafety {
    enum class OperationPolicy(
        internal val rejectSymlinks: Boolean
    ) {
        READ(rejectSymlinks = false),
        RECURSIVE_READ(rejectSymlinks = true),
        MUTATE(rejectSymlinks = true),
        RECURSIVE_MUTATE(rejectSymlinks = true)
    }

    fun validatePath(
        file: File,
        activeStorageRoots: List<String>,
        policy: OperationPolicy = OperationPolicy.READ
    ): Result<Unit> {
        if (policy.rejectSymlinks && containsSymbolicLink(file)) {
            return Result.failure(SecurityException("Access denied: symbolic links are not allowed for this operation"))
        }

        val canonical = file.canonicalPath
        val isAllowed = activeStorageRoots.any { root ->
            val canonicalRoot = runCatching { File(root).canonicalPath }.getOrDefault(root)
            canonical == canonicalRoot || canonical.startsWith(canonicalRoot + File.separator)
        }

        if (!isAllowed) {
            return Result.failure(SecurityException("Access denied: path outside storage boundaries"))
        }
        return Result.success(Unit)
    }

    fun pathWithAncestors(path: String, activeStorageRoots: List<String>): List<String> {
        val file = File(path)
        val canonical = runCatching { file.canonicalFile }.getOrDefault(file.absoluteFile)
        val matchingRoot = activeStorageRoots
            .map(::File)
            .map { runCatching { it.canonicalFile }.getOrDefault(it.absoluteFile) }
            .filter { root ->
                canonical.path == root.path || canonical.path.startsWith(root.path + File.separator)
            }
            .maxByOrNull { it.path.length }
            ?: return listOf(canonical.absolutePath)

        val result = mutableListOf<String>()
        var current: File? = canonical
        while (current != null) {
            result += current.absolutePath
            if (current.path == matchingRoot.path) break
            current = current.parentFile
        }
        return result
    }

    private fun containsSymbolicLink(file: File): Boolean {
        var current: File? = file.absoluteFile
        while (current != null) {
            if (Files.isSymbolicLink(current.toPath())) return true
            current = current.parentFile
        }
        return false
    }
}
