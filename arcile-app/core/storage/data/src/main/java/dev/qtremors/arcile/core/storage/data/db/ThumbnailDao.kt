package dev.qtremors.arcile.core.storage.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

@Dao
interface ThumbnailDao {
    @Query("SELECT variant_key FROM thumbnail_variants")
    suspend fun loadedVariantKeys(): List<String>

    @Query(
        """
        SELECT identity_key FROM thumbnail_entries
        WHERE last_failure_at IS NOT NULL
            AND (last_success_at IS NULL OR last_failure_at >= last_success_at)
        """
    )
    suspend fun failedIdentityKeys(): List<String>

    @Upsert
    suspend fun upsertEntry(entity: ThumbnailEntryEntity)

    @Upsert
    suspend fun upsertVariant(entity: ThumbnailVariantEntity)

    @Query(
        """
        UPDATE thumbnail_entries
        SET last_failure_at = NULL,
            failure_count = 0,
            failure_message = NULL
        WHERE identity_key = :identityKey
        """
    )
    suspend fun clearFailure(identityKey: String)

    @Query("DELETE FROM thumbnail_variants WHERE identity_key = :identityKey")
    suspend fun deleteVariantsForIdentity(identityKey: String)

    @Query("DELETE FROM thumbnail_entries WHERE source IN (:sources) OR source LIKE :descendantPrefix")
    suspend fun deleteEntriesForSources(sources: List<String>, descendantPrefix: String)

    @Query("DELETE FROM thumbnail_variants WHERE identity_key NOT IN (SELECT identity_key FROM thumbnail_entries)")
    suspend fun deleteOrphanVariants()

    @Query("DELETE FROM thumbnail_entries")
    suspend fun clearEntries()

    @Query("DELETE FROM thumbnail_variants")
    suspend fun clearVariants()

    @Query("SELECT COUNT(*) FROM thumbnail_variants")
    suspend fun variantCount(): Int

    @Query("SELECT variant_key FROM thumbnail_variants ORDER BY last_accessed_at ASC LIMIT :limit")
    suspend fun oldestVariantKeys(limit: Int): List<String>

    @Query("DELETE FROM thumbnail_variants WHERE variant_key IN (:variantKeys)")
    suspend fun deleteVariants(variantKeys: List<String>)

    @Transaction
    suspend fun clearAll() {
        clearVariants()
        clearEntries()
    }
}
