package dev.qtremors.arcile.core.vault.domain

import java.io.File

object OnlyFilesVaultFormat {
    const val PRIMARY_FILE = "vault.onlyfiles"
    const val BACKUP_FILE = "vault.onlyfiles.bak"
    const val COMMIT_FILE = ".header.commit"

    val HEADER_FILES = setOf(PRIMARY_FILE, BACKUP_FILE, COMMIT_FILE)

    fun isVaultHeaderName(name: String): Boolean = name in HEADER_FILES

    fun isVaultRootDirectory(directory: File): Boolean {
        if (!directory.isDirectory) return false
        val names = directory.list() ?: return false
        return PRIMARY_FILE in names || BACKUP_FILE in names
    }

    fun pathsInsideVault(absolutePaths: Collection<String>): Set<String> {
        val directoryStatus = hashMapOf<String, Boolean>()
        return absolutePaths.filterTo(linkedSetOf()) { path ->
            isInsideVault(File(path), directoryStatus)
        }
    }

    private fun isInsideVault(
        fileOrDir: File,
        directoryStatus: MutableMap<String, Boolean>
    ): Boolean {
        var current: File? = if (fileOrDir.isDirectory) fileOrDir else fileOrDir.parentFile
        val visited = arrayListOf<String>()
        while (current != null && current.parentFile != null) {
            val path = current.absolutePath
            directoryStatus[path]?.let { knownStatus ->
                visited.forEach { directoryStatus[it] = knownStatus }
                return knownStatus
            }
            visited += path
            if (isVaultRootDirectory(current)) {
                visited.forEach { directoryStatus[it] = true }
                return true
            }
            current = current.parentFile
        }
        visited.forEach { directoryStatus[it] = false }
        return false
    }

}
