package dev.qtremors.arcile.utils

import androidx.compose.ui.graphics.Color
import dev.qtremors.arcile.ui.theme.CategoryColors

/**
 * Returns the themed color for a given file category name.
 * Centralizes the mapping to avoid duplication across UI screens.
 */
fun getCategoryColor(name: String, categoryColors: CategoryColors, fallbackColor: Color): Color {
    return when (name) {
        "Images" -> categoryColors.images
        "Videos" -> categoryColors.videos
        "Audio" -> categoryColors.audio
        "Docs" -> categoryColors.docs
        "Archives" -> categoryColors.archives
        "APKs" -> categoryColors.apks
        else -> fallbackColor
    }
}
