package dev.qtremors.arcile.feature.onboarding.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Source
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.feature.onboarding.OnboardingUiState
import dev.qtremors.arcile.shared.ui.settings.AccentColorPickerSheet
import dev.qtremors.arcile.shared.ui.settings.ThemeModeSelector
import dev.qtremors.arcile.shared.ui.settings.accentLabelRes
import dev.qtremors.arcile.ui.theme.ThemeState
import dev.qtremors.arcile.ui.theme.spacing
@Composable
internal fun OnboardingPage(
    icon: ImageVector? = null,
    customIcon: (@Composable () -> Unit)? = null,
    title: String,
    description: String,
    hasBodyContent: Boolean = false,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MaterialTheme.spacing.large)
            .padding(
                top = if (hasBodyContent) MaterialTheme.spacing.large else MaterialTheme.spacing.extraLarge,
                bottom = if (hasBodyContent) 184.dp else 144.dp
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = if (hasBodyContent) {
            Arrangement.Top
        } else {
            Arrangement.Center
        }
    ) {
        Column(
            modifier = Modifier.widthIn(max = 560.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                tonalElevation = 4.dp,
                modifier = Modifier.size(if (hasBodyContent) 88.dp else 120.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (customIcon != null) {
                        customIcon()
                    } else if (icon != null) {
                        Icon(
                            icon,
                            contentDescription = null,
                            modifier = Modifier.size(if (hasBodyContent) 40.dp else 56.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(if (hasBodyContent) MaterialTheme.spacing.large else MaterialTheme.spacing.extraLarge))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(MaterialTheme.spacing.space12))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(if (hasBodyContent) MaterialTheme.spacing.large else MaterialTheme.spacing.extraLarge))
            content()
        }
    }
}

@Composable
internal fun OnboardingWelcomeAndFeatures() {
    OnboardingPage(
        customIcon = {
            AsyncImage(
                model = dev.qtremors.arcile.R.mipmap.ic_launcher,
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier.fillMaxSize()
            )
        },
        title = stringResource(R.string.onboarding_welcome_title),
        description = stringResource(R.string.onboarding_intro_description),
        hasBodyContent = true
    ) {
        FeatureRow(Icons.Default.Storage, stringResource(R.string.onboarding_feature_multi_volume), stringResource(R.string.onboarding_feature_multi_volume_description))
        FeatureRow(Icons.Default.Bolt, stringResource(R.string.onboarding_feature_batch_operations), stringResource(R.string.onboarding_feature_batch_operations_description))
        FeatureRow(Icons.Default.Category, stringResource(R.string.onboarding_feature_instant_categories), stringResource(R.string.onboarding_feature_instant_categories_description))
        FeatureRow(Icons.Default.RestoreFromTrash, stringResource(R.string.onboarding_feature_trash_subsystem), stringResource(R.string.onboarding_feature_trash_subsystem_description))
    }
}

@Composable
internal fun OnboardingPrivacy() {
    OnboardingPage(
        icon = Icons.Default.PrivacyTip,
        title = stringResource(R.string.onboarding_privacy_title),
        description = stringResource(R.string.onboarding_privacy_description),
        hasBodyContent = true
    ) {
        FeatureRow(androidx.compose.material.icons.Icons.Default.CloudOff, stringResource(R.string.onboarding_privacy_offline), stringResource(R.string.onboarding_privacy_offline_description))
        FeatureRow(androidx.compose.material.icons.Icons.Default.Block, stringResource(R.string.onboarding_privacy_trackers), stringResource(R.string.onboarding_privacy_trackers_description))
        FeatureRow(Icons.Default.Folder, stringResource(R.string.onboarding_privacy_local_file_access), stringResource(R.string.onboarding_privacy_local_file_access_description))
        FeatureRow(androidx.compose.material.icons.Icons.Default.Source, stringResource(R.string.onboarding_privacy_source), stringResource(R.string.onboarding_privacy_source_description))
    }
}

@Composable
internal fun OnboardingTheme(
    currentThemeState: ThemeState,
    onThemeChange: (ThemeState) -> Unit
) {
    var showAccentPicker by remember { mutableStateOf(false) }

    OnboardingPage(
        icon = Icons.Default.Palette,
        title = stringResource(R.string.onboarding_theme_title),
        description = stringResource(R.string.onboarding_theme_description),
        hasBodyContent = true
    ) {
        ThemeModeSelector(
            currentMode = currentThemeState.themeMode,
            onModeSelected = { onThemeChange(currentThemeState.copy(themeMode = it)) }
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.accent_color)) },
            supportingContent = { Text(stringResource(accentLabelRes(currentThemeState.accentColor))) },
            leadingContent = {
                Icon(Icons.Default.ColorLens, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            },
            trailingContent = {
                Icon(Icons.Default.ChevronRight, contentDescription = null)
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .clickable { showAccentPicker = true }
        )
    }

    if (showAccentPicker) {
        AccentColorPickerSheet(
            currentAccent = currentThemeState.accentColor,
            onAccentSelected = {
                onThemeChange(currentThemeState.copy(accentColor = it))
                showAccentPicker = false
            },
            onDismiss = { showAccentPicker = false }
        )
    }
}

@Composable
internal fun OnboardingSetupPermissions(
    state: OnboardingUiState,
    showOlderAndroidWarning: Boolean
) {
    OnboardingPage(
        icon = if (state.hasStoragePermission) Icons.Default.CheckCircle else Icons.Default.Folder,
        title = if (state.hasStoragePermission) {
            stringResource(R.string.onboarding_storage_ready_title)
        } else {
            stringResource(R.string.onboarding_storage_title)
        },
        description = if (state.hasStoragePermission) {
            stringResource(R.string.onboarding_storage_ready_description)
        } else {
            stringResource(R.string.onboarding_storage_description)
        },
        hasBodyContent = true
    ) {
        FeatureRow(Icons.Default.CheckCircle, stringResource(R.string.onboarding_storage_can_read), stringResource(R.string.onboarding_storage_can_read_description))
        FeatureRow(Icons.Default.PrivacyTip, stringResource(R.string.onboarding_storage_private), stringResource(R.string.onboarding_storage_private_description))
        FeatureRow(Icons.Default.WarningAmber, stringResource(R.string.onboarding_storage_restricted), stringResource(R.string.onboarding_storage_restricted_description))
        FeatureRow(
            icon = if (state.hasNotificationPermission) Icons.Default.CheckCircle else Icons.Default.Notifications,
            title = if (state.hasNotificationPermission) {
                stringResource(R.string.onboarding_notifications_ready_title)
            } else {
                stringResource(R.string.onboarding_notifications_title)
            },
            description = if (state.notificationPermissionRequired) {
                stringResource(R.string.onboarding_notifications_description)
            } else {
                stringResource(R.string.onboarding_notifications_not_required_description)
            }
        )
        AnimatedVisibility(visible = showOlderAndroidWarning) {
            FeatureRow(
                icon = Icons.Default.WarningAmber,
                title = stringResource(R.string.onboarding_limited_android_title),
                description = stringResource(R.string.onboarding_limited_android_description)
            )
        }
    }
}

@Composable
internal fun OnboardingDone() {
    OnboardingPage(
        icon = Icons.Default.CheckCircle,
        title = stringResource(R.string.onboarding_done_title),
        description = stringResource(R.string.onboarding_done_description)
    )
}

@Composable
internal fun FeatureRow(
    icon: ImageVector,
    title: String,
    description: String
) {
    val containerColor = MaterialTheme.colorScheme.surfaceContainer
    val iconContainerColor by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.primaryContainer,
        label = "onboardingFeatureIconContainer"
    )

    Surface(
        shape = MaterialTheme.shapes.large,
        color = containerColor,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = MaterialTheme.spacing.small)
    ) {
        Row(
            modifier = Modifier.padding(MaterialTheme.spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = iconContainerColor,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
