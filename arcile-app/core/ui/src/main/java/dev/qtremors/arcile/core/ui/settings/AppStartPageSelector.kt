package dev.qtremors.arcile.core.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.storage.domain.AppStartPage
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.rememberArcileHaptics

@Composable
fun AppStartPageSelector(currentPage: AppStartPage, onPageSelected: (AppStartPage) -> Unit) {
    val haptics = rememberArcileHaptics()
    val pages = AppStartPage.entries
    val labels = pages.map { page ->
        when (page) {
            AppStartPage.HOME -> stringResource(R.string.start_page_home)
            AppStartPage.BROWSER -> stringResource(R.string.start_page_browser)
        }
    }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
        SettingsChoiceHeader(
            title = stringResource(R.string.settings_app_start_page),
            description = stringResource(R.string.settings_app_start_page_description),
            icon = Icons.Default.Home
        )
        SettingsConnectedChoices(
            options = labels,
            isSelected = { pages[it] == currentPage },
            onSelectionChanged = { index, checked ->
                if (checked) {
                    haptics.selectionStart()
                    onPageSelected(pages[index])
                }
            },
            testTagForIndex = { "app_start_page_${pages[it].name.lowercase()}" },
            dynamicExpand = true,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)
        )
    }
}
