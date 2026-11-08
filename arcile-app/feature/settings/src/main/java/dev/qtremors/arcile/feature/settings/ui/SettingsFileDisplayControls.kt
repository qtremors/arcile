package dev.qtremors.arcile.feature.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.rememberArcileHaptics
import dev.qtremors.arcile.core.ui.settings.SettingsChoiceHeader
import dev.qtremors.arcile.core.ui.settings.SettingsConnectedChoices
import dev.qtremors.arcile.core.ui.theme.FileDetailVisibility
import dev.qtremors.arcile.core.ui.theme.UiPreferences

/** Plain, Special, and App folder icon buttons; Plain clears the others and Special and App combine. */
internal enum class FolderIconChoice { PLAIN, SPECIAL, APP }

internal fun UiPreferences.isFolderIconChoiceSelected(choice: FolderIconChoice): Boolean = when (choice) {
    FolderIconChoice.PLAIN -> !specialFolderIconsEnabled && !appFolderIconsEnabled
    FolderIconChoice.SPECIAL -> specialFolderIconsEnabled
    FolderIconChoice.APP -> appFolderIconsEnabled
}

internal fun UiPreferences.withFolderIconChoice(choice: FolderIconChoice, checked: Boolean): UiPreferences =
    when (choice) {
        FolderIconChoice.PLAIN -> if (checked) withSpecialFolderIcons(false).withAppFolderIcons(false) else this
        FolderIconChoice.SPECIAL -> withSpecialFolderIcons(checked)
        FolderIconChoice.APP -> withAppFolderIcons(checked)
    }

/** One button per row detail; each turns on and off independently. */
internal enum class FileDetailChoice { COUNT, SIZE, DATE, TIME }

internal fun FileDetailVisibility.isSelected(choice: FileDetailChoice): Boolean = when (choice) {
    FileDetailChoice.COUNT -> itemCount
    FileDetailChoice.SIZE -> size
    FileDetailChoice.DATE -> date
    FileDetailChoice.TIME -> time
}

internal fun FileDetailVisibility.with(choice: FileDetailChoice, checked: Boolean): FileDetailVisibility =
    when (choice) {
        FileDetailChoice.COUNT -> copy(itemCount = checked)
        FileDetailChoice.SIZE -> copy(size = checked)
        FileDetailChoice.DATE -> copy(date = checked)
        FileDetailChoice.TIME -> copy(time = checked)
    }

@Composable
internal fun FolderIconStyleSelector(theme: UiPreferences, onThemeChange: (UiPreferences) -> Unit) {
    val haptics = rememberArcileHaptics()
    val choices = FolderIconChoice.entries
    val labels = mapOf(
        FolderIconChoice.PLAIN to stringResource(R.string.settings_folder_icon_plain),
        FolderIconChoice.SPECIAL to stringResource(R.string.settings_folder_icon_special),
        FolderIconChoice.APP to stringResource(R.string.settings_folder_icon_app)
    )
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
        SettingsChoiceHeader(
            title = stringResource(R.string.settings_folder_icon_style),
            description = stringResource(R.string.settings_folder_icon_style_description),
            icon = Icons.Default.FolderSpecial
        )
        SettingsConnectedChoices(
            options = choices.map(labels::getValue),
            isSelected = { theme.isFolderIconChoiceSelected(choices[it]) },
            onSelectionChanged = { index, checked ->
                val updated = theme.withFolderIconChoice(choices[index], checked)
                if (updated != theme) {
                    haptics.toggleMenu()
                    onThemeChange(updated)
                }
            },
            rowTag = "folder_icon_style_choices",
            testTagForIndex = { "folder_icon_style_${choices[it].name.lowercase()}" },
            dynamicExpand = true,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)
        )
    }
}

@Composable
internal fun FileDetailsSelector(theme: UiPreferences, onThemeChange: (UiPreferences) -> Unit) {
    val haptics = rememberArcileHaptics()
    val choices = FileDetailChoice.entries
    val labels = mapOf(
        FileDetailChoice.COUNT to stringResource(R.string.settings_file_details_count),
        FileDetailChoice.SIZE to stringResource(R.string.settings_file_details_size),
        FileDetailChoice.DATE to stringResource(R.string.settings_file_details_date),
        FileDetailChoice.TIME to stringResource(R.string.settings_file_details_time)
    )
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
        SettingsChoiceHeader(
            title = stringResource(R.string.settings_file_details),
            description = stringResource(R.string.settings_file_details_description),
            icon = Icons.Default.Subtitles
        )
        SettingsConnectedChoices(
            options = choices.map(labels::getValue),
            isSelected = { theme.fileDetails.isSelected(choices[it]) },
            onSelectionChanged = { index, checked ->
                haptics.toggleMenu()
                onThemeChange(theme.copy(fileDetails = theme.fileDetails.with(choices[index], checked)))
            },
            rowTag = "file_details_choices",
            testTagForIndex = { "file_details_${choices[it].name.lowercase()}" },
            dynamicExpand = true,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)
        )
    }
}
