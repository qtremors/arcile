package dev.qtremors.arcile.core.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class CategoryColors(
    val images: Color,
    val videos: Color,
    val audio: Color,
    val docs: Color,
    val archives: Color,
    val apks: Color
)

val LightCategoryColors = CategoryColors(
    images = CatImageLight,
    videos = CatVideoLight,
    audio = CatAudioLight,
    docs = CatDocLight,
    archives = CatArchiveLight,
    apks = CatApkLight
)


val DarkCategoryColors = CategoryColors(
    images = CatImageDark,
    videos = CatVideoDark,
    audio = CatAudioDark,
    docs = CatDocDark,
    archives = CatArchiveDark,
    apks = CatApkDark
)


val LocalCategoryColors = staticCompositionLocalOf { LightCategoryColors }

fun getCategoryColor(
    name: String,
    categoryColors: CategoryColors,
    fallbackColor: Color
): Color = when (name) {
    "Images" -> categoryColors.images
    "Videos" -> categoryColors.videos
    "Audio" -> categoryColors.audio
    "Docs" -> categoryColors.docs
    "Archives" -> categoryColors.archives
    "APKs" -> categoryColors.apks
    else -> fallbackColor
}

fun CategoryColors.harmonizeWith(keyColor: Color): CategoryColors {
    return CategoryColors(
        images = this.images.harmonizeWith(keyColor),
        videos = this.videos.harmonizeWith(keyColor),
        audio = this.audio.harmonizeWith(keyColor),
        docs = this.docs.harmonizeWith(keyColor),
        archives = this.archives.harmonizeWith(keyColor),
        apks = this.apks.harmonizeWith(keyColor)
    )
}
