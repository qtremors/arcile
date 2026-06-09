package dev.qtremors.arcile.core.storage.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "thumbnail_variants",
    foreignKeys = [
        ForeignKey(
            entity = ThumbnailEntryEntity::class,
            parentColumns = ["identity_key"],
            childColumns = ["identity_key"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("identity_key"), Index("last_accessed_at")]
)
data class ThumbnailVariantEntity(
    @PrimaryKey
    @ColumnInfo(name = "variant_key")
    val variantKey: String,
    @ColumnInfo(name = "identity_key")
    val identityKey: String,
    @ColumnInfo(name = "size_bucket_px")
    val sizeBucketPx: Int,
    @ColumnInfo(name = "generated_at")
    val generatedAt: Long,
    @ColumnInfo(name = "last_accessed_at")
    val lastAccessedAt: Long
)
