package dev.qtremors.arcile.core.storage.data

import dev.qtremors.arcile.core.storage.domain.StorageNodeRef
import java.nio.charset.StandardCharsets
import java.util.Base64

/** Delimiter-safe identity used only for internal cache and snapshot keys. */
internal data class StorageNodePersistenceIdentity(
    val backendId: String,
    val canonicalIdentity: String,
    val displayPath: String
) {
    fun encode(): String = listOf(
        PREFIX,
        backendId.encodePart(),
        canonicalIdentity.encodePart(),
        displayPath.encodePart()
    ).joinToString(SEPARATOR)

    companion object {
        private const val PREFIX = "node-v1"
        private const val SEPARATOR = ":"

        fun from(node: StorageNodeRef): StorageNodePersistenceIdentity =
            StorageNodePersistenceIdentity(
                backendId = node.backendId,
                canonicalIdentity = node.canonicalIdentity.value,
                displayPath = node.displayPath.absolutePath
            )

        fun decode(value: String): StorageNodePersistenceIdentity? {
            val parts = value.split(SEPARATOR)
            if (parts.size != 4 || parts[0] != PREFIX) return null
            return runCatchingPreservingCancellation {
                StorageNodePersistenceIdentity(
                    backendId = parts[1].decodePart(),
                    canonicalIdentity = parts[2].decodePart(),
                    displayPath = parts[3].decodePart()
                )
            }.getOrNull()
        }

        private fun String.encodePart(): String = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(toByteArray(StandardCharsets.UTF_8))

        private fun String.decodePart(): String = String(
            Base64.getUrlDecoder().decode(this),
            StandardCharsets.UTF_8
        )
    }
}
