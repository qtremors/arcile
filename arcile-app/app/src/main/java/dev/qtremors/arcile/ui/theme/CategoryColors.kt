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
    images = Color(0xFF2E6B30),
    videos = Color(0xFFBC004B),
    audio = Color(0xFF8B5000),
    docs = Color(0xFF0061A4),
    archives = Color(0xFF86279E),
    apks = Color(0xFF006874)
)

val DarkCategoryColors = CategoryColors(
    images = Color(0xFF90D88D),
    videos = Color(0xFFFFB2BF),
    audio = Color(0xFFFFB870),
    docs = Color(0xFF9ECAFF),
    archives = Color(0xFFF3B2FF),
    apks = Color(0xFF4FD8EB)
)

val LocalCategoryColors = staticCompositionLocalOf { LightCategoryColors }
