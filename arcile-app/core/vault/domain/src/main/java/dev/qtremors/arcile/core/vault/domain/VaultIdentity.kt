package dev.qtremors.arcile.core.vault.domain

@JvmInline
value class VaultId private constructor(val value: String) {
    companion object {
        fun of(value: String): VaultId {
            require(value.isNotBlank()) { "Vault id must not be blank" }
            require(value.indexOf('\u0000') < 0) { "Vault id must not contain NUL" }
            return VaultId(value)
        }
    }
}

@JvmInline
value class VaultPath private constructor(val value: String) {
    val isRoot: Boolean get() = value == ROOT_VALUE
    val name: String get() = if (isRoot) ROOT_VALUE else value.substringAfterLast('/')
    val parent: VaultPath?
        get() = when {
            isRoot -> null
            '/' !in value -> Root
            else -> of(value.substringBeforeLast('/'))
        }

    fun resolve(childName: String): VaultPath {
        val cleanName = validateSegment(childName)
        return if (isRoot) of(cleanName) else of("$value/$cleanName")
    }

    fun isDescendantOf(ancestor: VaultPath): Boolean =
        ancestor.isRoot || value.startsWith("${ancestor.value}/")

    companion object {
        private const val ROOT_VALUE = ""
        val Root = VaultPath(ROOT_VALUE)

        fun of(value: String): VaultPath {
            val normalized = value.replace('\\', '/').trim('/')
            if (normalized.isEmpty()) return Root
            normalized.split('/').forEach(::validateSegment)
            return VaultPath(normalized)
        }

        fun validateSegment(value: String): String {
            val trimmed = value.trim()
            require(trimmed.isNotEmpty()) { "Vault path segment must not be blank" }
            require(trimmed != "." && trimmed != "..") { "Relative path segments are not allowed" }
            require('/' !in trimmed && '\\' !in trimmed) { "Vault path segment must not contain separators" }
            require(trimmed.indexOf('\u0000') < 0) { "Vault path segment must not contain NUL" }
            return trimmed
        }
    }
}
