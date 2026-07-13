package dev.qtremors.arcile.feature.browser

import androidx.compose.runtime.Immutable
import dev.qtremors.arcile.core.presentation.PropertiesUiModel

@Immutable
internal data class BrowserPropertiesState(
    val isVisible: Boolean = false,
    val isLoading: Boolean = false,
    val properties: PropertiesUiModel? = null
)
