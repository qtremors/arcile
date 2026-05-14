package dev.qtremors.arcile.presentation.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.R
import dev.qtremors.arcile.presentation.onboarding.OnboardingStep
import dev.qtremors.arcile.presentation.onboarding.OnboardingUiState
import dev.qtremors.arcile.presentation.ui.components.settings.AccentColorSelector
import dev.qtremors.arcile.presentation.ui.components.settings.ThemeModeSelector
import dev.qtremors.arcile.ui.theme.ThemeState
import dev.qtremors.arcile.ui.theme.spacing

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    currentThemeState: ThemeState,
    onThemeChange: (ThemeState) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onOpenStoragePermissionSettings: () -> Unit,
    onRequestNotificationPermission: () -> Unit
) {
    val stepIndex = when (state.step) {
        OnboardingStep.Welcome -> 0
        OnboardingStep.Features -> 1
        OnboardingStep.Theme -> 2
        OnboardingStep.StoragePermission -> 3
        OnboardingStep.NotificationPermission -> 4
        OnboardingStep.Done -> 5
    }
    val totalVisibleSteps = 5
    val visibleStep = (stepIndex + 1).coerceAtMost(totalVisibleSteps)
    val stepLabel = stringResource(R.string.onboarding_step_count, visibleStep, totalVisibleSteps)
    val progressDescription = stringResource(R.string.onboarding_progress_description)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            OnboardingHeader(
                state = state,
                stepLabel = stepLabel,
                progress = visibleStep / totalVisibleSteps.toFloat(),
                progressDescription = "$progressDescription: $stepLabel",
                onBack = onBack,
                onSkip = onSkip
            )
        },
        bottomBar = {
            OnboardingBottomBar(
                state = state,
                onNext = onNext,
                onOpenStoragePermissionSettings = onOpenStoragePermissionSettings,
                onRequestNotificationPermission = onRequestNotificationPermission
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (state.step) {
                OnboardingStep.Welcome -> OnboardingWelcome()
                OnboardingStep.Features -> OnboardingFeatures()
                OnboardingStep.Theme -> OnboardingTheme(
                    currentThemeState = currentThemeState,
                    onThemeChange = onThemeChange
                )
                OnboardingStep.StoragePermission -> OnboardingStoragePermission(state.hasStoragePermission)
                OnboardingStep.NotificationPermission -> OnboardingNotificationPermission(state.hasNotificationPermission)
                OnboardingStep.Done -> OnboardingDone()
            }
        }
    }
}

@Composable
private fun OnboardingHeader(
    state: OnboardingUiState,
    stepLabel: String,
    progress: Float,
    progressDescription: String,
    onBack: () -> Unit,
    onSkip: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = MaterialTheme.spacing.large)
                .padding(top = MaterialTheme.spacing.space12, bottom = MaterialTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space12)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (state.step != OnboardingStep.Welcome && state.step != OnboardingStep.Done) {
                    TextButton(
                        onClick = onBack,
                        modifier = Modifier.defaultMinSize(minWidth = 72.dp, minHeight = 44.dp)
                    ) {
                        Text(stringResource(R.string.back))
                    }
                } else {
                    Spacer(modifier = Modifier.size(width = 72.dp, height = 44.dp))
                }

                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    Text(
                        text = stepLabel,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }

                if (state.step == OnboardingStep.Welcome ||
                    state.step == OnboardingStep.Features ||
                    state.step == OnboardingStep.Theme
                ) {
                    TextButton(
                        onClick = onSkip,
                        modifier = Modifier.defaultMinSize(minWidth = 72.dp, minHeight = 44.dp)
                    ) {
                        Text(stringResource(R.string.onboarding_skip))
                    }
                } else {
                    Spacer(modifier = Modifier.size(width = 72.dp, height = 44.dp))
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .semantics { contentDescription = progressDescription }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(8.dp)
                        .clip(MaterialTheme.shapes.extraLarge)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

@Composable
private fun OnboardingBottomBar(
    state: OnboardingUiState,
    onNext: () -> Unit,
    onOpenStoragePermissionSettings: () -> Unit,
    onRequestNotificationPermission: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = MaterialTheme.spacing.large, vertical = MaterialTheme.spacing.space12),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space12)
        ) {
            when (state.step) {
                OnboardingStep.StoragePermission -> {
                    Button(
                        onClick = onOpenStoragePermissionSettings,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 56.dp)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null)
                        Spacer(modifier = Modifier.width(MaterialTheme.spacing.small))
                        Text(stringResource(R.string.onboarding_open_storage_settings))
                    }
                    Button(
                        onClick = onNext,
                        enabled = state.hasStoragePermission,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 56.dp)
                    ) {
                        Text(stringResource(R.string.onboarding_continue))
                    }
                }
                OnboardingStep.NotificationPermission -> {
                    if (state.notificationPermissionRequired && !state.hasNotificationPermission) {
                        Button(
                            onClick = onRequestNotificationPermission,
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 56.dp)
                        ) {
                            Icon(Icons.Default.Notifications, contentDescription = null)
                            Spacer(modifier = Modifier.width(MaterialTheme.spacing.small))
                            Text(stringResource(R.string.onboarding_enable_notifications))
                        }
                        OutlinedButton(
                            onClick = onNext,
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 56.dp)
                        ) {
                            Text(stringResource(R.string.onboarding_not_now))
                        }
                    } else {
                        Button(
                            onClick = onNext,
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 56.dp)
                        ) {
                            Text(stringResource(R.string.onboarding_finish))
                        }
                    }
                }
                OnboardingStep.Done -> {
                    Button(
                        onClick = onNext,
                        enabled = !state.isCompleting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 56.dp)
                    ) {
                        Text(stringResource(R.string.onboarding_finish))
                    }
                }
                else -> {
                    Button(
                        onClick = onNext,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 56.dp)
                    ) {
                        Text(stringResource(R.string.onboarding_continue))
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingPage(
    icon: ImageVector,
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
                bottom = if (hasBodyContent) 96.dp else MaterialTheme.spacing.extraLarge
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
                modifier = Modifier.size(if (hasBodyContent) 88.dp else 104.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(if (hasBodyContent) 40.dp else 48.dp)
                    )
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
private fun OnboardingWelcome() {
    OnboardingPage(
        icon = Icons.Default.Folder,
        title = stringResource(R.string.onboarding_welcome_title),
        description = stringResource(R.string.onboarding_welcome_description)
    )
}

@Composable
private fun OnboardingFeatures() {
    OnboardingPage(
        icon = Icons.Default.Bolt,
        title = stringResource(R.string.onboarding_features_title),
        description = stringResource(R.string.onboarding_features_description),
        hasBodyContent = true
    ) {
        FeatureRow(Icons.Default.Storage, stringResource(R.string.onboarding_feature_browser), stringResource(R.string.onboarding_feature_browser_description))
        FeatureRow(Icons.Default.Category, stringResource(R.string.onboarding_feature_categories), stringResource(R.string.onboarding_feature_categories_description))
        FeatureRow(Icons.Default.RestoreFromTrash, stringResource(R.string.onboarding_feature_trash), stringResource(R.string.onboarding_feature_trash_description))
        FeatureRow(Icons.Default.Security, stringResource(R.string.onboarding_feature_restricted), stringResource(R.string.onboarding_feature_restricted_description))
    }
}

@Composable
private fun OnboardingTheme(
    currentThemeState: ThemeState,
    onThemeChange: (ThemeState) -> Unit
) {
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
        AccentColorSelector(
            currentAccent = currentThemeState.accentColor,
            onAccentSelected = { onThemeChange(currentThemeState.copy(accentColor = it)) }
        )
    }
}

@Composable
private fun OnboardingStoragePermission(hasStoragePermission: Boolean) {
    OnboardingPage(
        icon = if (hasStoragePermission) Icons.Default.CheckCircle else Icons.Default.Folder,
        title = if (hasStoragePermission) {
            stringResource(R.string.onboarding_storage_ready_title)
        } else {
            stringResource(R.string.onboarding_storage_title)
        },
        description = if (hasStoragePermission) {
            stringResource(R.string.onboarding_storage_ready_description)
        } else {
            stringResource(R.string.onboarding_storage_description)
        },
        hasBodyContent = true
    ) {
        FeatureRow(Icons.Default.CheckCircle, stringResource(R.string.onboarding_storage_can_read), stringResource(R.string.onboarding_storage_can_read_description))
        FeatureRow(Icons.Default.PrivacyTip, stringResource(R.string.onboarding_storage_private), stringResource(R.string.onboarding_storage_private_description))
        FeatureRow(Icons.Default.WarningAmber, stringResource(R.string.onboarding_storage_restricted), stringResource(R.string.onboarding_storage_restricted_description))
    }
}

@Composable
private fun OnboardingNotificationPermission(hasNotificationPermission: Boolean) {
    OnboardingPage(
        icon = if (hasNotificationPermission) Icons.Default.CheckCircle else Icons.Default.Notifications,
        title = if (hasNotificationPermission) {
            stringResource(R.string.onboarding_notifications_ready_title)
        } else {
            stringResource(R.string.onboarding_notifications_title)
        },
        description = if (hasNotificationPermission) {
            stringResource(R.string.onboarding_notifications_ready_description)
        } else {
            stringResource(R.string.onboarding_notifications_description)
        }
    )
}

@Composable
private fun OnboardingDone() {
    OnboardingPage(
        icon = Icons.Default.CheckCircle,
        title = stringResource(R.string.onboarding_done_title),
        description = stringResource(R.string.onboarding_done_description)
    )
}

@Composable
private fun FeatureRow(
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
