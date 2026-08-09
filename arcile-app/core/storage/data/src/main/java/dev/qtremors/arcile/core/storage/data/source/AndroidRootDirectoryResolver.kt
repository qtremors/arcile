package dev.qtremors.arcile.core.storage.data.source

import dev.qtremors.arcile.core.storage.data.runCatchingPreservingCancellation
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.util.concurrent.atomic.AtomicReference

internal class AndroidRootDirectoryResolver(
    private val entryNames: List<String> = DEFAULT_ENTRY_NAMES,
    private val entryExists: (File) -> Boolean = { entry ->
        runCatchingPreservingCancellation {
            Files.exists(entry.toPath(), LinkOption.NOFOLLOW_LINKS)
        }.getOrDefault(false)
    }
) {
    private val cachedChildren = AtomicReference<List<File>?>(null)

    fun children(root: File): Array<File> {
        cachedChildren.get()?.let { return it.toTypedArray() }
        return synchronized(cachedChildren) {
            cachedChildren.get()?.toTypedArray() ?: entryNames.asSequence()
                .map { name -> File(root, name) }
                .filter(entryExists)
                .toList()
                .also(cachedChildren::set)
                .toTypedArray()
        }
    }

    fun isKnownEntry(file: File): Boolean {
        if (file.parentFile?.absolutePath != File.separator || file.name !in entryNames) return false
        val cached = cachedChildren.get()
        return if (cached != null) {
            cached.any { it.absolutePath == file.absolutePath }
        } else {
            entryExists(file)
        }
    }

    private companion object {
        val DEFAULT_ENTRY_NAMES = listOf(
            "acct",
            "apex",
            "bin",
            "bugreports",
            "cache",
            "config",
            "d",
            "data",
            "data_mirror",
            "debug_ramdisk",
            "dev",
            "etc",
            "linkerconfig",
            "lost+found",
            "metadata",
            "mnt",
            "odm",
            "oem",
            "postinstall",
            "proc",
            "product",
            "sdcard",
            "storage",
            "sys",
            "system",
            "system_ext",
            "vendor"
        )
    }
}
