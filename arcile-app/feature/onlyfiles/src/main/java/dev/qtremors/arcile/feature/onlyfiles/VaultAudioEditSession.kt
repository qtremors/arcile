package dev.qtremors.arcile.feature.onlyfiles

import android.system.Os
import dev.qtremors.arcile.core.ui.audio.PrivateAudioEditSession
import dev.qtremors.arcile.core.vault.domain.VaultFailure
import dev.qtremors.arcile.core.storage.domain.BuiltInFileOpen
import dev.qtremors.arcile.core.vault.domain.VaultFileSystem
import dev.qtremors.arcile.core.vault.domain.VaultId
import dev.qtremors.arcile.core.vault.domain.VaultNodeMetadata
import java.io.File
import java.io.FileInputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Exposes decrypted vault audio to the shared editor as in-memory files. Each path is a randomly named
 * symlink in app cache whose target is `/proc/self/fd/N`: the link holds no audio, and the memory file
 * vanishes when the session closes or the process dies. The session closes itself when its vault locks.
 */
internal class VaultAudioEditSession private constructor(
    private val owner: VaultNodeMetadata,
    private val files: VaultFileSystem,
    private val linkDirectory: File,
    private val scratch: VaultAudioScratchFactory,
    private val link: (target: String, path: String) -> Unit
) : PrivateAudioEditSession {
    private val entries = ConcurrentHashMap<String, Pair<VaultAudioScratch, String?>>()
    private val activeState = MutableStateFlow(true)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    override val active: StateFlow<Boolean> = activeState.asStateFlow()
    override var sourcePaths: List<String> = emptyList()
        private set

    override fun displayName(path: String): String? = entries[path]?.second

    override fun createOutput(extension: String): String {
        check(activeState.value) { "The vault locked." }
        return expose(scratch.create(), extension, null)
    }

    /** Saves beside the first source as a new file; an existing name is never replaced. */
    override suspend fun publish(outputPath: String): Result<String> = try {
        if (!activeState.value) throw VaultFailure.Locked(owner.ref.vaultId)
        val output = entries[outputPath]?.first ?: throw VaultFailure.InvalidPath("Unknown edit output")
        val size = output.file.length()
        if (size <= 0L) throw VaultFailure.IntegrityFailed("The edited audio is empty")
        val extension = outputPath.substringAfterLast('.', "")
        val base = owner.name.substringBeforeLast('.', owner.name)
        val created = createUnique(base, extension)
        files.replaceFileContents(created.ref, created.revision, size) { FileInputStream(output.file) }.onFailure {
            files.deletePermanently(created.ref)
        }.getOrThrow()
        Result.success(created.name)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }

    override fun discard(outputPath: String) {
        entries.remove(outputPath)?.let { (memory, _) ->
            File(outputPath).delete()
            memory.close()
        }
    }

    override fun close() {
        if (!activeState.compareAndSet(true, false)) return
        entries.keys.toList().forEach(::discard)
        linkDirectory.delete()
        liveDirectories -= linkDirectory.name
        scope.cancel()
    }

    private suspend fun createUnique(base: String, extension: String): VaultNodeMetadata {
        for (index in 1..MAX_NAME_ATTEMPTS) {
            val suffix = if (index == 1) " (edited)" else " (edited $index)"
            val name = if (extension.isEmpty()) "$base$suffix" else "$base$suffix.$extension"
            val result = files.createEmptyFile(owner.ref.vaultId, owner.ref.parentId, name)
            result.getOrNull()?.let { return it }
            val error = result.exceptionOrNull()
            if (error !is VaultFailure.NameConflict && error !is VaultFailure.PathConflict) throw error ?: VaultFailure.Unavailable()
        }
        throw VaultFailure.NameConflict(base)
    }

    private fun expose(memory: VaultAudioScratch, extension: String, displayName: String?): String {
        val safeExtension = extension.lowercase().takeIf { it.matches(Regex("[a-z0-9]{1,5}")) }
        val path = File(linkDirectory, UUID.randomUUID().toString() + (safeExtension?.let { ".$it" } ?: "")).path
        try {
            link(memory.file.path, path)
        } catch (error: Exception) {
            memory.close()
            throw error
        }
        entries[path] = memory to displayName
        return path
    }

    companion object {
        /** Several sources may be combined; together they must still fit the in-memory limit. */
        const val MAX_SESSION_BYTES = 512L * 1024L * 1024L
        private const val MAX_NAME_ATTEMPTS = 100
        private const val LINK_DIRECTORY = "private-audio-edit"
        private val liveDirectories: MutableSet<String> = ConcurrentHashMap.newKeySet()

        suspend fun create(
            nodes: List<VaultNodeMetadata>,
            files: VaultFileSystem,
            cacheDirectory: String,
            unlockedVaults: StateFlow<Set<VaultId>>,
            scratch: VaultAudioScratchFactory = MemoryAudioScratchFactory,
            link: (String, String) -> Unit = { target, path -> Os.symlink(target, path) }
        ): VaultAudioEditSession = kotlinx.coroutines.withContext(Dispatchers.IO) {
            val owner = nodes.firstOrNull() ?: throw VaultFailure.InvalidPath("Select audio to edit")
            if (nodes.any { it.ref.vaultId != owner.ref.vaultId }) throw VaultFailure.InvalidPath("Edit audio from one vault at a time")
            nodes.firstOrNull { it.sizeBytes !in 1..VaultAudioMetadataEditor.MAX_EDIT_BYTES }?.let {
                throw VaultFailure.FileTooLarge(it.sizeBytes, VaultAudioMetadataEditor.MAX_EDIT_BYTES)
            }
            val total = nodes.sumOf { it.sizeBytes }
            if (total > MAX_SESSION_BYTES) throw VaultFailure.FileTooLarge(total, MAX_SESSION_BYTES)
            val root = File(cacheDirectory, LINK_DIRECTORY)
            // Links left by a process that died point at descriptors that no longer exist; remove them.
            root.listFiles()?.filterNot { it.name in liveDirectories }?.forEach { it.deleteRecursively() }
            val directory = File(root, UUID.randomUUID().toString())
            if (!directory.mkdirs()) throw VaultFailure.Unavailable("Audio editing is unavailable")
            liveDirectories += directory.name
            val session = VaultAudioEditSession(owner, files, directory, scratch, link)
            try {
                session.sourcePaths = nodes.map { node ->
                    val memory = scratch.create()
                    try {
                        decryptVaultAudio(files, node, memory.file)
                    } catch (error: Throwable) {
                        memory.close()
                        throw error
                    }
                    session.expose(memory, node.extension, node.name)
                }
            } catch (error: Throwable) {
                session.close()
                throw error
            }
            session.scope.launch {
                unlockedVaults.first { owner.ref.vaultId !in it }
                session.close()
            }
            session
        }
    }
}

/** Audio the shared editor can open together: one vault, writable, and within the in-memory limits. */
internal fun canEditVaultAudio(nodes: List<VaultNodeMetadata>): Boolean =
    nodes.isNotEmpty() && nodes.size <= MAX_EDIT_SOURCES &&
        nodes.all { it.builtInOpen() == BuiltInFileOpen.AUDIO && it.ref.capabilities.canRename } &&
        nodes.map { it.ref.vaultId }.distinct().size == 1 &&
        nodes.all { it.sizeBytes in 1..VaultAudioMetadataEditor.MAX_EDIT_BYTES } &&
        nodes.sumOf { it.sizeBytes } <= VaultAudioEditSession.MAX_SESSION_BYTES

private const val MAX_EDIT_SOURCES = 20

/** Opens the shared audio editor for a registered private session; the session ID is the only extra. */
internal fun launchPrivateAudioEditor(context: android.content.Context, sessionId: String) {
    context.startActivity(android.content.Intent(dev.qtremors.arcile.core.ui.audio.PrivateAudioEditSessions.ACTION_EDIT)
        .setPackage(context.packageName)
        .putExtra(dev.qtremors.arcile.core.ui.audio.PrivateAudioEditSessions.EXTRA_SESSION, sessionId))
}
