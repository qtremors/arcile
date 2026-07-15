package dev.qtremors.arcile.core.vault.crypto

import dev.qtremors.arcile.core.vault.domain.DirectoryId
import dev.qtremors.arcile.core.vault.domain.NodeId
import dev.qtremors.arcile.core.vault.domain.VaultFailure
import dev.qtremors.arcile.core.vault.domain.VaultId
import dev.qtremors.arcile.core.vault.domain.VaultName
import dev.qtremors.arcile.core.vault.domain.VaultNodeKind
import dev.qtremors.arcile.core.vault.domain.VaultObjectId
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

data class VaultManifestEntry(
    val nodeId: NodeId,
    val name: String,
    val kind: VaultNodeKind,
    val revision: Long,
    val modifiedAtMillis: Long,
    val sizeBytes: Long,
    val mimeType: String?,
    val objectId: VaultObjectId?,
    val childDirectoryId: DirectoryId?,
    val protectedKey: ByteArray
) {
    init {
        require(VaultName.of(name).value == name) { "Manifest name is not canonical NFC" }
        require(revision >= 1L)
        require(modifiedAtMillis >= 0L)
        require(sizeBytes >= 0L)
        require(protectedKey.size == VaultCryptography.KEY_SIZE_BYTES)
        when (kind) {
            VaultNodeKind.FILE -> {
                require(objectId != null && childDirectoryId == null)
            }
            VaultNodeKind.DIRECTORY -> {
                require(objectId == null && childDirectoryId != null && sizeBytes == 0L && mimeType == null)
            }
        }
    }

    override fun equals(other: Any?): Boolean = other is VaultManifestEntry &&
        nodeId == other.nodeId && name == other.name && kind == other.kind && revision == other.revision &&
        modifiedAtMillis == other.modifiedAtMillis && sizeBytes == other.sizeBytes && mimeType == other.mimeType &&
        objectId == other.objectId && childDirectoryId == other.childDirectoryId &&
        protectedKey.contentEquals(other.protectedKey)

    override fun hashCode(): Int = arrayOf(
        nodeId, name, kind, revision, modifiedAtMillis, sizeBytes, mimeType, objectId,
        childDirectoryId, protectedKey.contentHashCode()
    ).contentHashCode()

    fun copyDefensively(): VaultManifestEntry = copy(protectedKey = protectedKey.copyOf())
}

data class VaultDirectorySnapshot(
    val directoryId: DirectoryId,
    val generation: Long,
    val entries: List<VaultManifestEntry>,
    val pageObjectIds: List<VaultObjectId>
)

data class PreparedVaultManifest(
    val directoryId: DirectoryId,
    val generation: Long,
    val rootRelativePath: String,
    val rootBytes: ByteArray,
    val pages: List<PreparedVaultManifestPage>,
    val replacedPageObjectIds: Set<VaultObjectId>
)

data class PreparedVaultManifestPage(
    val objectId: VaultObjectId,
    val relativePath: String,
    val bytes: ByteArray
)

/** Paged authenticated directory metadata. Logical names and hierarchy appear only in ciphertext. */
class VaultDirectoryManifestCodec(
    private val json: Json = Json { ignoreUnknownKeys = false; encodeDefaults = true; explicitNulls = false }
) {
    fun createRoot(
        directory: VaultDirectoryAccess,
        vaultId: VaultId,
        directoryId: DirectoryId,
        directoryKey: ByteArray
    ): VaultDirectorySnapshot {
        val prepared = prepare(vaultId, directoryId, directoryKey, 0L, emptyList(), emptySet())
        publish(directory, prepared)
        return read(directory, vaultId, directoryId, directoryKey)
    }

    fun prepare(
        vaultId: VaultId,
        directoryId: DirectoryId,
        directoryKey: ByteArray,
        generation: Long,
        entries: List<VaultManifestEntry>,
        replacedPageObjectIds: Set<VaultObjectId> = emptySet()
    ): PreparedVaultManifest {
        require(directoryKey.size == VaultCryptography.KEY_SIZE_BYTES)
        require(generation >= 0L)
        validateEntries(entries)
        val ordered = entries.sortedWith(
            compareBy<VaultManifestEntry> { VaultName.of(it.name).comparisonKey }.thenBy { it.nodeId.value }
        )
        val pages = ordered.chunked(MAX_ENTRIES_PER_PAGE).mapIndexed { index, pageEntries ->
            val objectId = VaultObjectId.fromRandomBytes(VaultCryptography.randomBytes(32))
            val document = VaultPageDocument(pageEntries.map(VaultManifestEntry::toDocument))
            val plaintext = json.encodeToString(document).toByteArray(StandardCharsets.UTF_8)
            require(plaintext.size <= MAX_PAGE_PLAINTEXT_BYTES)
            try {
                val sealed = VaultCryptography.seal(
                    directoryKey,
                    plaintext,
                    pageAssociatedData(vaultId, directoryId, generation, index, objectId)
                )
                val encoded = ByteArrayOutputStream().use { bytes ->
                    DataOutputStream(bytes).use { output ->
                        output.write(PAGE_MAGIC)
                        output.writeInt(FORMAT_VERSION)
                        output.writeLong(generation)
                        output.writeInt(index)
                        output.write(objectId.value.hexToByteArray())
                        output.write(sealed.nonce)
                        output.writeInt(sealed.ciphertext.size)
                        output.write(sealed.ciphertext)
                    }
                    bytes.toByteArray()
                }
                PreparedVaultManifestPage(objectId, objectId.shardedPath(), encoded)
            } finally {
                plaintext.fill(0)
            }
        }

        val rootDocument = VaultRootDocument(
            pageObjectIds = pages.map { it.objectId.value },
            entryCount = entries.size.toLong()
        )
        val rootPlaintext = json.encodeToString(rootDocument).toByteArray(StandardCharsets.UTF_8)
        val rootBytes = try {
            val sealed = VaultCryptography.seal(
                directoryKey,
                rootPlaintext,
                rootAssociatedData(vaultId, directoryId, generation)
            )
            ByteArrayOutputStream().use { bytes ->
                DataOutputStream(bytes).use { output ->
                    output.write(ROOT_MAGIC)
                    output.writeInt(FORMAT_VERSION)
                    output.writeLong(generation)
                    output.write(sealed.nonce)
                    output.writeInt(sealed.ciphertext.size)
                    output.write(sealed.ciphertext)
                }
                bytes.toByteArray()
            }
        } finally {
            rootPlaintext.fill(0)
        }
        return PreparedVaultManifest(
            directoryId = directoryId,
            generation = generation,
            rootRelativePath = rootSlot(directoryId, generation),
            rootBytes = rootBytes,
            pages = pages,
            replacedPageObjectIds = replacedPageObjectIds
        )
    }

    /** Caller publishes only after its transaction commit marker is durable. */
    fun publish(directory: VaultDirectoryAccess, prepared: PreparedVaultManifest) {
        prepared.pages.forEach { page ->
            require(!directory.exists(page.relativePath)) { "Manifest pages are immutable" }
            directory.writeAtomic(page.relativePath, page.bytes)
            require(directory.readBytes(page.relativePath).contentEquals(page.bytes))
        }
        directory.writeAtomic(prepared.rootRelativePath, prepared.rootBytes)
        require(directory.readBytes(prepared.rootRelativePath).contentEquals(prepared.rootBytes))
    }

    fun read(
        directory: VaultDirectoryAccess,
        vaultId: VaultId,
        directoryId: DirectoryId,
        directoryKey: ByteArray
    ): VaultDirectorySnapshot {
        require(directoryKey.size == VaultCryptography.KEY_SIZE_BYTES)
        val roots = listOf(rootSlot(directoryId, 0L), rootSlot(directoryId, 1L)).mapNotNull { path ->
            if (!directory.exists(path)) null else runCatching {
                decodeRoot(directory.readBytes(path), vaultId, directoryId, directoryKey)
            }.getOrNull()
        }
        val root = roots.maxByOrNull { it.generation }
            ?: throw VaultFailure.IntegrityFailed("Directory manifest root is missing or damaged")
        val entries = ArrayList<VaultManifestEntry>(root.entryCount.toSafeEntryCount())
        root.pageObjectIds.forEachIndexed { index, objectId ->
            val bytes = try {
                directory.readBytes(objectId.shardedPath())
            } catch (error: Throwable) {
                throw VaultFailure.IntegrityFailed("Directory manifest page is missing", error)
            }
            entries += decodePage(bytes, vaultId, directoryId, root.generation, index, objectId, directoryKey)
        }
        require(entries.size.toLong() == root.entryCount) { "Directory entry count does not match its root" }
        validateEntries(entries)
        return VaultDirectorySnapshot(directoryId, root.generation, entries, root.pageObjectIds)
    }

    private fun decodeRoot(
        bytes: ByteArray,
        vaultId: VaultId,
        directoryId: DirectoryId,
        key: ByteArray
    ): OpenRoot {
        try {
            require(bytes.size in MIN_ROOT_BYTES..MAX_ROOT_BYTES)
            DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                require(ByteArray(ROOT_MAGIC.size).also(input::readFully).contentEquals(ROOT_MAGIC))
                val format = input.readInt()
                if (format != FORMAT_VERSION) throw VaultFailure.UnsupportedFormat(format)
                val generation = input.readLong()
                require(generation >= 0L)
                val nonce = ByteArray(VaultCryptography.NONCE_SIZE_BYTES).also(input::readFully)
                val ciphertextSize = input.readInt()
                require(ciphertextSize in VaultCryptography.GCM_TAG_SIZE_BYTES..MAX_ROOT_BYTES)
                require(input.available() == ciphertextSize)
                val ciphertext = ByteArray(ciphertextSize).also(input::readFully)
                val plaintext = VaultCryptography.open(
                    key,
                    VaultSealedValue(nonce, ciphertext),
                    rootAssociatedData(vaultId, directoryId, generation)
                )
                try {
                    val document = json.decodeFromString<VaultRootDocument>(plaintext.toString(StandardCharsets.UTF_8))
                    require(document.entryCount >= 0L)
                    require(document.pageObjectIds.size.toLong() == pageCount(document.entryCount))
                    val ids = document.pageObjectIds.map(VaultObjectId::of)
                    require(ids.distinct().size == ids.size)
                    return OpenRoot(generation, document.entryCount, ids)
                } finally {
                    plaintext.fill(0)
                }
            }
        } catch (error: Throwable) {
            if (error is VaultFailure) throw error
            throw VaultFailure.IntegrityFailed("Directory manifest root failed authentication", error)
        }
    }

    private fun decodePage(
        bytes: ByteArray,
        vaultId: VaultId,
        directoryId: DirectoryId,
        generation: Long,
        pageIndex: Int,
        objectId: VaultObjectId,
        key: ByteArray
    ): List<VaultManifestEntry> {
        try {
            require(bytes.size in MIN_PAGE_BYTES..MAX_PAGE_BYTES)
            DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                require(ByteArray(PAGE_MAGIC.size).also(input::readFully).contentEquals(PAGE_MAGIC))
                val format = input.readInt()
                if (format != FORMAT_VERSION) throw VaultFailure.UnsupportedFormat(format)
                require(input.readLong() == generation)
                require(input.readInt() == pageIndex)
                require(VaultObjectId.of(ByteArray(32).also(input::readFully).toHexString()) == objectId)
                val nonce = ByteArray(VaultCryptography.NONCE_SIZE_BYTES).also(input::readFully)
                val ciphertextSize = input.readInt()
                require(ciphertextSize in VaultCryptography.GCM_TAG_SIZE_BYTES..MAX_PAGE_BYTES)
                require(input.available() == ciphertextSize)
                val ciphertext = ByteArray(ciphertextSize).also(input::readFully)
                val plaintext = VaultCryptography.open(
                    key,
                    VaultSealedValue(nonce, ciphertext),
                    pageAssociatedData(vaultId, directoryId, generation, pageIndex, objectId)
                )
                try {
                    val document = json.decodeFromString<VaultPageDocument>(plaintext.toString(StandardCharsets.UTF_8))
                    require(document.entries.size <= MAX_ENTRIES_PER_PAGE)
                    return document.entries.map(VaultManifestEntryDocument::toEntry)
                } finally {
                    plaintext.fill(0)
                }
            }
        } catch (error: Throwable) {
            if (error is VaultFailure) throw error
            throw VaultFailure.IntegrityFailed("Directory manifest page $pageIndex failed authentication", error)
        }
    }

    private fun validateEntries(entries: List<VaultManifestEntry>) {
        val names = HashSet<String>(entries.size)
        val ids = HashSet<NodeId>(entries.size)
        entries.forEach { entry ->
            val canonical = VaultName.of(entry.name)
            require(canonical.value == entry.name) { "Directory name is not normalized" }
            require(names.add(canonical.comparisonKey)) { "Case-insensitive directory name collision" }
            require(ids.add(entry.nodeId)) { "Duplicate node id in directory" }
        }
    }

    private fun rootAssociatedData(vaultId: VaultId, directoryId: DirectoryId, generation: Long) =
        structuredAad("directory-root", vaultId, directoryId, generation, -1, null)

    private fun pageAssociatedData(
        vaultId: VaultId,
        directoryId: DirectoryId,
        generation: Long,
        pageIndex: Int,
        objectId: VaultObjectId
    ) = structuredAad("directory-page", vaultId, directoryId, generation, pageIndex, objectId)

    private fun structuredAad(
        purpose: String,
        vaultId: VaultId,
        directoryId: DirectoryId,
        generation: Long,
        pageIndex: Int,
        objectId: VaultObjectId?
    ): ByteArray {
        val prefix = "Arcile/OnlyFiles/v1/$purpose".toByteArray(StandardCharsets.US_ASCII)
        val vault = vaultId.value.toByteArray(StandardCharsets.UTF_8)
        val directory = directoryId.value.toByteArray(StandardCharsets.UTF_8)
        val objectBytes = objectId?.value?.hexToByteArray() ?: ByteArray(0)
        return ByteBuffer.allocate(prefix.size + 4 + vault.size + 4 + directory.size + 8 + 4 + objectBytes.size).apply {
            put(prefix)
            putInt(vault.size); put(vault)
            putInt(directory.size); put(directory)
            putLong(generation)
            putInt(pageIndex)
            put(objectBytes)
        }.array()
    }

    companion object {
        const val FORMAT_VERSION = 1
        const val MAX_ENTRIES_PER_PAGE = 256
        private const val MAX_PAGE_PLAINTEXT_BYTES = 2 * 1024 * 1024
        private const val MAX_PAGE_BYTES = MAX_PAGE_PLAINTEXT_BYTES + 1024
        private const val MIN_PAGE_BYTES = 8 + 4 + 8 + 4 + 32 + 12 + 4 + 16
        private const val MAX_ROOT_BYTES = 8 * 1024 * 1024
        private const val MIN_ROOT_BYTES = 8 + 4 + 8 + 12 + 4 + 16
        private val ROOT_MAGIC = "AODIR001".toByteArray(StandardCharsets.US_ASCII)
        private val PAGE_MAGIC = "AOPAG001".toByteArray(StandardCharsets.US_ASCII)

        fun rootSlot(directoryId: DirectoryId, generation: Long): String =
            "manifests/${directoryId.value}/root.${if (generation and 1L == 0L) "a" else "b"}"

        private fun pageCount(entries: Long): Long = if (entries == 0L) 0L else ((entries - 1L) / MAX_ENTRIES_PER_PAGE) + 1L
    }
}

private data class OpenRoot(
    val generation: Long,
    val entryCount: Long,
    val pageObjectIds: List<VaultObjectId>
)

@Serializable
private data class VaultRootDocument(val pageObjectIds: List<String>, val entryCount: Long)

@Serializable
private data class VaultPageDocument(val entries: List<VaultManifestEntryDocument>)

@Serializable
private data class VaultManifestEntryDocument(
    val nodeId: String,
    val name: String,
    val kind: String,
    val revision: Long,
    val modifiedAtMillis: Long,
    val sizeBytes: Long,
    val mimeType: String? = null,
    val objectId: String? = null,
    val childDirectoryId: String? = null,
    val protectedKey: String
)

private fun VaultManifestEntry.toDocument() = VaultManifestEntryDocument(
    nodeId = nodeId.value,
    name = name,
    kind = kind.name,
    revision = revision,
    modifiedAtMillis = modifiedAtMillis,
    sizeBytes = sizeBytes,
    mimeType = mimeType,
    objectId = objectId?.value,
    childDirectoryId = childDirectoryId?.value,
    protectedKey = protectedKey.toBase64()
)

private fun VaultManifestEntryDocument.toEntry() = VaultManifestEntry(
    nodeId = NodeId.of(nodeId),
    name = name,
    kind = VaultNodeKind.valueOf(kind),
    revision = revision,
    modifiedAtMillis = modifiedAtMillis,
    sizeBytes = sizeBytes,
    mimeType = mimeType,
    objectId = objectId?.let(VaultObjectId::of),
    childDirectoryId = childDirectoryId?.let(DirectoryId::of),
    protectedKey = protectedKey.fromBase64(VaultCryptography.KEY_SIZE_BYTES)
)

private fun Long.toSafeEntryCount(): Int {
    require(this in 0..Int.MAX_VALUE.toLong()) { "Directory is too large to materialize" }
    return toInt()
}

private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }

private fun String.hexToByteArray(): ByteArray {
    require(length % 2 == 0 && all { it in '0'..'9' || it in 'a'..'f' })
    return ByteArray(length / 2) { index -> substring(index * 2, index * 2 + 2).toInt(16).toByte() }
}
