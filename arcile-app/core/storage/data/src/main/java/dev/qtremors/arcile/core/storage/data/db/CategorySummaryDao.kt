package dev.qtremors.arcile.core.storage.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface CategorySummaryDao {
    @Query("SELECT * FROM category_summaries WHERE scope_key = :scopeKey")
    suspend fun get(scopeKey: String): List<CategorySummaryEntity>

    @Upsert
    suspend fun upsert(summaries: List<CategorySummaryEntity>)

    @Query("DELETE FROM category_summaries WHERE scope_key IN (:scopeKeys)")
    suspend fun delete(scopeKeys: List<String>)

    @Query("DELETE FROM category_summaries")
    suspend fun clear()
}
