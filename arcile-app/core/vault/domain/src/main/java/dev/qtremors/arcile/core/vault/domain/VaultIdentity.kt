package dev.qtremors.arcile.core.vault.domain

import java.nio.charset.StandardCharsets
import java.text.Normalizer
import java.util.Locale
import java.util.UUID

private fun requireOpaqueIdentifier(kind: String, value: String): String {
    require(value.isNotBlank()) { "$kind must not be blank" }
    require(value.length <= 128) { "$kind is too long" }
    require(value.none { it == '\u0000' || it == '/' || it == '\\' }) {
        "$kind contains an invalid character"
    }
    return value
}

@JvmInline
value class VaultId private constructor(val value: String) : Comparable<VaultId> {
    override fun compareTo(other: VaultId): Int = value.compareTo(other.value)

    companion object {
        fun of(value: String) = VaultId(requireOpaqueIdentifier("Vault id", value))
        fun random() = VaultId(UUID.randomUUID().toString())
    }
}

@JvmInline
value class DirectoryId private constructor(val value: String) : Comparable<DirectoryId> {
    override fun compareTo(other: DirectoryId): Int = value.compareTo(other.value)

    companion object {
        val Root = DirectoryId("root")
        fun of(value: String) = DirectoryId(requireOpaqueIdentifier("Directory id", value))
        fun random() = DirectoryId(UUID.randomUUID().toString())
    }
}

@JvmInline
value class NodeId private constructor(val value: String) : Comparable<NodeId> {
    override fun compareTo(other: NodeId): Int = value.compareTo(other.value)

    companion object {
        fun of(value: String) = NodeId(requireOpaqueIdentifier("Node id", value))
        fun random() = NodeId(UUID.randomUUID().toString())
    }
}

@JvmInline
value class VaultObjectId private constructor(val value: String) : Comparable<VaultObjectId> {
    override fun compareTo(other: VaultObjectId): Int = value.compareTo(other.value)

    /** The on-disk name is deliberately unrelated to the logical node name. */
    fun shardedPath(): String = "objects/${value.take(2)}/${value.drop(2).take(2)}/$value.obj"

    companion object {
        fun of(value: String): VaultObjectId {
            require(value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' }) {
                "Vault object id must be 32 lowercase hexadecimal bytes"
            }
            return VaultObjectId(value)
        }

        fun fromRandomBytes(bytes: ByteArray): VaultObjectId {
            require(bytes.size == 32)
            return VaultObjectId(bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) })
        }
    }
}

data class VaultNodeRef(
    val vaultId: VaultId,
    val nodeId: NodeId,
    val parentId: DirectoryId,
    val capabilities: VaultNodeCapabilities,
    val directoryId: DirectoryId? = null
) {
    /** Safe opaque identity for StorageNodeRef.backendIdentity and navigation queues. */
    val backendIdentity: String get() = "${vaultId.value}:${nodeId.value}"
}

data class VaultNodeCapabilities(
    val canRead: Boolean = true,
    val canRename: Boolean = true,
    val canDeletePermanently: Boolean = true,
    val canCopy: Boolean = true,
    val canMove: Boolean = true,
    val canExport: Boolean = true,
    val canShare: Boolean = true,
    val canOpenWith: Boolean = true
)

/**
 * Canonical logical name validation shared by every import and mutation boundary.
 * Names are stored in NFC form and compared with [comparisonKey].
 */
@JvmInline
value class VaultName private constructor(val value: String) {
    val comparisonKey: String get() = value.lowercase(Locale.ROOT)

    companion object {
        const val MAX_UTF8_BYTES = 255

        fun of(input: String): VaultName {
            val normalized = Normalizer.normalize(input, Normalizer.Form.NFC)
            require(normalized.isNotBlank()) { "Vault name must not be blank" }
            require(normalized != "." && normalized != "..") { "Dot segments are not allowed" }
            require(normalized.none { it == '/' || it == '\\' || it == '\u0000' }) {
                "Vault name contains a separator or NUL"
            }
            require(normalized.toByteArray(StandardCharsets.UTF_8).size <= MAX_UTF8_BYTES) {
                "Vault name is longer than $MAX_UTF8_BYTES UTF-8 bytes"
            }
            return VaultName(normalized)
        }
    }
}

/**
 * Transitional presentation value. Persistent vault operations use stable IDs, never this path.
 */
@JvmInline
value class VaultPath private constructor(val value: String) {
    val isRoot: Boolean get() = value.isEmpty()
    val name: String get() = if (isRoot) "" else value.substringAfterLast('/')
    val parent: VaultPath?
        get() = when {
            isRoot -> null
            '/' !in value -> Root
            else -> of(value.substringBeforeLast('/'))
        }

    fun resolve(childName: String): VaultPath {
        val child = VaultName.of(childName.trim()).value
        return if (isRoot) of(child) else of("$value/$child")
    }

    fun isDescendantOf(ancestor: VaultPath): Boolean =
        ancestor.isRoot || value.startsWith("${ancestor.value}/")

    companion object {
        val Root = VaultPath("")

        fun of(value: String): VaultPath {
            val normalized = value.replace('\\', '/').trim('/')
            if (normalized.isEmpty()) return Root
            val segments = normalized.split('/').map { VaultName.of(it).value }
            return VaultPath(segments.joinToString("/"))
        }

        fun validateSegment(value: String): String = VaultName.of(value).value
    }
}
