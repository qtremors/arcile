package dev.qtremors.arcile.feature.settings.ui

import dev.qtremors.arcile.core.ui.theme.UiPreferences
import dev.qtremors.arcile.core.storage.domain.AppStartPage
import dev.qtremors.arcile.core.storage.domain.FileOpenBehavior

internal data class SettingsPreferenceActions(
    val themeChange: (UiPreferences) -> Unit,
    val showThumbnailsChange: (Boolean) -> Unit,
    val homeRecentCarouselLimitChange: (Int) -> Unit,
    val showHiddenFilesChange: (Boolean) -> Unit,
    val appStartPageChange: (AppStartPage) -> Unit,
    val browserTabsEnabledChange: (Boolean) -> Unit,
    val rememberLastFolderChange: (Boolean) -> Unit,
    val expandableAppBarChange: (Boolean) -> Unit,
    val activityRecordingChange: (Boolean) -> Unit,
    val browserScrollbarEnabledChange: (Boolean) -> Unit,
    val galleryScrollbarEnabledChange: (Boolean) -> Unit,
    val fileOpenBehaviorChange: (String, FileOpenBehavior) -> Unit,
    val fileOpenBehaviorRemove: (String) -> Unit
)
