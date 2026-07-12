package dev.qtremors.arcile.plugin.glb

internal enum class ModelViewerControl {
    None,
    Zoom,
    Brightness,
    Background
}

internal enum class ModelViewerBackground {
    Theme,
    White,
    Black
}

internal data class ModelViewerState(
    val uiVisible: Boolean = true,
    val infoVisible: Boolean = false,
    val activeControl: ModelViewerControl = ModelViewerControl.None,
    val zoomScale: Float = 1f,
    val lightBrightness: Float = 1f,
    val backgroundMode: ModelViewerBackground = ModelViewerBackground.Theme,
    val loading: Boolean = true,
    val errorMessage: String? = null
)
