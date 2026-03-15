package dev.qtremors.arcile.ui.theme

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
