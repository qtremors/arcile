package dev.qtremors.arcile.core.storage.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "category_summaries",
    primaryKeys = ["scope_key", "category_name"]
)
data class CategorySummaryEntity(
    @ColumnInfo(name = "scope_key") val scopeKey: String,
    @ColumnInfo(name = "category_name") val categoryName: String,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    @ColumnInfo(name = "item_count") val itemCount: Long,
    @ColumnInfo(name = "cached_at") val cachedAt: Long
)
