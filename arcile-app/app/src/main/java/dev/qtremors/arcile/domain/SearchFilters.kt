package dev.qtremors.arcile.domain

data class SearchFilters(
    val fileType: String? = null,
    val itemType: String? = null,
    val minSize: Long? = null,
    val maxSize: Long? = null,
    val minDateMillis: Long? = null,
    val maxDateMillis: Long? = null
)
