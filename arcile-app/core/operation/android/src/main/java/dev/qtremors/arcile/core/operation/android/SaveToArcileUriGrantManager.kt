package dev.qtremors.arcile.core.operation.android

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import dev.qtremors.arcile.core.operation.SaveToArcileImportItem

class SaveToArcileUriGrantManager(
    private val contentResolver: ContentResolver
) {
    fun acquirePersistableReadGrants(uris: List<Uri>): Set<String> {
        val alreadyOwned = runCatching {
            contentResolver.persistedUriPermissions
                .filter { it.isReadPermission }
                .mapTo(mutableSetOf()) { it.uri.toString() }
        }.getOrDefault(emptySet())
        val acquired = linkedSetOf<String>()

        uris.distinct().forEach { uri ->
            val value = uri.toString()
            if (value in alreadyOwned) return@forEach
            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }.onSuccess {
                acquired += value
            }
        }
        return acquired
    }

    fun releaseOwnedPersistableReadGrants(items: List<SaveToArcileImportItem>) {
        items.asSequence()
            .filter { it.ownsPersistedReadGrant }
            .map { it.uri }
            .distinct()
            .forEach { value ->
                runCatching {
                    contentResolver.releasePersistableUriPermission(
                        Uri.parse(value),
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
            }
    }
}
